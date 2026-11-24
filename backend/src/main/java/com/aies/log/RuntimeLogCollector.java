package com.aies.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理员运行日志采集器：以 Logback 内存 Appender 挂载到 ROOT，滚动保留最近日志。
 *
 * <p>仅内存保存、不落盘、不外发；容量固定，写满后丢弃最旧记录。查询支持级别阈值、
 * 关键字与 logger 名称过滤，供管理端“日志与审计”窗口只读检索。</p>
 */
@Component
public class RuntimeLogCollector {

    /** 环形缓冲容量（条） */
    public static final int CAPACITY = 2000;

    /** 单条消息截断上限（字符） */
    private static final int MESSAGE_LIMIT = 2000;

    /** 异常堆栈截断上限（字符） */
    private static final int DETAIL_LIMIT = 4000;

    private final Deque<LogEntry> buffer = new ArrayDeque<>(CAPACITY);
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong warnCount = new AtomicLong();
    private final LocalDateTime startedAt = LocalDateTime.now();

    private volatile AppenderBase<ILoggingEvent> appender;
    private volatile boolean capturing;

    @PostConstruct
    void start() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        CollectorAppender collector = new CollectorAppender();
        collector.setContext(context);
        collector.setName("ADMIN_RUNTIME_LOG_COLLECTOR");
        collector.start();
        root.addAppender(collector);
        this.appender = collector;
        this.capturing = true;
    }

    @PreDestroy
    void stop() {
        AppenderBase<ILoggingEvent> current = this.appender;
        if (current != null) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(current);
            current.stop();
        }
        this.capturing = false;
    }

    /**
     * 按条件分页查询采集缓冲（时间倒序）。
     *
     * @param page    页码（从1开始）
     * @param size    每页条数（最大200）
     * @param level   最低级别（TRACE/DEBUG/INFO/WARN/ERROR，空表示不过滤）
     * @param keyword 消息/logger/异常堆栈关键字（忽略大小写）
     * @param logger  logger 名称片段（忽略大小写）
     */
    public LogPage query(long page, long size, String level, String keyword, String logger) {
        Level threshold = parseLevel(level);
        String keywordFilter = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        String loggerFilter = StringUtils.hasText(logger)
                ? logger.trim().toLowerCase(Locale.ROOT) : null;

        List<LogEntry> matched = new ArrayList<>();
        synchronized (buffer) {
            for (LogEntry entry : buffer) {
                if (threshold != null && !Level.valueOf(entry.level()).isGreaterOrEqual(threshold)) {
                    continue;
                }
                if (loggerFilter != null
                        && !entry.logger().toLowerCase(Locale.ROOT).contains(loggerFilter)) {
                    continue;
                }
                if (keywordFilter != null && !matchesKeyword(entry, keywordFilter)) {
                    continue;
                }
                matched.add(entry);
            }
        }

        long matchedTotal = matched.size();
        long safeSize = Math.max(1, Math.min(size, 200));
        long from = Math.max(0, page - 1) * safeSize;
        if (from >= matchedTotal) {
            return new LogPage(List.of(), matchedTotal);
        }
        int to = (int) Math.min(from + safeSize, matchedTotal);
        return new LogPage(List.copyOf(matched.subList((int) from, to)), matchedTotal);
    }

    /** 清空缓冲，返回被清掉的条数 */
    public int clear() {
        synchronized (buffer) {
            int removed = buffer.size();
            buffer.clear();
            return removed;
        }
    }

    /** 采集器运行指标快照 */
    public LogStats stats() {
        int captured;
        synchronized (buffer) {
            captured = buffer.size();
        }
        return new LogStats(CAPACITY, captured, total.get(), errorCount.get(), warnCount.get(),
                startedAt, capturing);
    }

    private void capture(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        String loggerName = event.getLoggerName();
        if (loggerName == null || loggerName.startsWith("ch.qos.logback")
                || loggerName.startsWith("com.aies.log.")) {
            return;
        }
        try {
            LogEntry entry = new LogEntry(
                    sequence.incrementAndGet(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()),
                            ZoneId.systemDefault()),
                    event.getLevel().toString(),
                    loggerName,
                    event.getThreadName() == null ? "" : event.getThreadName(),
                    truncate(event.getFormattedMessage(), MESSAGE_LIMIT),
                    event.getThrowableProxy() == null ? null
                            : truncate(ThrowableProxyUtil.asString(event.getThrowableProxy()),
                                    DETAIL_LIMIT));
            total.incrementAndGet();
            if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
                errorCount.incrementAndGet();
            } else if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                warnCount.incrementAndGet();
            }
            synchronized (buffer) {
                if (buffer.size() >= CAPACITY) {
                    buffer.removeLast();
                }
                buffer.addFirst(entry);
            }
        } catch (RuntimeException ignored) {
            // 采集失败不得影响业务日志输出
        }
    }

    private boolean matchesKeyword(LogEntry entry, String keyword) {
        return entry.message().toLowerCase(Locale.ROOT).contains(keyword)
                || entry.logger().toLowerCase(Locale.ROOT).contains(keyword)
                || (entry.detail() != null
                        && entry.detail().toLowerCase(Locale.ROOT).contains(keyword));
    }

    private Level parseLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        return Level.toLevel(level.trim().toUpperCase(Locale.ROOT), null);
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    /** 内存日志条目 */
    public record LogEntry(long seq, LocalDateTime time, String level, String logger,
                           String thread, String message, String detail) {
    }

    /** 过滤后的单页日志 */
    public record LogPage(List<LogEntry> records, long total) {
    }

    /** 采集器运行指标 */
    public record LogStats(int capacity, int captured, long total, long errorCount, long warnCount,
                           LocalDateTime startedAt, boolean capturing) {
    }

    /** Logback 内存 Appender 适配器 */
    private final class CollectorAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent eventObject) {
            capture(eventObject);
        }
    }
}
