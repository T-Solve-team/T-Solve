package vn.tsolve.wiki.dto;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record WikiDocumentFile(String fileName, String contentType, long fileSize, StreamingResponseBody content) {}
