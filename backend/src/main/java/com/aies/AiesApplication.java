package com.aies;

import com.aies.config.AiProperties;
import com.aies.config.FileStorageProperties;
import com.aies.config.FileStoreProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({FileStoreProperties.class, FileStorageProperties.class, AiProperties.class})
@MapperScan("com.aies.mapper")
public class AiesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiesApplication.class, args);
    }
}
