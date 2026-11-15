package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色（03-数据库设计 §3.2 五表RBAC标准）。
 *
 * <p>内置三角色 STUDENT/TEACHER/ADMIN 的 built_in=1，不可删除（UC-31负例）。</p>
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：STUDENT / TEACHER / ADMIN */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 1 内置角色不可删 */
    private Integer builtIn;

    private LocalDateTime createTime;
}
