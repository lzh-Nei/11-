package com.aies.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 病毒扫描直通实现（W7 前默认装配；R-02 病毒扫描预案未启用）。
 */
@Component
public class NoopVirusScanHook implements VirusScanHook {

    @Override
    public boolean clean(MultipartFile file) {
        return true;
    }
}
