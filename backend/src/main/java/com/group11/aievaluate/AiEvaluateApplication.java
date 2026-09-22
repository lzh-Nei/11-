package com.group11.aievaluate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.group11.aievaluate.mapper")
public class AiEvaluateApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEvaluateApplication.class, args);
    }
}