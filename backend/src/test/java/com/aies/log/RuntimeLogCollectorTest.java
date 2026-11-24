package com.aies.log;

import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行日志采集器单测：ROOT 挂载后可检索、按级别过滤、可清空。
 */
class RuntimeLogCollectorTest {

    @Test
    void capturesAndFiltersRuntimeLogs() {
        RuntimeLogCollector collector = new RuntimeLogCollector();
        Logger logger = (Logger) LoggerFactory.getLogger("com.aies.test.RuntimeLogCollectorTest");
        try {
            collector.start();
            logger.error("采集测试-错误");
            logger.info("采集测试-普通");

            var all = collector.query(1, 10, null, "采集测试", null);
            assertThat(all.total()).isEqualTo(2);

            var errors = collector.query(1, 10, "ERROR", "采集测试", null);
            assertThat(errors.total()).isEqualTo(1);
            assertThat(errors.records().get(0).level()).isEqualTo("ERROR");

            assertThat(collector.stats().errorCount()).isGreaterThanOrEqualTo(1);
            assertThat(collector.stats().capturing()).isTrue();
        } finally {
            collector.stop();
        }
        assertThat(collector.clear()).isGreaterThanOrEqualTo(0);
        assertThat(collector.stats().captured()).isZero();
        assertThat(collector.stats().capturing()).isFalse();
    }
}
