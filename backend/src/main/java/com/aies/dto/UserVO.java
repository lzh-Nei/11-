package com.aies.dto;

import com.aies.entity.User;

import java.util.List;

/**
 * 对外返回的用户信息，刻意不包含 password 字段。
 *
 * <p>五表RBAC标准：角色来自 sys_user_role 关联查询，不再落用户表字段。</p>
 */
public record UserVO(Long id, String username, String realName, String phone,
                     String email, List<String> roles) {

    public static UserVO from(User user, List<String> roles) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(),
                user.getPhone(), user.getEmail(), roles);
    }
}
