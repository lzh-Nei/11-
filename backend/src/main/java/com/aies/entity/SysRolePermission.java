package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联（03-数据库设计 §3.2 五表RBAC标准）。
 *
 * <p>(role_id, perm_id) 联合唯一；变更后需发 Redis 广播刷新权限缓存（UC-32即时生效）。</p>
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permId;
}
