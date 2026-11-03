package com.group11.aievaluate.security;

import cn.dev33.satoken.stp.StpInterface;
import com.group11.aievaluate.entity.User;
import com.group11.aievaluate.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色/权限数据源。
 *
 * <p>每次鉴权时 Sa-Token 会回调这里获取当前用户的角色与权限列表。
 * 当前课程项目采用“用户表 role 字段 + 代码内角色权限映射”的简化 RBAC；
 * 后续如果要做完整的角色-权限表，只需把下面两个方法的返回值改成查表即可，
 * Controller 上的注解无需修改。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = findEnabledUser(loginId);
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }
        return switch (user.getRole()) {
            // “*” 是 Sa-Token 的权限通配符，拥有所有权限
            case "admin" -> List.of("*");
            case "teacher" -> List.of("file:upload", "file:manage");
            case "student" -> List.of("file:upload");
            default -> Collections.emptyList();
        };
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = findEnabledUser(loginId);
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }
        return List.of(user.getRole());
    }

    private User findEnabledUser(Object loginId) {
        if (loginId == null) {
            return null;
        }
        User user = userMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }
}
