package com.aies.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aies.entity.User;
import com.aies.model.ClassStudentCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户数据访问。
 *
 * <p>基础 CRUD 由 MyBatis-Plus 的 BaseMapper 提供，
 * 下面两个方法是登录/注册/重置密码用到的查询与更新。</p>
 */
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    User selectByUsername(@Param("username") String username);

    @Update("UPDATE sys_user SET password = #{password}, first_login = 0, update_time = NOW() "
            + "WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /** 报表提交率分母：按班级统计启用状态的学生数 */
    @Select("<script>"
            + "SELECT u.class_id AS classId, COUNT(*) AS studentCount FROM sys_user u "
            + "JOIN sys_user_role ur ON ur.user_id = u.id "
            + "JOIN sys_role r ON r.id = ur.role_id "
            + "WHERE r.role_code = 'STUDENT' AND u.status = 1 AND u.class_id IN "
            + "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY u.class_id"
            + "</script>")
    List<ClassStudentCount> countStudentsByClassIds(@Param("classIds") List<Long> classIds);
}
