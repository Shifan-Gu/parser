package tidebound.config;

import tidebound.S3Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public S3Service s3Service() {
        return new S3Service();
    }
}

