package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.dto.UpdateProfileRequest;
import com.aies.entity.User;
import com.aies.mapper.AuditLogMapper;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人资料自助更新单测：落库/返回新VO/审计、姓名空值、手机号非法、手机号清空、用户不存在。
 */
class ProfileServiceTest {

    private UserMapper userMapper;
    private RbacMapper rbacMapper;
    private AuditLogMapper auditLogMapper;
    private ProfileService profileService;

    private final LoginPrincipal student = new LoginPrincipal(3L, "jti", "raw", "STUDENT", Long.MAX_VALUE);

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        rbacMapper = mock(RbacMapper.class);
        auditLogMapper = mock(AuditLogMapper.class);
        profileService = new ProfileService(userMapper, rbacMapper, auditLogMapper, new ObjectMapper());
    }

    private User user(String realName, String phone) {
        User user = new User();
        user.setId(3L);
        user.setUsername("student");
        user.setRealName(realName);
        user.setPhone(phone);
        user.setStatus(1);
        return user;
    }

    private UpdateProfileRequest request(String realName, String phone) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setRealName(realName);
        request.setPhone(phone);
        return request;
    }

    @Test
    void updateProfileShouldPersistAndReturnFreshVo() {
        when(userMapper.selectById(3L))
                .thenReturn(user("示例学生", "13800000002"), user("新姓名", "13900000003"));
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));
        when(rbacMapper.selectPermCodes(3L)).thenReturn(List.of("user:me", "task:view"));

        var vo = profileService.updateProfile(request(" 新姓名 ", "13900000003"), student);

        assertThat(vo.realName()).isEqualTo("新姓名");
        assertThat(vo.phone()).isEqualTo("13900000003");
        assertThat(vo.permissions()).contains("user:me");
        verify(userMapper).update(any(), any());
        verify(auditLogMapper).insert(any());
    }

    @Test
    void blankRealNameShouldReject() {
        when(userMapper.selectById(3L)).thenReturn(user("示例学生", null));

        assertThatThrownBy(() -> profileService.updateProfile(request("   ", null), student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名不能为空");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void invalidPhoneShouldReject() {
        when(userMapper.selectById(3L)).thenReturn(user("示例学生", null));

        assertThatThrownBy(() -> profileService.updateProfile(request("示例学生", "12345"), student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号格式不正确");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void clearPhoneShouldAllowEmptyString() {
        when(userMapper.selectById(3L))
                .thenReturn(user("示例学生", "13800000002"), user("示例学生", null));
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));
        when(rbacMapper.selectPermCodes(3L)).thenReturn(List.of("user:me"));

        var vo = profileService.updateProfile(request("示例学生", ""), student);

        assertThat(vo.phone()).isNull();
        verify(userMapper).update(any(), any());
        verify(auditLogMapper).insert(any());
    }

    @Test
    void missingUserShouldReject() {
        when(userMapper.selectById(3L)).thenReturn(null);

        assertThatThrownBy(() -> profileService.updateProfile(request("示例学生", null), student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }
}
