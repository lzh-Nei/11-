package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.entity.SysConfig;
import com.aies.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期可调参数（sys_config）：仅白名单键可写，读取失败回退默认值。
 *
 * <p>白名单键同时是“真实生效”的参数：上传上限与报表缓存 TTL 由业务侧读取，
 * 不存在只写不读的装饰性配置。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    public static final String KEY_UPLOAD_MAX_FILE_MB = "upload.maxFileMb";
    public static final String KEY_REPORT_CACHE_TTL_MINUTES = "report.cacheTtlMinutes";

    private static final Map<String, int[]> RANGES = Map.of(
            KEY_UPLOAD_MAX_FILE_MB, new int[]{1, 20},
            KEY_REPORT_CACHE_TTL_MINUTES, new int[]{1, 60});

    private static final Map<String, Integer> DEFAULTS = Map.of(
            KEY_UPLOAD_MAX_FILE_MB, 20,
            KEY_REPORT_CACHE_TTL_MINUTES, 10);

    private final SysConfigMapper sysConfigMapper;

    /** 读取整型配置；缺失/非法时回退默认值（读取路径不抛异常） */
    public int getInt(String key, int defaultValue) {
        SysConfig config = sysConfigMapper.selectById(key);
        if (config == null || config.getConfigValue() == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("sys_config 值非整数，回退默认值: key={} value={}", key, config.getConfigValue());
            return defaultValue;
        }
    }

    /** 管理端可编辑配置快照（白名单键 → 当前值） */
    public Map<String, String> editable() {
        Map<String, String> values = new LinkedHashMap<>();
        List<SysConfig> rows = sysConfigMapper.selectBatchIds(RANGES.keySet());
        Map<String, String> stored = new LinkedHashMap<>();
        rows.forEach(row -> stored.put(row.getConfigKey(), row.getConfigValue()));
        RANGES.keySet().forEach(key -> values.put(key,
                stored.getOrDefault(key, String.valueOf(DEFAULTS.get(key)))));
        return values;
    }

    /** 白名单键校验 + 整数范围校验后落库（调用方负责审计与缓存刷新） */
    public void apply(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "没有可更新的配置项");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            int[] range = RANGES.get(key);
            if (range == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的配置项：" + key);
            }
            int value = parseInRange(key, entry.getValue(), range[0], range[1]);
            upsert(key, value);
        }
    }

    private int parseInRange(String key, String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置值不能为空：" + key);
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置值必须为整数：" + key);
        }
        if (value < min || value > max) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    key + " 取值范围为 " + min + "-" + max);
        }
        return value;
    }

    private void upsert(String key, int value) {
        SysConfig existing = sysConfigMapper.selectById(key);
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(String.valueOf(value));
        config.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            sysConfigMapper.insert(config);
        } else {
            sysConfigMapper.updateById(config);
        }
    }
}
