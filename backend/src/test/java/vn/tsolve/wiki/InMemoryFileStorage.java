package vn.tsolve.wiki;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.tsolve.platform.dto.StoredFile;
import vn.tsolve.platform.service.FileStorage;

public class InMemoryFileStorage implements FileStorage {
    public final Map<String, byte[]> files = new ConcurrentHashMap<>();
    public int failOnStore = Integer.MAX_VALUE;
    private int calls;
    public int closedReads;

    public void clear() {
        files.clear();
        calls = 0;
        closedReads = 0;
        failOnStore = Integer.MAX_VALUE;
    }

    @Override
    public synchronized StoredFile store(InputStream content) {
        if (++calls == failOnStore) throw new IllegalStateException("Storage unavailable");
        try {
            byte[] bytes = content.readAllBytes();
            String key = UUID.randomUUID().toString();
            files.put(key, bytes);
            return new StoredFile(key, bytes.length);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Override
    public InputStream read(String key) {
        return new ByteArrayInputStream(files.get(key)) {
            @Override
            public void close() throws IOException {
                closedReads++;
                super.close();
            }
        };
    }

    @Override
    public URI signedUrl(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String key) {
        files.remove(key);
    }
}
