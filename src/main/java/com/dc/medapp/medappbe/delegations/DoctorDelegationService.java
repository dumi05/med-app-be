package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorDelegationService {
    private final DoctorDelegationRepository doctorDelegationRepository;
    private final DoctorRepository doctorRepository;

    @Transactional
    public DoctorDelegationEntity propose (Long delegatingDoctorId, Long delegateDoctorId, LocalDate startDate, LocalDate endDate) {
        doValidations(delegatingDoctorId, delegateDoctorId, startDate, endDate);

        var delegating = findDoctor(delegatingDoctorId);
        var delegate = findDoctor(delegateDoctorId);

        if(!delegating.isDelegationConsent() || !delegate.isDelegationConsent()){
            throw new IllegalArgumentException("Entrambi i medici devono aver dato consenso alla delega.");
        }

        return doctorDelegationRepository.save(
                DoctorDelegationEntity.builder()
                        .delegatingDoctor(delegating)
                        .delegateDoctor(delegate)
                        .startDate(startDate)
                        .endDate(endDate)
                        .status(DelegationStatus.PROPOSED)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public boolean isAuthorized(Long requesterDoctorId, Long assignedDoctorId){
        if(requesterDoctorId.equals(assignedDoctorId)){
            return true;
        }
        return doctorDelegationRepository.existsActiveDelegation(assignedDoctorId, requesterDoctorId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Long> activeDelegatingDoctorIds(Long delegateDoctorId) {
        return doctorDelegationRepository.findActiveDelegatingDoctorIds(delegateDoctorId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<DoctorDelegationEntity> findInvolvingDoctors(Long doctorId){
        return doctorDelegationRepository.findInvolvingDoctors(doctorId);
    }

    private DoctorDelegationEntity answer(Long delegationId, Long actingDoctorId, DelegationStatus status, String action){
        var delegation = doctorDelegationRepository.findById(delegationId).orElseThrow(() -> new ResourceNotFoundException("Delega non trovata: "+delegationId));

        if(!delegation.getDelegateDoctor().getId().equals(actingDoctorId)){
            throw new UnauthorizedAccessException("Solo il medico delegato può " + action + " la delega");
        }
        if(delegation.getStatus() != DelegationStatus.PROPOSED){
            throw new InvalidStateTransitionException("Impossibile " + action + " una delega in stato " + delegation.getStatus());
        }

        delegation.setStatus(status);
        return delegation;
    }

    private DoctorEntity findDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Medico non trovato: " + doctorId));
    }

    private static void doValidations(Long delegatingDoctorId, Long delegateDoctorId, LocalDate startDate, LocalDate endDate) {
        if(delegatingDoctorId.equals(delegateDoctorId)) {
            throw new IllegalArgumentException("Un medico non può delegare sé stesso.");
        }
        if(endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La data di fine delega non può essere precedente alla data di inizio.");
        }
        if(endDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La delega non può terminare nel passato.");
        }
    }

    @Transactional
    public DoctorDelegationEntity accept(Long delegationId, Long docId){
        return answer(delegationId, docId, DelegationStatus.ACCEPTED, "accettare");
    }

    @Transactional
    public DoctorDelegationEntity reject(Long delegationId, Long docId){
        return answer(delegationId, docId, DelegationStatus.REJECTED, "rifiutare");
    }
}
