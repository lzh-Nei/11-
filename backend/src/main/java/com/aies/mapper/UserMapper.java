package com.aies.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aies.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问。
 *
 * <p>基础 CRUD 由 MyBatis-Plus 的 BaseMapper 提供，
 * 下面两个方法是登录/注册/重置密码用到的查询与更新。</p>
 */
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    User selectByUsername(@Param("username") String username);

    @Update("UPDATE sys_user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
