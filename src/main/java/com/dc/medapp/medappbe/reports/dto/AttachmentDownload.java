package com.dc.medapp.medappbe.reports.dto;

import org.springframework.core.io.Resource;

public record AttachmentDownload(Resource resource, String contentType, Long sizeBytes, String filename) {
}
