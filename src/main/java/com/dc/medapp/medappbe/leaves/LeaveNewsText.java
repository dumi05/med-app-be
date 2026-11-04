package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.doctor.DoctorEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public final class LeaveNewsText {

    private LeaveNewsText(){}

    private static DateTimeFormatter day(){
        return DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN);
    }

    private static DateTimeFormatter dayWithName() {
        return DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.ITALIAN);
    }

    public static String title(DoctorEntity doc, LocalDate start, LocalDate end){
        return nameOf(doc) + ": assente dal " + day().format(start) + " al " + day().format(end);
    }

    public static String body(DoctorEntity doc, LocalDate start, LocalDate end, Optional<LocalDate> returnDay, DoctorEntity replacement){
        var text = new StringBuilder("Il dott. "+fullName(doc) + " sarà assente dal " + day().format(start) + " al " + day().format(end) + " (inclusi).");
        returnDay.ifPresent(day -> text.append(" Tornerà disponibile da ").append(dayWithName().format(day)).append("."));
        if(replacement != null){
            text.append(" In caso di necessità puoi rivolgerti al dott. ").append(fullName(replacement)).append('.');
        }
        text.append(" Le visite già prenotate in quel periodo sono state spostate o annullate: controlla le tue prenotazioni.");
        return text.toString();
    }

    private static String nameOf(DoctorEntity doc){
        return "Dott. " + fullName(doc);
    }

    private static String fullName(DoctorEntity doc){
        return doc.getFirstName() + " " + doc.getLastName();
    }
}
