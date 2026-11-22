package vn.tsolve.wiki.dto;

import vn.tsolve.wiki.entity.WikiDocumentType;

public record WikiUploadPreview(
        String fileName, String title, String excerpt, int wordCount, String result, WikiDocumentType documentType) {}
