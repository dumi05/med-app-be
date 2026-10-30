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
}
