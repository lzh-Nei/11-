package com.aies.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置（D-23 桶化，W7-C1）。
 *
 * <p>mode=minio 时装配 {@code MinioFileStorage}（swe-hot 真实落桶，M1 硬边界）；
 * mode=local（默认）时装配 {@code LocalFileStorage}（本地 files/ 目录兜底，
 * 供无容器场景与单测使用）。凭据一律环境变量注入（红线4）。</p>
 */
@ConfigurationProperties(prefix = "filestorage")
public class FileStorageProperties {

    /** 存储模式：minio / local（默认 local，compose 注入 minio） */
    private String mode = "local";

    /** MinIO endpoint（如 http://minio:9000，容器内网络） */
    private String endpoint = "http://localhost:9000";

    /** MinIO 访问账号（.env 注入，与 minio-init 同源） */
    private String accessKey = "minioadmin";

    /** MinIO 访问密钥（.env 注入，零明文入仓） */
    private String secretKey = "";

    /** 热桶（03-02 §四：提交原件+解析文本+黄金集样本） */
    private String bucketHot = "swe-hot";

    /** 冷桶（留痕导出，W8 评价域接入） */
    private String bucketCold = "swe-cold";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketHot() {
        return bucketHot;
    }

    public void setBucketHot(String bucketHot) {
        this.bucketHot = bucketHot;
    }

    public String getBucketCold() {
        return bucketCold;
    }

    public void setBucketCold(String bucketCold) {
        this.bucketCold = bucketCold;
    }
}
