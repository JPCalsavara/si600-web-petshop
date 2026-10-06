package br.unicamp.ft.si600.eventos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.b2")
public class StorageConfig {
    private String endpoint;
    private String region = "us-east-1";
    private String bucket;
    private String keyId;
    private String applicationKey;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getApplicationKey() { return applicationKey; }
    public void setApplicationKey(String applicationKey) { this.applicationKey = applicationKey; }
}
