package com.aies.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 病毒扫描直通实现：未启用 ClamAV 时的默认装配。
 *
 * <p>启用 {@code app.upload.clamav.enabled=true} 后由 {@link ClamAvVirusScanHook} 接管。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.upload.clamav", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoopVirusScanHook implements VirusScanHook {

    @PostConstruct
    void warnDisabled() {
        log.warn("病毒扫描未启用（NoopVirusScanHook 直通）；生产环境请配置 app.upload.clamav.enabled=true");
    }

    @Override
    public boolean clean(MultipartFile file) {
        return true;
    }
}
