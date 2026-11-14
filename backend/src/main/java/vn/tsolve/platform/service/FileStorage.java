package vn.tsolve.platform.service;

import java.io.InputStream;
import java.net.URI;
import vn.tsolve.platform.dto.StoredFile;

public interface FileStorage { // STORY-007, object storage (Garage)
    StoredFile store(InputStream content);

    /**
     * The caller must close the returned stream with try-with-resources, because it holds a pooled HTTP connection
     * to the object store until closed.
     */
    InputStream read(String key);

    URI signedUrl(String key); // AC-007.12, expires after FILE_STORAGE_URL_TTL_SECONDS

    void delete(String key);
}
