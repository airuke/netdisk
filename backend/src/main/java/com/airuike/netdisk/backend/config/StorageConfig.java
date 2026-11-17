package com.airuike.netdisk.backend.config;
import io.minio.MinioClient;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({StorageProperties.class, UploadProperties.class})
public class StorageConfig {
    @Bean(destroyMethod="close")
    public MinioClient minioClient(StorageProperties p) {
        MinioClient client=MinioClient.builder().endpoint(p.endpoint())
                .credentials(p.accessKey(),p.secretKey()).build();
        client.setTimeout(10000,180000,180000);
        return client;
    }
    @Bean(destroyMethod="close")
    public MinioClient uploadMinioClient(StorageProperties p) {
        var http=new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(3)).writeTimeout(Duration.ofMinutes(3))
                .callTimeout(Duration.ofMinutes(3)).build();
        var client=MinioClient.builder().endpoint(p.endpoint()).credentials(p.accessKey(),p.secretKey())
                .httpClient(http,true).build();
        client.setRetry(null,null,null);
        return client;
    }
}
