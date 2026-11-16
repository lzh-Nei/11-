package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级（03-数据库设计 §3.1；M3 管理端班级管理载体）。
 *
 * <p>class_code 全局唯一，注册时作为班级码校验（A-08）；teacher_id 为班主任/授课教师。</p>
 */
@Data
@TableName("class")
public class SysClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级名称 */
    private String className;

    /** 班级码（4-16位字母/数字/短横线，唯一） */
    private String classCode;

    /** 授课教师（sys_user.id，须具备 TEACHER 角色） */
    private Long teacherId;

    private LocalDateTime createTime;
}
