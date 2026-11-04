package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentReassignmentService;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.leaves.dto.LeavePreviewResponse;
import com.dc.medapp.medappbe.news.NewsEntity;
import com.dc.medapp.medappbe.news.NewsRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.schedule.ScheduleService;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final int MAX_LEAVE_DAYS = 90;

    @Value("${app.schedule.leave-notice-days:7}")
    private int noticeDays;
    private final DoctorLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final NewsRepository newsRepository;
    private final ScheduleService scheduleService;
    private final ProfileResolver profileResolver;
    private final AppointmentReassignmentService reassignmentService;
    private final ReturnDayFinder returnDayFinder;

    private record Plan(AppointmentEntity appointment, Optional<AvailabilitySlotEntity> slot){}
    public record Result(Long leaveId, int reassigned, int cancelled){}

    @Transactional(readOnly = true)
    public LeavePreviewResponse preview(UserEntity user, Long requestDocId, LocalDate start, LocalDate end, Long replacementId){
        var doctor = scheduleService.resolveDoctor(user, requestDocId);
        var replacement = validate(doctor, start, end, replacementId);

        var plans = plan(doctor, start, end, replacement);

        var affected = plans.stream().map(plan -> new LeavePreviewResponse.AffectedAppointment(
                plan.appointment().getId(),
                plan.appointment().getSlot().getStartTime(),
                plan.appointment().getPatient().getFirstName(),
                plan.appointment().getPatient().getLastName(),
                plan.slot().isPresent() ? LeavePreviewResponse.Outcome.REASSIGN : LeavePreviewResponse.Outcome.CANCEL
        )).toList();

        int reassign = (int) plans.stream().filter(plan -> plan.slot().isPresent()).count();
        var days = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), start));
        return new LeavePreviewResponse(
                days,
                days < noticeDays,
                affected,
                reassign,
                plans.size()-reassign,
                LeaveNewsText.title(doctor, start, end),
                newsBody(doctor,start,end, replacement));
    }

    @Transactional
    @Audited(action = "CREATE_DOCTOR_LEAVE", entity = "DoctorLeave")
    public Result create(UserEntity user, Long requestDocId, LocalDate start, LocalDate end, Long replacementId){
        var doctor = scheduleService.resolveDoctor(user, requestDocId);
        var replacement = validate(doctor, start, end, replacementId);
        var leave = leaveRepository.save(DoctorLeaveEntity.builder()
                .doctor(doctor).startDate(start).endDate(end).replacementDoctor(replacement).createdByRole(user.getRole()).build());

        int reassigned = 0;
        int cancelled = 0;
        for (var plan : plan(doctor, start, end, replacement)) {
            if (plan.slot().isPresent()) {
                reassignmentService.reassign(plan.appointment(), plan.slot().get());
                reassigned++;
            } else {
                reassignmentService.cancelBecauseDoctorIsAway(plan.appointment());
                cancelled++;
            }
        }

        newsRepository.save(NewsEntity.builder().author(doctor)
                .title(LeaveNewsText.title(doctor, start, end))
                .body(newsBody(doctor, start, end, replacement))
                .expiresAt(end).automatic(true).leave(leave).build());
        return new Result(leave.getId(), reassigned, cancelled);
    }

    @Transactional(readOnly = true)
    public List<DoctorLeaveEntity> find(UserEntity user, Long requestDoctorId){
        var doctor = scheduleService.resolveDoctor(user, requestDoctorId);
        return leaveRepository.findByDoctorIdAndEndDateGreaterThanEqualOrderByStartDate(doctor.getId(), LocalDate.now());
    }

    @Transactional
    @Audited(action = "DELETE_DOCTOR_LEAVE", entity = "DoctorLeave", idArg = 1)
    public void delete(UserEntity actor, Long leaveId){
        var leave = leaveRepository.findById(leaveId).orElseThrow(() -> new ResourceNotFoundException("Assenza non trovata."));
        if(actor.getRole() != Role.ADMIN && !leave.getDoctor().getId().equals(profileResolver.doctorIdOf(actor))){
            throw new UnauthorizedAccessException("Solo il medico interessato o l'amministratore possono annullare un'assenza.");
        }
        if(leave.getEndDate().isBefore(LocalDate.now())){
            throw new IllegalArgumentException("Un'assenza già finita non si può annullare.");
        }
        newsRepository.deleteByLeaveId(leaveId);
        leaveRepository.delete(leave);
    }


    private DoctorEntity validate(DoctorEntity doctor, LocalDate start, LocalDate end, Long replacementId) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("L'ultimo giorno non può precedere il primo.");
        }
        if (end.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("L'assenza non può essere già finita.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_LEAVE_DAYS) {
            throw new IllegalArgumentException("Un'assenza può durare al massimo " + MAX_LEAVE_DAYS + " giorni.");
        }
        if (leaveRepository.overlaps(doctor.getId(), start, end)) {
            throw new IllegalArgumentException("Esiste già un'assenza in quel periodo.");
        }
        if (replacementId == null) {
            return null;
        }
        var replacement = doctorRepository.findById(replacementId)
                .orElseThrow(() -> new ResourceNotFoundException("Medico " + replacementId + " non trovato."));
        if (replacement.getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("Il sostituto deve essere un altro medico.");
        }
        if (!replacement.getSpecialization().getId().equals(doctor.getSpecialization().getId())) {
            throw new IllegalArgumentException("Il sostituto deve avere la stessa specializzazione.");
        }
        if (!replacement.getUser().isEnabled()) {
            throw new IllegalArgumentException("Il sostituto non è attivo.");
        }
        return replacement;
    }

    private List<Plan> plan(DoctorEntity doc, LocalDate start, LocalDate end, DoctorEntity replacement){
        var from = start.atStartOfDay().isAfter(LocalDateTime.now()) ? start.atStartOfDay() : LocalDateTime.now();
        var to = end.plusDays(1).atStartOfDay();
        return appointmentRepository.findBookedOfDoctorBetween(doc.getId(), from, to).stream()
                .map(app -> new Plan(app, slotFor(app, replacement)))
                .toList();
    }

    private Optional<AvailabilitySlotEntity> slotFor(AppointmentEntity appointment, DoctorEntity replacement){
        if(replacement == null){
            return Optional.empty();
        }

        var slot = appointment.getSlot();
        if(leaveRepository.isAway(replacement.getId(), slot.getStartTime().toLocalDate())){
            return Optional.empty();
        }
        return slotRepository.findFirstByDoctorIdAndStartTimeAndBookedFalse(replacement.getId(), slot.getStartTime())
                .filter(x -> x.getDurationMinutes() >= slot.getDurationMinutes());
    }

    private String newsBody(DoctorEntity doc, LocalDate start, LocalDate end, DoctorEntity replacement){
        return LeaveNewsText.body(doc, start, end, returnDayFinder.after(doc.getId(), end), replacement);
    }

}
