package com.aies.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * R-02 病毒扫描钩子（UC-16 基本流第3.5步预留接口）。
 *
 * <p>W7 接入真实扫描服务（ClamAV 容器/云查杀）时以实现类替换注入；
 * 扫描不通过时 SubmissionService 拒绝落盘（→30001 同径拒绝，留 R-02 预案决策）。
 * 接口层不做假实现——当前唯一实现为直通（NoopVirusScanHook）。</p>
 */
public interface VirusScanHook {

    /** true=允许落盘；false=查杀命中，拒绝该文件 */
    boolean clean(MultipartFile file);
}
