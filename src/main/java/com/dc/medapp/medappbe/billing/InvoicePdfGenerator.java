package com.dc.medapp.medappbe.billing;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfCell;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class InvoicePdfGenerator {

    private static final String STUDIO = "Studio medico La Mole";
    private static final String DEMO_NOTICE = "Documento dimostrativo senza valore fiscale";

    public byte[] generate(InvoiceItemEntity item){
        var appointment = item.getAppointment();
        var doctor = appointment.getSlot().getDoctor();
        var patient = appointment.getPatient();
        var day = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN);
        var dayAndTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm", Locale.ITALIAN);
        var zone = ZoneId.systemDefault();

        var bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        var normal = FontFactory.getFont(FontFactory.HELVETICA, 11);

        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 56,56,56,56);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(STUDIO, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(0x1a,0x4e, 0x78)))); // same color of the webapp?
            document.add(new Paragraph("Torino", normal));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Fattura " + item.getNumber(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph(DEMO_NOTICE, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.RED)));
            document.add(new Paragraph(" "));

            var table = new PdfPTable(new float[]{1.2f,2.8f});
            table.setWidthPercentage(100);
            row(table, "Data di emissione", day.format(item.getIssuedAt().atZone(zone)), bold, normal);
            row(table, "Paziente", patient.getFirstName().concat(" ").concat(patient.getLastName()), bold, normal);
            row(table, "Medico", String.format("Dott. %s %s (%s)", doctor.getFirstName(), doctor.getLastName(), doctor.getSpecialization().getName()), bold, normal);
            row(table, "Prestazione", BillingService.STANDARD_VISIT_TYPE, bold, normal);
            row(table, "Data della visita", dayAndTime.format(appointment.getSlot().getStartTime()), bold, normal);
            row(table, "Importo", NumberFormat.getCurrencyInstance(Locale.ITALY).format(item.getAmount()), bold, normal);
            row(table, "Pagamento", "Ricevuto il "+day.format(item.getPaidAt().atZone(zone)), bold, normal);
            document.add(table);
        } catch (DocumentException de) {
            throw new IllegalStateException("Impossibile creare il pdf della fatture "+item.getNumber(), de);
        } finally{
            document.close();
        }
        return out.toByteArray();
    }

    private static void row(PdfPTable table, String label, String value, Font bold, Font normal){
        var left = new PdfPCell(new Phrase(label, bold));
        var right = new PdfPCell(new Phrase(value, normal));

        for (var cell: new PdfPCell[]{left,right}) {
            cell.setBorder(6);
            cell.setBorderColor(Color.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        }
        table.addCell(left);
        table.addCell(right);
    }
}
