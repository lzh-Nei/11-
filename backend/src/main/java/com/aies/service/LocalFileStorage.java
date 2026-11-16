package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.FileStoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件系统实现（filestorage.mode=local 默认装配；无容器场景/单测兜底）。
 *
 * <p>布局沿用 FileStoreService 的 files/ 目录：key 直接映射 {baseDir}/files/{key}
 * （UUID32 与 golden/、parsed/ 前缀 key 均可），与 MinIO 实现保持 key 同形，
 * 切换仅换装配、零迁移。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "filestorage.mode", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path fileDir;

    public LocalFileStorage(FileStoreProperties props) throws IOException {
        this.fileDir = Paths.get(props.getBaseDir()).toAbsolutePath().normalize().resolve("files");
        Files.createDirectories(fileDir);
        log.info("LocalFileStorage 装配: dir={}", fileDir);
    }

    @Override
    public String store(String key, InputStream in, long size) {
        validateKey(key);
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
            }
            return key;
        } catch (IOException e) {
            log.error("本地落盘失败: key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "文件落盘失败，请重试");
        }
    }

    @Override
    public InputStream openStream(String key) {
        validateKey(key);
        Path target = resolveSafe(key);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储读取失败");
        }
    }

    @Override
    public long size(String key) {
        validateKey(key);
        Path target = resolveSafe(key);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String storeCold(String key, InputStream in, long size) {
        // key 同形映射 files/cold/{key}（与 MinIO swe-cold 语义对齐）
        return store("cold/" + key, in, size);
    }

    @Override
    public String presignedGetUrl(String key, int expirySeconds) {
        // 本地模式无私有桶签名语义：S-03/S-04 签名URL仅 minio 模式可用（登记口径）
        throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "本地模式不支持签名URL");
    }

    /** key→本地路径映射，resolve 后必须仍在 files/ 内（双保险防穿越） */
    private Path resolveSafe(String key) {
        Path target = fileDir.resolve(key).normalize();
        if (!target.startsWith(fileDir)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "非法对象key");
        }
        return target;
    }
}
