package vn.tsolve.ticket.dto;

import org.springframework.core.io.InputStreamSource;

/** A new Evidence file; the service opens and closes its content without depending on multipart web types. */
public record EvidenceUpload(String fileName, String contentType, long size, InputStreamSource content) {}
