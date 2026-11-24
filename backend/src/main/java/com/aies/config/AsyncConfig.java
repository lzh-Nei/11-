package com.aies.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务线程池（报表导出等旁路能力）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reportExportExecutor")
    public ThreadPoolTaskExecutor reportExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("report-export-");
        executor.initialize();
        return executor;
    }
}
