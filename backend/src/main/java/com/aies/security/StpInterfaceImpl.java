package com.aies.security;

import cn.dev33.satoken.stp.StpInterface;
import com.aies.entity.User;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色/权限数据源（五表RBAC标准版）。
 *
 * <p>每次鉴权时 Sa-Token 会回调这里获取当前用户的角色与权限列表。
 * V1 起数据来自 sys_user_role / sys_role_permission / sys_permission 查询
 * （权限点种子与 02-04 RTM 一一对应），角色由管理员经 UC-31/32 管理，
 * 权限变更经 Redis 广播即时生效——不再使用代码内硬编码映射。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = toEnabledUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        return rbacMapper.selectPermCodes(userId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = toEnabledUserId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        return rbacMapper.selectRoleCodes(userId);
    }

    /** 登录id → 校验存在且启用的用户id；账号禁用/不存在返回 null（等同无权限） */
    private Long toEnabledUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        long userId;
        try {
            userId = Long.parseLong(loginId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return userId;
    }
}
