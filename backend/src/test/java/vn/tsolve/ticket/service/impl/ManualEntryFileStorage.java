package vn.tsolve.ticket.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;

/** An in-memory Workspace file storage for the manual entry tests; it can fail a given store call or every delete. */
final class ManualEntryFileStorage implements FileStorage {

    final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    final List<String> deleted = new CopyOnWriteArrayList<>();
    /** The store call (from 1) that fails, as object storage down would; 0 = none. */
    volatile int failOnStore;

    volatile boolean failDelete;
    private final AtomicInteger stores = new AtomicInteger();

    void reset() {
        objects.clear();
        deleted.clear();
        stores.set(0);
        failOnStore = 0;
        failDelete = false;
    }

    @Override
    public StoredFile store(InputStream content) {
        if (stores.incrementAndGet() == failOnStore) {
            throw new UncheckedIOException(new IOException("storage down"));
        }
        try {
            byte[] bytes = content.readAllBytes();
            String key = UUID.randomUUID().toString();
            objects.put(key, bytes);
            return new StoredFile(key, bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream read(String key) {
        return new ByteArrayInputStream(objects.get(key));
    }

    @Override
    public URI signedUrl(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String key) {
        deleted.add(key);
        if (failDelete) {
            throw new UncheckedIOException(new IOException("storage down"));
        }
        objects.remove(key);
    }
}
