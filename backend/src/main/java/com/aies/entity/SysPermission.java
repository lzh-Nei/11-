package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点（03-数据库设计 §3.2 五表RBAC标准）。
 *
 * <p>perm_code 与 02-04 RTM 一一对应（04-安全编码规约 S-06），接口级鉴权最小单元。</p>
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限点编码，如 submission:upload */
    private String permCode;

    /** 权限点名称 */
    private String permName;

    /** 所属模块：auth/user/task/submission/evaluation/report/system */
    private String module;

    private LocalDateTime createTime;
}
