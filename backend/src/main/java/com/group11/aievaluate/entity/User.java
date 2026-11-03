package com.group11.aievaluate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户。
 *
 * <p>对应表 sys_user；password 字段保存的是 BCrypt 密文，不是明文。</p>
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，唯一 */
    private String username;

    /** BCrypt 密码密文，长度 60 */
    private String password;

    /** 手机号，唯一，用于短信登录/找回密码 */
    private String phone;

    private String nickname;

    private String avatar;

    /** 角色：admin / teacher / student */
    private String role;

    /** 1 正常，0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
