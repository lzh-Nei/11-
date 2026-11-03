package com.group11.aievaluate.dto;

import com.group11.aievaluate.entity.User;

/**
 * 对外返回的用户信息，刻意不包含 password 字段。
 */
public record UserVO(Long id, String username, String phone, String nickname, String avatar, String role) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getPhone(),
                user.getNickname(), user.getAvatar(), user.getRole());
    }
}
