package com.aies.dto;

import com.aies.entity.User;

import java.util.List;

/**
 * 对外返回的用户信息，刻意不包含 password 字段。
 *
 * <p>五表RBAC标准：角色来自 sys_user_role 关联查询，不再落用户表字段。</p>
 *
 * <p>W6-C2 契约细化（D-21 同径，加法字段）：/me 增补 permissions（前端
 * userStore 装载角色+权限清单用，UC-13 前端路由守卫/菜单裁剪数据源）。</p>
 */
public record UserVO(Long id, String username, String realName, String phone,
                     String email, List<String> roles, List<String> permissions) {

    public static UserVO from(User user, List<String> roles, List<String> permissions) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(),
                user.getPhone(), user.getEmail(), roles, permissions);
    }
}
