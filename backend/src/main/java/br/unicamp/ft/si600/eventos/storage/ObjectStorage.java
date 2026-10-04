package br.unicamp.ft.si600.eventos.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorage {
    void upload(String key, InputStream content, long contentLength, String contentType) throws IOException;
    void delete(String key);
    String createDownloadUrl(String key, Duration expiration);
}
