package br.unicamp.ft.si600.eventos.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public class B2ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public B2ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) throws IOException {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType).contentLength(contentLength).build(),
                    RequestBody.fromInputStream(content, contentLength));
        } catch (RuntimeException ex) {
            throw new IOException("Falha ao enviar PDF para o object storage", ex);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String createDownloadUrl(String key, Duration expiration) {
        var request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket).key(key).build();
        var presign = GetObjectPresignRequest.builder()
                .signatureDuration(expiration).getObjectRequest(request).build();
        return presigner.presignGetObject(presign).url().toString();
    }
}
