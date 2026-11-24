package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.dto.chat.ChatDtos.ContactVO;
import com.aies.dto.chat.ChatDtos.MessageVO;
import com.aies.dto.chat.ChatDtos.UnreadRow;
import com.aies.entity.ChatMessage;
import com.aies.entity.SysClass;
import com.aies.entity.User;
import com.aies.mapper.ChatMessageMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.UserMapper;
import com.aies.realtime.RealtimeWebSocketHandler;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 单测（V20）：可达性、历史正序、发送落库+双端推送、已读。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SysClassMapper classMapper;
    @Mock
    private RealtimeWebSocketHandler webSocketHandler;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private ChatService chatService;

    private final LoginPrincipal studentPrincipal =
            new LoginPrincipal(3L, "jti", "raw", "STUDENT", Long.MAX_VALUE);
    private User student;
    private User teacher;
    private SysClass clazz;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(3L);
        student.setRealName("张三");
        student.setClassId(10L);
        student.setStatus(1);

        teacher = new User();
        teacher.setId(2L);
        teacher.setRealName("李老师");
        teacher.setStatus(1);

        clazz = new SysClass();
        clazz.setId(10L);
        clazz.setTeacherId(2L);
    }

    @Test
    void contactsShouldReturnTeacherWithLastMessageAndUnread() {
        when(userMapper.selectById(3L)).thenReturn(student);
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(classMapper.selectById(10L)).thenReturn(clazz);
        ChatMessage last = message(9L, 2L, 3L, "作业已批改", 0, LocalDateTime.now());
        when(chatMessageMapper.selectLatestPerPeer(3L)).thenReturn(List.of(last));
        UnreadRow unread = new UnreadRow();
        unread.setSenderId(2L);
        unread.setUnread(1L);
        when(chatMessageMapper.selectUnreadBySender(3L)).thenReturn(List.of(unread));
        when(webSocketHandler.isOnline(2L)).thenReturn(true);

        List<ContactVO> contacts = chatService.contacts(studentPrincipal);

        assertThat(contacts).hasSize(1);
        ContactVO contact = contacts.get(0);
        assertThat(contact.userId()).isEqualTo(2L);
        assertThat(contact.role()).isEqualTo("TEACHER");
        assertThat(contact.online()).isTrue();
        assertThat(contact.unread()).isEqualTo(1L);
        assertThat(contact.lastContent()).isEqualTo("作业已批改");
    }

    @Test
    void messagesShouldReturnChronologicalOrder() {
        when(userMapper.selectById(3L)).thenReturn(student);
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(classMapper.selectById(10L)).thenReturn(clazz);
        ChatMessage newer = message(12L, 2L, 3L, "第二条", 0, LocalDateTime.now());
        ChatMessage older = message(11L, 3L, 2L, "第一条", 0, LocalDateTime.now());
        when(chatMessageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(newer, older));

        List<MessageVO> messages = chatService.messages(studentPrincipal, 2L, null, 30);

        assertThat(messages).extracting(MessageVO::id).containsExactly(11L, 12L);
        assertThat(messages).extracting(MessageVO::content).containsExactly("第一条", "第二条");
    }

    @Test
    void sendShouldPersistAndPushToBothSides() {
        when(userMapper.selectById(3L)).thenReturn(student);
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(classMapper.selectById(10L)).thenReturn(clazz);
        doAnswer(invocation -> {
            ChatMessage inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        MessageVO sent = chatService.send(studentPrincipal, 2L, "  老师您好  ");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("老师您好");
        assertThat(captor.getValue().getReadFlag()).isZero();
        assertThat(sent.id()).isEqualTo(100L);
        verify(webSocketHandler).sendToUser(eq(2L), eq("chat.message"), any(MessageVO.class));
        verify(webSocketHandler).sendToUser(eq(3L), eq("chat.message"), any(MessageVO.class));
    }

    @Test
    void sendToDisallowedPeerShouldBeForbidden() {
        when(userMapper.selectById(3L)).thenReturn(student);
        when(classMapper.selectById(10L)).thenReturn(clazz);
        when(userMapper.selectById(2L)).thenReturn(teacher);

        assertThatThrownBy(() -> chatService.send(studentPrincipal, 99L, "你好"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(40301);
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
        verify(webSocketHandler, never()).sendToUser(anyLong(), any(), any());
    }

    @Test
    void markReadShouldReturnUpdatedCount() {
        when(userMapper.selectById(3L)).thenReturn(student);
        when(userMapper.selectById(2L)).thenReturn(teacher);
        when(classMapper.selectById(10L)).thenReturn(clazz);
        when(chatMessageMapper.markRead(3L, 2L)).thenReturn(2);

        int updated = chatService.markRead(studentPrincipal, 2L);

        assertThat(updated).isEqualTo(2);
    }

    private ChatMessage message(long id, long senderId, long receiverId, String content,
                                int readFlag, LocalDateTime createTime) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setReadFlag(readFlag);
        message.setCreateTime(createTime);
        return message;
    }
}
