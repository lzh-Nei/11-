package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行期可调参数（V3 新增 sys_config）。
 *
 * <p>仅管理端白名单键可写，值以字符串存储，应用侧按类型/范围校验。</p>
 */
@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;

    private String configValue;

    private String description;

    private LocalDateTime updateTime;
}
