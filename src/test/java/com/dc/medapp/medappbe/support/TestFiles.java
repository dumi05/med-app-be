package com.dc.medapp.medappbe.support;

import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class TestFiles {
    private TestFiles(){}

    public static byte[] pdfBytes() {
        return "%PDF-1.4\n% referto di prova\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01};
    }

    public static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'};
    }

    public static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "il mio referto.pdf", "application/pdf", pdfBytes());
    }

    public static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "scansione.jpg", "image/jpeg", jpegBytes());
    }

    public static MockMultipartFile png() {
        return new MockMultipartFile("file", "scansione.png", "image/png", pngBytes());
    }

    /** It says "pdf" in the name and in the declared type, but the content is a text: the content decides. */
    public static MockMultipartFile fakePdf() {
        return new MockMultipartFile("file", "referto.pdf", "application/pdf", "this is not a pdf".getBytes(StandardCharsets.US_ASCII));
    }

    public static MockMultipartFile empty() {
        return new MockMultipartFile("file", "vuoto.pdf", "application/pdf", new byte[0]);
    }

    /** A PDF one byte over the limit of 10 MB. */
    public static MockMultipartFile tooLargePdf() {
        var bytes = new byte[10 * 1024 * 1024 + 1];
        Arrays.fill(bytes, (byte) 'x');
        System.arraycopy(pdfBytes(), 0, bytes, 0, 5);
        return new MockMultipartFile("file", "enorme.pdf", "application/pdf", bytes);
    }

    public static byte[] docxBytes() {
        return zip("[Content_Types].xml","<Types/>","word/document.xml", "<document/>");
    }

    public static MockMultipartFile docx(){
        return new MockMultipartFile("file","il mio referto.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes());
    }

    public static MockMultipartFile docxWithMacros(){
        return new MockMultipartFile("file","con-macro.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>", "word/vbaProject.bin","macro"));
    }

    public static MockMultipartFile spreadsheetCalledDocx() {
        return new MockMultipartFile("file", "tabella.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>"));
    }

    public static MockMultipartFile brokenZipCalledDocx() {
        return new MockMultipartFile("file", "rotto.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{'P', 'K', 3, 4, 'n', 'o', 't', ' ', 'a', ' ', 'z', 'i', 'p'});
    }

    public static byte[] zip(String... namesAndContents) {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new java.util.zip.ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static byte[] docxThatIsHugeOnceOpened(int megabytes) {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            var megabyte = new byte[1024 * 1024];
            for (int i = 0; i < megabytes; i++) {
                zip.write(megabyte);
            }
            zip.closeEntry();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
