package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户（03-数据库设计 §3.1 五表RBAC标准）。
 *
 * <p>对应表 sys_user；password 字段保存的是 BCrypt 密文，不是明文。
 * 角色不再落在用户表上，经 sys_user_role 关联 sys_role（见 {@link SysRole}）。</p>
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（学号/工号），唯一 */
    private String username;

    /** BCrypt 密码密文，长度 60 */
    private String password;

    /** 姓名（PII，日志脱敏） */
    private String realName;

    /** 手机号（PII，展示脱敏），短信登录/找回依据 */
    private String phone;

    /** 邮箱，邮箱码登录/找回前置 */
    private String email;

    /** 学生所属班级，外键在 V2 迁移补加 */
    private Long classId;

    /** 1 正常，0 禁用（禁用→40103） */
    private Integer status;

    /** 1 首登强制改密，0 否 */
    private Integer firstLogin;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
