package com.aies.mapper;

import com.aies.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站内通知 Mapper：未读计数与全部已读走直写 SQL，列表复用条件构造。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_flag = 0")
    long countUnread(@Param("userId") long userId);

    @Update("""
            UPDATE notification SET read_flag = 1
            WHERE user_id = #{userId} AND read_flag = 0
            """)
    int markAllRead(@Param("userId") long userId);
}
