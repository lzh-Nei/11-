package com.group11.aievaluate.controller;

import com.group11.aievaluate.model.FileMeta;
import com.group11.aievaluate.service.FileStoreService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 下载相关接口 —— 高并发与断点续传的关键都在这里：
 *
 * 1. 流式写出：从文件随机偏移开始、按 64KB 缓冲逐块写 socket，
 *    全程不把文件读进内存，单机即可支撑大量并发下载连接（配合 Tomcat 线程池配置）。
 * 2. HTTP Range：客户端带 `Range: bytes=start-end` 头时返回 206 + Content-Range，
 *    只传输指定字节区间 —— 这是断点续传与多线程分段下载的协议基础。
 */
@RestController
@RequestMapping("/api/download")
public class DownloadController {

    private static final int COPY_BUFFER = 64 * 1024;

    private final FileStoreService store;

    public DownloadController(FileStoreService store) {
        this.store = store;
    }

    /** 文件元信息：多线程下载前用它获取文件名与总大小，用于切分 Range 区间 */
    @GetMapping("/info/{id}")
    public FileMeta info(@PathVariable String id) throws IOException {
        return store.getMeta(id);
    }

    /**
     * 下载（支持 Range）。
     * - 无 Range 头：200 全量传输；
     * - 带 Range 头：206 Partial Content + Content-Range，只传输请求区间。
     */
    @GetMapping("/{id}")
    public void download(@PathVariable String id,
                         @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                         HttpServletResponse response) throws IOException {
        FileMeta meta = store.getMeta(id);
        Path path = store.resolveFile(id);
        long size = meta.size();

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        String encoded = URLEncoder.encode(meta.name(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);

        long start = 0;
        long length = size;
        boolean partial = false;

        if (range != null && !range.isBlank() && size > 0) {
            List<HttpRange> ranges;
            try {
                ranges = HttpRange.parseRanges(range);
            } catch (IllegalArgumentException e) {
                rangeNotSatisfiable(response, size);
                return;
            }
            // 本服务只支持单区间；多区间请求直接 416
            if (ranges.size() != 1) {
                rangeNotSatisfiable(response, size);
                return;
            }
            long s = ranges.get(0).getRangeStart(size);
            long e = ranges.get(0).getRangeEnd(size); // 对超出部分会自动收敛到 size-1
            if (s > e || s >= size) {
                rangeNotSatisfiable(response, size);
                return;
            }
            start = s;
            length = e - s + 1;
            partial = true;
        }

        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        if (partial) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + (start + length - 1) + "/" + size);
        }
        response.setContentLengthLong(length);

        // 核心：定位到 start 偏移后，精确拷贝 length 字节到响应流（NIO 流式，不占大内存）
        try (InputStream in = Files.newInputStream(path)) {
            skipFully(in, start);
            OutputStream out = response.getOutputStream();
            byte[] buf = new byte[COPY_BUFFER];
            long remaining = length;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    private void rangeNotSatisfiable(HttpServletResponse response, long size) {
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
    }

    /** 从当前位置向前跳过 count 字节（skip 返回 0 时回退到逐字节读，避免死循环） */
    private static void skipFully(InputStream in, long count) throws IOException {
        while (count > 0) {
            long skipped = in.skip(count);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    break;
                }
                skipped = 1;
            }
            count -= skipped;
        }
    }
}
