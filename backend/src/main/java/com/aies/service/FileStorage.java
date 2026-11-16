package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;

import java.io.InputStream;

/**
 * 对象存储统一契约（D-23 桶化抽象，W7-C1）。
 *
 * <p>key 语义：本地实现与 MinIO 实现同形——UUID32（提交原件）或
 * {@code golden/xxx.docx}（黄金集样本）、{@code parsed/{uuid32}.txt}（解析文本），
 * 调用方生成 key，存储层只管落桶/取流。切换实现零迁移（W6-C2 裁决口径）。</p>
 *
 * <p>装配：mode=minio → {@link MinioFileStorage}（dev/生产）；
 * mode=local → {@link LocalFileStorage}（无容器兜底/单测）。</p>
 */
public interface FileStorage {

    /** key 白名单：字母数字开头，仅 / . _ - 分隔符，禁 ".." 防穿越（S-07 用户输入禁入 key） */
    String KEY_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9/._-]{0,127}$";

    /**
     * 落桶（新写或覆盖）。key 由调用方生成；返回入参 key（便于链式落库）。
     */
    String store(String key, InputStream in, long size);

    /**
     * 全量读取流（调用方负责关闭）。对象不存在→40400 同形。
     */
    InputStream openStream(String key);

    /**
     * 对象字节大小（statObject）。对象不存在→40400 同形。
     */
    long size(String key);

    /**
     * GET 预签名 URL（S-03/S-04：5 分钟有效，私有桶下载）。
     */
    String presignedGetUrl(String key, int expirySeconds);

    /**
     * 落冷桶（W7-C2评价域留痕 trace/{traceId}.json，04-05 §六；ADR-001冷热双桶的冷侧承载）。
     * minio→swe-cold；local→files/cold/ 子目录（key 同形）。
     */
    String storeCold(String key, InputStream in, long size);

    /** key 合法性统一校验（两个实现共用，防路径穿越/非法字符） */
    default void validateKey(String key) {
        if (key == null || !key.matches(KEY_PATTERN) || key.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "非法对象key");
        }
    }
}
