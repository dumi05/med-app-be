package com.dc.medapp.medappbe.reports;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AttachmentType {
    PDF("application/pdf", "pdf", new int[]{0x25, 0x50, 0x44, 0x46, 0x2D}),
    JPEG("image/jpeg", "jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", new int[]{0x50,0x4B,0x03,0x04});

    public static final int HEAD_SIZE = 8;

    private final String contentType;
    private final String extension;
    @Getter(AccessLevel.NONE)
    private final int[] signature;

    AttachmentType(String contentType, String extension, int[] signature) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
    }

    public static Optional<AttachmentType> detect(byte[] signature) {
        return Arrays.stream(values()).filter(type -> type.matches(signature)).findFirst();
    }

    public static Optional<AttachmentType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(x -> x.contentType.equalsIgnoreCase(contentType)).findFirst();
    }

    private boolean matches(byte[] head) {
        if(head.length < this.signature.length){
            return false;
        }
        for(int i = 0; i < signature.length; i++) {
            if((head[i] & 0xFF) != signature[i]){
                return false;
            }
        }
        return true;
    }
}
