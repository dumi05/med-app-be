package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.reports.dto.AttachmentDownload;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class AttachmentHttp {
    private AttachmentHttp(){}

    public static ResponseEntity<Resource> asDownload(AttachmentDownload req) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(req.contentType()))
                .contentLength(req.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(req.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(req.resource());
    }
}
