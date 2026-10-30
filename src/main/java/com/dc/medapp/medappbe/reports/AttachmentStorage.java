package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@Slf4j
public class AttachmentStorage {

    private final Path root;

    public AttachmentStorage(@Value("${app.storage.dir}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create the folder of the attachments: " + root, ex);
        }
    }

    public String save(InputStream content) {
        var storeName = UUID.randomUUID().toString();
        try{
            Files.copy(content, root.resolve(storeName));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the attachment", e);
        }
        return storeName;
    }

    public Resource open(String storedName) {
        var path = pathOf(storedName);
        if(!Files.isRegularFile(path)){
            throw new ResourceNotFoundException("Allegato non trovato.");
        }
        return new FileSystemResource(path);
    }

    public void delete (String storedName) {
        try {
            Files.deleteIfExists(pathOf(storedName));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete the attachment: {}", storedName, e);
        }
    }

    private Path pathOf(String storedName) {
        try {
            if(!UUID.fromString(storedName).toString().equals(storedName)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid name of attachment");
        }
        return root.resolve(storedName);
    }
}
