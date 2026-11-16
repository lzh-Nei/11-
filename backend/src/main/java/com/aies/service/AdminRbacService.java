package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.admin.AdminDtos.AdminPermissionVO;
import com.aies.dto.admin.AdminDtos.AdminRoleVO;
import com.aies.entity.SysPermission;
import com.aies.entity.SysRole;
import com.aies.entity.SysRolePermission;
import com.aies.entity.SysUserRole;
import com.aies.mapper.SysPermissionMapper;
import com.aies.mapper.SysRoleMapper;
import com.aies.mapper.SysRolePermissionMapper;
import com.aies.mapper.SysUserRoleMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M3 管理端角色权限模块（UC-31/32）：角色矩阵读取与维护。
 *
 * <p>ADMIN 内置角色权限只读（防锁死）；STUDENT/TEACHER 可调整。
 * 变更后删除该角色全部用户的 auth:perm 缓存，实现 UC-32 即时生效（30min TTL 兜底）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminRbacService {

    private static final String AUTH_PERM_PREFIX = "auth:perm:";
    private static final String ROLE_ADMIN = "ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final StringRedisTemplate redis;
    private final AdminAuditService auditService;

    /** 角色列表（含权限矩阵与用户数） */
    public List<AdminRoleVO> roles() {
        List<SysPermission> permissions = permissionMapper.selectList(null);
        Map<Long, String> codeById = permissions.stream()
                .collect(Collectors.toMap(SysPermission::getId, SysPermission::getPermCode));
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId)).stream()
                .map(role -> {
                    List<String> permCodes = permIdsOf(role.getId()).stream()
                            .map(codeById::get).filter(java.util.Objects::nonNull).sorted().toList();
                    long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()));
                    return new AdminRoleVO(role.getId(), role.getRoleCode(), role.getRoleName(),
                            role.getBuiltIn(), userCount, permCodes);
                })
                .toList();
    }

    /** 全量权限点（按模块排序，前端矩阵左列） */
    public List<AdminPermissionVO> permissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getModule).orderByAsc(SysPermission::getId))
                .stream()
                .map(p -> new AdminPermissionVO(p.getId(), p.getPermCode(), p.getPermName(), p.getModule()))
                .toList();
    }

    /** 更新角色权限矩阵（ADMIN 拒改；未知权限码→30000；变更后即时失效该角色用户权限缓存） */
    @Transactional
    public void updateRolePermissions(long roleId, List<String> permCodes, LoginPrincipal principal) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (ROLE_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内置管理员角色权限受保护（防锁死）");
        }
        List<String> codes = permCodes.stream().filter(StringUtils::hasText)
                .map(String::trim).distinct().toList();
        List<SysPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getPermCode, codes));
        Set<String> found = permissions.stream().map(SysPermission::getPermCode).collect(Collectors.toSet());
        List<String> missing = codes.stream().filter(code -> !found.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "权限点不存在：" + String.join(",", missing));
        }
        List<String> before = permIdsOf(roleId).stream()
                .map(id -> permissionMapper.selectById(id))
                .filter(java.util.Objects::nonNull)
                .map(SysPermission::getPermCode)
                .sorted().toList();

        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (SysPermission permission : permissions) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermId(permission.getId());
            rolePermMapper.insert(relation);
        }
        evictRoleUserPermissions(roleId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roleCode", role.getRoleCode());
        detail.put("before", before);
        detail.put("after", codes.stream().sorted().toList());
        auditService.record(principal, "system", "UPDATE_ROLE_PERMISSIONS",
                "role:" + role.getRoleCode(), detail);
    }

    private List<Long> permIdsOf(long roleId) {
        return rolePermMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermId).toList();
    }

    private void evictRoleUserPermissions(long roleId) {
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).toList();
        if (!userIds.isEmpty()) {
            redis.delete(userIds.stream().map(id -> AUTH_PERM_PREFIX + id).toList());
        }
    }
}
