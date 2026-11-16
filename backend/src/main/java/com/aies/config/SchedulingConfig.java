package com.aies.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度开关（W8-M1 V6：留痕补偿定时任务TraceCompensationService.retryPending承载；
 * 主代码此前无调度器，本配置为唯一@EnableScheduling入口）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
