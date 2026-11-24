package com.aies.mapper;

import com.aies.dto.chat.ChatDtos;
import com.aies.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天消息 Mapper：会话历史走 MyBatis-Plus 条件构造，会话摘要与未读数走聚合 SQL。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 每个会话（对端）最后一条消息，用于会话列表预览 */
    @Select("""
            SELECT m.* FROM chat_message m
            JOIN (
                SELECT MAX(id) AS id FROM chat_message
                WHERE sender_id = #{me} OR receiver_id = #{me}
                GROUP BY LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)
            ) latest ON latest.id = m.id
            """)
    List<ChatMessage> selectLatestPerPeer(@Param("me") long me);

    /** 按发送人聚合未读数（仅统计收件人为当前用户且未读） */
    @Select("""
            SELECT sender_id AS senderId, COUNT(*) AS unread
            FROM chat_message
            WHERE receiver_id = #{me} AND read_flag = 0
            GROUP BY sender_id
            """)
    List<ChatDtos.UnreadRow> selectUnreadBySender(@Param("me") long me);

    /** 把 peer 发给 me 的未读消息置为已读，返回更新条数 */
    @Update("""
            UPDATE chat_message SET read_flag = 1
            WHERE receiver_id = #{me} AND sender_id = #{peer} AND read_flag = 0
            """)
    int markRead(@Param("me") long me, @Param("peer") long peer);
}
