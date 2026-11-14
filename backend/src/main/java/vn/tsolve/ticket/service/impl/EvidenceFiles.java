package vn.tsolve.ticket.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import vn.tsolve.common.exception.InvalidFieldsException;
import vn.tsolve.common.exception.ProblemDetails.FieldError;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.ticket.dto.EvidenceUpload;
import vn.tsolve.ticket.exception.StorageUnavailableException;

/** File rules and object operations shared by Manual entry and Draft deletion. Calls stay outside transactions. */
final class EvidenceFiles {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFiles.class);
    private static final long MAX_SIZE = 10_485_760L;
    private static final int MAX_FILES = 5;
    private static final Map<String, String> TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "txt", "text/plain",
            "log", "text/plain");

    private final ObjectProvider<FileStorage> storage;

    EvidenceFiles(ObjectProvider<FileStorage> storage) {
        this.storage = storage;
    }

    static void validate(List<EvidenceUpload> files, int keptCount) {
        for (EvidenceUpload file : files) {
            if (!TYPES.containsKey(extension(file.fileName())) || file.size() > MAX_SIZE) {
                String name = file.fileName() == null ? "" : file.fileName();
                throw new InvalidFieldsException(List.of(new FieldError("files", "MSG34", Map.of("file_name", name))));
            }
        }
        if (keptCount + files.size() > MAX_FILES) {
            throw new InvalidFieldsException(List.of(new FieldError("files", "MSG72", Map.of())));
        }
    }

    List<StoredEvidence> store(List<EvidenceUpload> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        FileStorage target;
        try {
            target = storage.getIfAvailable();
        } catch (RuntimeException e) {
            throw new StorageUnavailableException();
        }
        if (target == null) {
            throw new StorageUnavailableException();
        }
        List<StoredEvidence> stored = new ArrayList<>();
        try {
            for (EvidenceUpload file : files) {
                try (InputStream content = file.content().getInputStream()) {
                    StoredFile result = target.store(content);
                    String type = file.contentType();
                    stored.add(new StoredEvidence(
                            file.fileName(),
                            type == null || type.isBlank() ? TYPES.get(extension(file.fileName())) : type,
                            result.sizeBytes(),
                            result.key()));
                }
            }
        } catch (IOException | RuntimeException e) {
            delete(target, stored.stream().map(StoredEvidence::key).toList());
            throw new StorageUnavailableException();
        }
        return stored;
    }

    void delete(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        FileStorage target;
        try {
            target = storage.getIfAvailable();
        } catch (RuntimeException e) {
            keys.forEach(key -> log.warn("Evidence storage unavailable; could not delete key={}", key));
            return;
        }
        if (target == null) {
            keys.forEach(key -> log.warn("Evidence storage unavailable; could not delete key={}", key));
            return;
        }
        delete(target, keys);
    }

    private static void delete(FileStorage target, List<String> keys) {
        for (String key : keys) {
            try {
                target.delete(key);
            } catch (RuntimeException e) {
                // A filename, exception message or file content must never reach the log.
                log.warn("Could not delete Evidence key={}", key);
            }
        }
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    record StoredEvidence(String fileName, String fileType, long size, String key) {}
}
