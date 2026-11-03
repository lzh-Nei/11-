package com.group11.aievaluate;

import com.group11.aievaluate.config.FileStoreProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStoreProperties.class)
@MapperScan("com.group11.aievaluate.mapper")
public class AiEvaluateApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEvaluateApplication.class, args);
    }
}