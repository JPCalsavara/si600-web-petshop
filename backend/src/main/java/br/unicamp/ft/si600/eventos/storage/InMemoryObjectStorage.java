package br.unicamp.ft.si600.eventos.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObjectStorage implements ObjectStorage {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        content.transferTo(out);
        objects.put(key, out.toByteArray());
    }

    @Override
    public void delete(String key) { objects.remove(key); }

    @Override
    public String createDownloadUrl(String key, Duration expiration) {
        if (!objects.containsKey(key)) throw new IllegalArgumentException("Objeto não encontrado");
        return "http://test-storage.local/" + key;
    }

    public boolean contains(String key) { return objects.containsKey(key); }
}
