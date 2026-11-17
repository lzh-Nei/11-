package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.FileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 实现（swe-hot 真实落桶，D-23 兑现 / M1 硬边界）。
 *
 * <p>mode=filestorage.mode=minio 时装配；桶由 compose minio-init 预建
 * （swe-hot/swe-cold/backup），key 与本地实现同形（UUID32 或带前缀），
 * golden_dataset 零改动。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "filestorage.mode", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    private final MinioClient client;
    private final MinioClient presignClient;
    private final String bucketHot;
    private final String bucketCold;

    public MinioFileStorage(FileStorageProperties props) {
        this.client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        String publicEndpoint = props.getPublicEndpoint();
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            publicEndpoint = props.getEndpoint();
        }
        this.presignClient = MinioClient.builder()
                .endpoint(publicEndpoint)
                .region("us-east-1")
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        this.bucketHot = props.getBucketHot();
        this.bucketCold = props.getBucketCold();
        log.info("MinioFileStorage 装配: endpoint={}, publicEndpoint={}, bucket={}",
                props.getEndpoint(), publicEndpoint, bucketHot);
    }

    @Override
    public String store(String key, InputStream in, long size) {
        validateKey(key);
        try (in) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucketHot)
                    .object(key)
                    .stream(in, size, ObjectWriteArgs.MAX_PART_SIZE)
                    .contentType("application/octet-stream")
                    .build());
            return key;
        } catch (Exception e) {
            log.error("MinIO 落桶失败: key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储写入失败");
        }
    }

    @Override
    public String storeCold(String key, InputStream in, long size) {
        validateKey(key);
        try (in) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucketCold)
                    .object(key)
                    .stream(in, size, ObjectWriteArgs.MAX_PART_SIZE)
                    .contentType("application/json; charset=utf-8")
                    .build());
            return key;
        } catch (Exception e) {
            log.error("MinIO 冷桶落盘失败: key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "冷桶写入失败");
        }
    }

    @Override
    public InputStream openStream(String key) {
        validateKey(key);
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucketHot).object(key).build());
        } catch (Exception e) {
            throw notFoundOrSystem(key, e);
        }
    }

    @Override
    public long size(String key) {
        validateKey(key);
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucketHot).object(key).build());
            return stat.size();
        } catch (Exception e) {
            throw notFoundOrSystem(key, e);
        }
    }

    @Override
    public String presignedGetUrl(String key, int expirySeconds) {
        validateKey(key);
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketHot)
                    .object(key)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 签名URL生成失败: key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "签名URL生成失败");
        }
    }

    /** MinIO SDK 异常归一：对象不存在→40400 同形，其余→50000 */
    private BusinessException notFoundOrSystem(String key, Exception e) {
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("Object does not exist") || msg.contains("NoSuchKey")
                || msg.contains("404") || msg.contains("does not exist")) {
            return new BusinessException(ErrorCode.NOT_FOUND);
        }
        log.error("MinIO 读取失败: key={}", key, e);
        return new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储读取失败");
    }
}
