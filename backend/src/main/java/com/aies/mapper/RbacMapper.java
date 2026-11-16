package com.aies.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 五表RBAC数据访问（03-数据库设计 §3.2）。
 *
 * <p>角色/权限查询供 JWT 过滤器鉴权装载（{@code JwtAuthenticationFilter}/{@code JwtService}）使用；
 * 权限集 Redis 缓存（TTL 30min + 变更广播失效）按 03-02 §五 在 UC-32 落地时接入。</p>
 */
public interface RbacMapper {

    @Select("SELECT r.role_code FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p "
            + "JOIN sys_role_permission rp ON rp.perm_id = p.id "
            + "JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectPermCodes(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) "
            + "SELECT #{userId}, id FROM sys_role WHERE role_code = #{roleCode}")
    int insertUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    /** 可受理申诉的教师（启用状态，排除原评教师/班主任） */
    @Select("SELECT DISTINCT u.id FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE r.role_code = 'TEACHER' AND u.status = 1 "
            + "AND (#{excludeId} IS NULL OR u.id <> #{excludeId})")
    List<Long> selectAvailableTeacherIds(@Param("excludeId") Long excludeId);
}
