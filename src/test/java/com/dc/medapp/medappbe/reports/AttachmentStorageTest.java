package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AttachmentStorageTest {
    @TempDir
    Path folder;

    private AttachmentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new AttachmentStorage(folder.resolve("attachments").toString());
    }

    @Test
    void createsTheFolderWhenItIsNotThere() {
        assertThat(folder.resolve("attachments")).isDirectory();
    }

    @Test
    void save_writesTheContentUnderARandomUuidName() throws IOException {
        var name = storage.save(new ByteArrayInputStream("hello".getBytes()));

        assertThat(UUID.fromString(name).toString()).isEqualTo(name);
        assertThat(Files.readString(folder.resolve("attachments").resolve(name))).isEqualTo("hello");
    }

    @Test
    void save_neverReusesAName() {
        var first = storage.save(new ByteArrayInputStream("a".getBytes()));
        var second = storage.save(new ByteArrayInputStream("a".getBytes()));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void open_returnsTheFileThatWasSaved() throws IOException {
        var name = storage.save(new ByteArrayInputStream("hello".getBytes()));

        var resource = storage.open(name);

        assertThat(resource.exists()).isTrue();
        assertThat(new String(resource.getInputStream().readAllBytes())).isEqualTo("hello");
    }

    @Test
    void open_throwsResourceNotFoundException_whenTheFileIsNotThere() {
        assertThrows(ResourceNotFoundException.class, () -> storage.open(UUID.randomUUID().toString()));
    }

    @Test
    void open_refusesANameThatIsNotAUuid_soThatNothingOutsideTheFolderCanBeRead() throws IOException {
        Files.writeString(folder.resolve("secret.txt"), "secret");

        assertThrows(IllegalArgumentException.class, () -> storage.open("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.open("secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.open(""));
    }

    @Test
    void open_refusesAUuidWrittenInAnotherForm() {
        var upper = UUID.randomUUID().toString().toUpperCase();

        assertThrows(IllegalArgumentException.class, () -> storage.open(upper));
    }

    @Test
    void delete_removesTheFile() throws IOException {
        var name = storage.save(new ByteArrayInputStream("hello".getBytes()));

        storage.delete(name);

        try (Stream<Path> files = Files.list(folder.resolve("attachments"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void delete_doesNotFail_whenTheFileIsNotThere() {
        assertDoesNotThrow(() -> storage.delete(UUID.randomUUID().toString()));
        assertDoesNotThrow(() -> storage.delete("not-a-uuid"));
    }
}
