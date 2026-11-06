package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.support.TestFiles;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocxInspectorTest {

    private static void check(byte[] content) {
        DocxInspector.requireWordDocument(new ByteArrayInputStream(content));
    }

    @Test
    void aWordDocument_isAccepted() {
        assertThatCode(() -> check(TestFiles.docxBytes())).doesNotThrowAnyException();
    }

    @Test
    void aWordDocumentWithOtherPartsToo_isAccepted() {
        var content = TestFiles.zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>",
                "word/styles.xml", "<styles/>", "docProps/core.xml", "<core/>");

        assertThatCode(() -> check(content)).doesNotThrowAnyException();
    }

    @Test
    void aSpreadsheet_isRefused_eventhoughItIsAZipToo() {
        var content = TestFiles.zip("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>");

        assertThatThrownBy(() -> check(content)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non è un documento Word");
    }

    @Test
    void aZipWithoutTheTypesList_orWithoutTheDocument_isRefused() {
        assertThatThrownBy(() -> check(TestFiles.zip("word/document.xml", "<document/>"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> check(TestFiles.zip("[Content_Types].xml", "<Types/>"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptyZip_isRefused() {
        assertThatThrownBy(() -> check(TestFiles.zip())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWordDocumentWithMacros_isRefused() {
        var content = TestFiles.zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>", "word/vbaProject.bin", "x");

        assertThatThrownBy(() -> check(content)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("macro");
    }

    @Test
    void anEntryThatClimbsOutOfTheArchive_isRefused() {
        for (var name : new String[]{"../evil.txt", "word/../../evil.txt", "/etc/passwd", "\\windows\\evil.txt", "c:evil.txt"}) {
            var content = TestFiles.zip("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>", name, "x");

            assertThatThrownBy(() -> check(content)).withFailMessage(name).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("percorso");
        }
    }

    @Test
    void anArchiveWithTooManyEntries_isRefused_butThe1000Limit_isAllowed() {
        assertThatCode(() -> check(withEntries(DocxInspector.MAX_ENTRIES))).doesNotThrowAnyException();
        assertThatThrownBy(() -> check(withEntries(DocxInspector.MAX_ENTRIES + 1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("troppi");
    }

    /** A Word document with {@code count} entries in all. */
    private static byte[] withEntries(int count) {
        var parts = new ArrayList<String>(java.util.List.of("[Content_Types].xml", "<Types/>", "word/document.xml", "<document/>"));
        for (int i = 2; i < count; i++) {
            parts.add("word/media/image" + i + ".png");
            parts.add("x");
        }
        return TestFiles.zip(parts.toArray(String[]::new));
    }

    @Test
    void aZipBomb_isRefused_withoutOpeningItAll() {
        var bomb = TestFiles.docxThatIsHugeOnceOpened(101);

        assertThat(bomb.length).isLessThan(1024 * 1024);
        assertThatThrownBy(() -> check(bomb)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("troppo grande");
    }

    @Test
    void aBigDocumentUnderTheLimit_isAccepted() {
        assertThatCode(() -> check(TestFiles.docxThatIsHugeOnceOpened(5))).doesNotThrowAnyException();
    }

    @Test
    void bytesThatOnlyStartLikeAZip_areRefused() {
        assertThatThrownBy(() -> check(TestFiles.brokenZipCalledDocx().getBytes())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnreadableContent_isRefused() throws IOException {
        var broken = new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk");
            }
        };

        assertThatThrownBy(() -> DocxInspector.requireWordDocument(broken)).isInstanceOf(IllegalArgumentException.class);
    }
}