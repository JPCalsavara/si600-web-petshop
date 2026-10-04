package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.storage.B2ObjectStorage;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "storage.b2.enabled", havingValue = "true")
@EnableConfigurationProperties(StorageConfig.class)
public class StorageConfiguration {
    @Bean
    S3Client b2S3Client(StorageConfig config) {
        return S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getKeyId(), config.getApplicationKey())))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    S3Presigner b2S3Presigner(StorageConfig config) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getKeyId(), config.getApplicationKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    ObjectStorage objectStorage(S3Client client, S3Presigner presigner, StorageConfig config) {
        return new B2ObjectStorage(client, presigner, config.getBucket());
    }
}
