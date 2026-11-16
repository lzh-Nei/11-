package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.admin.AdminDtos.AdminTeacherVO;
import com.aies.dto.admin.AdminDtos.AdminUserVO;
import com.aies.entity.SysClass;
import com.aies.entity.SysRole;
import com.aies.entity.SysUserRole;
import com.aies.entity.User;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.SysRoleMapper;
import com.aies.mapper.SysUserRoleMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.JwtService;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M3 管理端用户模块（UC-06/07/09）：用户检索、禁用/启用、角色分配、教师下拉。
 *
 * <p>保护口径：不得修改当前登录账号；ADMIN 角色账号的角色/状态不可在此变更（防锁死）。
 * 禁用同时作废该用户全部 RT 并清理权限缓存；AT 按 S-10 同口径自然过期（最长2h）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String AUTH_PERM_PREFIX = "auth:perm:";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private final UserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysClassMapper classMapper;
    private final RbacMapper rbacMapper;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final AdminAuditService auditService;

    /** 用户分页：keyword 匹配账号/姓名，status 与 roleCode 可组合筛选 */
    public Page<AdminUserVO> list(long page, long size, String keyword,
                                  Integer status, String roleCode) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getRealName, kw));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(roleCode)) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, roleCode.trim()));
            if (role == null) {
                return emptyPage(page, size);
            }
            List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()))
                    .stream().map(SysUserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                return emptyPage(page, size);
            }
            wrapper.in(User::getId, userIds);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> result = userMapper.selectPage(Page.of(page, size), wrapper);
        Map<Long, String> classNames = classNames(result.getRecords());
        List<AdminUserVO> records = result.getRecords().stream()
                .map(user -> toVO(user, classNames))
                .toList();
        Page<AdminUserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /** 启用/禁用（禁用→40103 登录拦截；RT 立即作废） */
    @Transactional
    public void updateStatus(long userId, int status, LoginPrincipal principal) {
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态取值非法");
        }
        if (principal.userId() == userId) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能修改当前登录账号的状态");
        }
        User user = requireUser(userId);
        if (hasRole(userId, ROLE_ADMIN)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员账号状态受保护，请先调整角色");
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId)
                .set("status", status));
        redis.delete(AUTH_PERM_PREFIX + userId);
        if (status == 0) {
            jwtService.revokeAllRefreshTokens(userId);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("status", status);
        auditService.record(principal, "user", status == 1 ? "ENABLE_USER" : "DISABLE_USER",
                "user:" + userId, detail);
    }

    /** 角色分配（单角色/多角色均按传入集合重建；权限缓存即时失效） */
    @Transactional
    public void updateRoles(long userId, List<String> roleCodes, LoginPrincipal principal) {
        if (principal.userId() == userId) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能修改当前登录账号的角色");
        }
        User user = requireUser(userId);
        if (hasRole(userId, ROLE_ADMIN)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "管理员账号角色受保护（防锁死）");
        }
        List<String> codes = roleCodes.stream().filter(StringUtils::hasText)
                .map(String::trim).distinct().toList();
        if (codes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少保留一个角色");
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getRoleCode, codes));
        Set<String> existing = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        List<String> missing = codes.stream().filter(code -> !existing.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在：" + String.join(",", missing));
        }
        List<String> before = rbacMapper.selectRoleCodes(userId);

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (String code : codes) {
            rbacMapper.insertUserRole(userId, code);
        }
        redis.delete(AUTH_PERM_PREFIX + userId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("before", before);
        detail.put("after", codes);
        auditService.record(principal, "user", "ASSIGN_ROLES", "user:" + userId, detail);
    }

    /** 教师下拉（班级管理绑定授课教师） */
    public List<AdminTeacherVO> teachers() {
        SysRole teacherRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, ROLE_TEACHER));
        if (teacherRole == null) {
            return List.of();
        }
        List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, teacherRole.getId()))
                .stream().map(SysUserRole::getUserId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .sorted(Comparator.comparing(User::getRealName, Comparator.nullsLast(String::compareTo)))
                .map(user -> new AdminTeacherVO(user.getId(), user.getUsername(), user.getRealName()))
                .toList();
    }

    private User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private boolean hasRole(long userId, String roleCode) {
        return rbacMapper.selectRoleCodes(userId).contains(roleCode);
    }

    private AdminUserVO toVO(User user, Map<Long, String> classNames) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(),
                user.getEmail(), user.getClassId(),
                user.getClassId() == null ? null : classNames.get(user.getClassId()),
                user.getStatus(), user.getFirstLogin(),
                rbacMapper.selectRoleCodes(user.getId()), user.getCreateTime());
    }

    private Map<Long, String> classNames(List<User> users) {
        List<Long> classIds = users.stream().map(User::getClassId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(SysClass::getId, SysClass::getClassName,
                        (left, right) -> left));
    }

    private Page<AdminUserVO> emptyPage(long page, long size) {
        Page<AdminUserVO> empty = new Page<>(page, size, 0);
        empty.setRecords(new ArrayList<>());
        return empty;
    }
}
