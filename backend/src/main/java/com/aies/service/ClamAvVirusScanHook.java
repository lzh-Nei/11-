package com.aies.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * ClamAV（clamd INSTREAM）病毒扫描实现。
 *
 * <p>启用条件：{@code app.upload.clamav.enabled=true}。协议：发送 {@code zINSTREAM\0}，
 * 逐块写 4 字节大端长度 + 数据，零长度结束，读取 {@code stream: OK} / {@code ... FOUND}。
 * 连接失败/超时等异常按 fail-closed 拒绝上传（返回 false），并记录 WARN。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.upload.clamav", name = "enabled", havingValue = "true")
public class ClamAvVirusScanHook implements VirusScanHook {

    private static final int CHUNK_SIZE = 8192;
    private static final int MAX_RESPONSE_BYTES = 1024;

    @Value("${app.upload.clamav.host:localhost}")
    private String host;

    @Value("${app.upload.clamav.port:3310}")
    private int port;

    @Value("${app.upload.clamav.timeout-ms:3000}")
    private int timeoutMs;

    @Override
    public boolean clean(MultipartFile file) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK_SIZE];
            try (InputStream in = file.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(lengthBytes(read));
                    out.write(buffer, 0, read);
                }
            }
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            String response = readResponse(socket.getInputStream());
            boolean clean = response.contains("OK") && !response.contains("FOUND");
            if (!clean) {
                log.warn("ClamAV 查杀命中: file={} response={}", file.getOriginalFilename(), response);
            }
            return clean;
        } catch (IOException e) {
            // fail-closed：扫描服务不可用时拒绝上传，避免带毒文件绕过校验
            log.warn("ClamAV 扫描失败（fail-closed 拒绝上传）: host={} port={} err={}",
                    host, port, e.getMessage());
            return false;
        }
    }

    private static byte[] lengthBytes(int length) {
        return new byte[]{
                (byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length};
    }

    private static String readResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int value;
        while (sb.length() < MAX_RESPONSE_BYTES && (value = in.read()) != -1) {
            if (value == 0) {
                break;
            }
            sb.append((char) value);
        }
        return sb.toString().trim();
    }
}
