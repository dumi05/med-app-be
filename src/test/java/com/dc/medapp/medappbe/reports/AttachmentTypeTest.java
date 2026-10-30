package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.support.TestFiles;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class AttachmentTypeTest {
    private byte[] head(byte[] content) {
        return Arrays.copyOf(content, Math.min(content.length, AttachmentType.HEAD_SIZE));
    }

    @Test
    void detect_recognizesAPdf() {
        assertThat(AttachmentType.detect(head(TestFiles.pdfBytes()))).contains(AttachmentType.PDF);
    }

    @Test
    void detect_recognizesAJpeg() {
        assertThat(AttachmentType.detect(head(TestFiles.jpegBytes()))).contains(AttachmentType.JPEG);
    }

    @Test
    void detect_recognizesAPng() {
        assertThat(AttachmentType.detect(head(TestFiles.pngBytes()))).contains(AttachmentType.PNG);
    }

    @Test
    void detect_doesNotRecognizeATextThatLooksLikeAPdfOnlyByItsName() {
        assertThat(AttachmentType.detect(head("this is not a pdf".getBytes()))).isEmpty();
    }

    @Test
    void detect_needsTheWholeSignature() {
        // "%PDF" without the dash, and the first byte of a PNG alone
        assertThat(AttachmentType.detect("%PDF".getBytes())).isEmpty();
        assertThat(AttachmentType.detect(new byte[]{(byte) 0x89})).isEmpty();
    }

    @Test
    void detect_refusesAnEmptyContent() {
        assertThat(AttachmentType.detect(new byte[0])).isEmpty();
    }

    @Test
    void detect_refusesAnExecutable() {
        assertThat(AttachmentType.detect(new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00})).isEmpty();
    }

    @Test
    void theTypeAndTheExtensionGoTogether() {
        assertThat(AttachmentType.fromContentType("application/pdf")).contains(AttachmentType.PDF);
        assertThat(AttachmentType.PDF.getExtension()).isEqualTo("pdf");
        assertThat(AttachmentType.JPEG.getExtension()).isEqualTo("jpg");
        assertThat(AttachmentType.PNG.getContentType()).isEqualTo("image/png");
        assertThat(AttachmentType.fromContentType("text/html")).isEmpty();
    }
}
