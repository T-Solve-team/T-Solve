package vn.tsolve.ticket.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;
import vn.tsolve.ticket.exception.ManualEntryRejectedException;

/**
 * The Evidence files of a manual entry (AC-006.2, AC-006.6, AC-006.7): checks them, puts them in the Workspace file
 * storage and removes them again when the entry is not saved.
 */
@Component
class EvidenceFiles {

    static final int MAX_FILES = 5;
    static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Spec D18; the extension after the last dot, in any case. */
    private static final Set<String> TYPES = Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt", "log");

    private static final Logger log = LoggerFactory.getLogger(EvidenceFiles.class);

    /** Empty when no object storage is configured (tsolve.storage.endpoint unset): files then cannot be saved. */
    private final Optional<FileStorage> storage;

    EvidenceFiles(Optional<FileStorage> storage) {
        this.storage = storage;
    }

    /** One stored file, ready to become an Evidence row. */
    record Stored(String key, String fileName, String fileType, long fileSize) {}

    /** MSG72 for more than five files, otherwise MSG34 for the first file of a wrong type or over 10 MB. */
    void check(List<MultipartFile> files) {
        check(files, 0);
    }

    /** As {@link #check(List)}, with {@code kept} files of a Draft counted too (AC-007.5). */
    void check(List<MultipartFile> files, int kept) {
        if (kept + files.size() > MAX_FILES) {
            throw ManualEntryRejectedException.tooManyFiles();
        }
        for (MultipartFile file : files) {
            if (type(name(file)) == null || file.getSize() > MAX_BYTES) {
                throw ManualEntryRejectedException.fileRejected(name(file));
            }
        }
    }

    /** Stores every file; when one fails, the ones already stored are deleted and the failure is rethrown. */
    List<Stored> store(List<MultipartFile> files) {
        List<Stored> stored = new ArrayList<>();
        if (files.isEmpty()) {
            return stored;
        }
        FileStorage target = storage.orElseThrow(() -> new IllegalStateException("no object storage configured"));
        try {
            for (MultipartFile file : files) {
                try (InputStream content = file.getInputStream()) {
                    StoredFile saved = target.store(content);
                    // the name is kept as given; the key, not the name, locates the object (edge 17)
                    stored.add(new Stored(saved.key(), name(file), type(name(file)), saved.sizeBytes()));
                }
            }
            return stored;
        } catch (IOException e) {
            delete(stored);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            delete(stored);
            throw e;
        }
    }

    /** Best effort: a file left behind costs storage, not correctness, so a failed delete is only logged. */
    void delete(List<Stored> stored) {
        deleteKeys(stored.stream().map(Stored::key).toList());
    }

    /** As {@link #delete(List)}, by storage key: the files of a Draft (AC-007.5, AC-007.7). */
    void deleteKeys(List<String> keys) {
        storage.ifPresent(target -> {
            for (String key : keys) {
                try {
                    target.delete(key);
                } catch (RuntimeException e) {
                    // the key only: the file name can hold personal data (backend/AGENTS.md §9)
                    log.warn(
                            "Could not delete the Evidence object {}: {}",
                            key,
                            e.getClass().getSimpleName());
                }
            }
        });
    }

    private static String name(MultipartFile file) {
        return file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    }

    /** The stored type: the lower-cased extension, {@code jpeg} as {@code jpg}; null when not allowed. */
    static String type(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(extension)) {
            return null;
        }
        return extension.equals("jpeg") ? "jpg" : extension;
    }
}
