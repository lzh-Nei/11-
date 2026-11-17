package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.config.AuthProperties;
import com.aies.dto.admin.AdminDtos.ClassCreateRequest;
import com.aies.dto.admin.AdminDtos.CreatePromptRequest;
import com.aies.dto.admin.AdminDtos.CreateUserRequest;
import com.aies.dto.admin.AdminDtos.UpdateLlmConfigRequest;
import com.aies.dto.admin.AdminDtos.UpdateUserRequest;
import com.aies.dto.admin.AdminDtos.UserDeletionImpactVO;
import com.aies.entity.AuditLog;
import com.aies.entity.GoldenDataset;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.SysClass;
import com.aies.entity.SysPermission;
import com.aies.entity.SysRole;
import com.aies.entity.SysRolePermission;
import com.aies.entity.SysUserRole;
import com.aies.entity.User;
import com.aies.mapper.AuditLogMapper;
import com.aies.mapper.EvaluationHistoryMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.GoldenDatasetMapper;
import com.aies.mapper.LlmConfigMapper;
import com.aies.mapper.PromptTemplateMapper;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.SysPermissionMapper;
import com.aies.mapper.SysRoleMapper;
import com.aies.mapper.SysRolePermissionMapper;
import com.aies.mapper.SysUserRoleMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.JwtService;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M3 管理端服务单测（WP8-C2）：用户状态/角色、角色权限矩阵、班级、AI配置、系统配置与审计。
 *
 * <p>覆盖保护口径（自改/管理员锁死）、缓存失效（auth:perm）、RT作废、删除前置校验、
 * Prompt占位符与唯一启用版、黄金样本启停、审计回填。</p>
 */
class AdminServiceTest {

    private final LoginPrincipal admin = new LoginPrincipal(1L, "jti", "raw", "ADMIN", Long.MAX_VALUE);
    private final LoginPrincipal teacher = new LoginPrincipal(2L, "jti", "raw", "TEACHER", Long.MAX_VALUE);

    private UserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysClassMapper classMapper;
    private RbacMapper rbacMapper;
    private JwtService jwtService;
    private StringRedisTemplate redis;
    private AdminAuditService auditService;

    private AdminUserService userService;
    private AdminRbacService rbacService;
    private AdminClassService classService;
    private AdminAiService aiService;
    private AdminSystemService systemService;

    private SysPermissionMapper permissionMapper;
    private SysRolePermissionMapper rolePermMapper;
    private TaskMapper taskMapper;
    private LlmConfigMapper llmConfigMapper;
    private PromptTemplateMapper promptMapper;
    private GoldenDatasetMapper goldenMapper;
    private AuditLogMapper auditLogMapper;
    private SubmissionMapper submissionMapper;
    private EvaluationHistoryMapper evaluationHistoryMapper;
    private EvaluationResultMapper evaluationResultMapper;
    private UserCascadeService userCascadeService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        classMapper = mock(SysClassMapper.class);
        rbacMapper = mock(RbacMapper.class);
        jwtService = mock(JwtService.class);
        redis = mock(StringRedisTemplate.class);
        auditService = mock(AdminAuditService.class);
        permissionMapper = mock(SysPermissionMapper.class);
        rolePermMapper = mock(SysRolePermissionMapper.class);
        taskMapper = mock(TaskMapper.class);
        llmConfigMapper = mock(LlmConfigMapper.class);
        promptMapper = mock(PromptTemplateMapper.class);
        goldenMapper = mock(GoldenDatasetMapper.class);
        auditLogMapper = mock(AuditLogMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        evaluationHistoryMapper = mock(EvaluationHistoryMapper.class);
        evaluationResultMapper = mock(EvaluationResultMapper.class);
        userCascadeService = mock(UserCascadeService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        userService = new AdminUserService(userMapper, roleMapper, userRoleMapper, classMapper,
                rbacMapper, jwtService, redis, auditService, auditLogMapper, taskMapper,
                submissionMapper, evaluationHistoryMapper, evaluationResultMapper,
                userCascadeService, passwordEncoder);
        rbacService = new AdminRbacService(roleMapper, permissionMapper, rolePermMapper,
                userRoleMapper, redis, auditService);
        classService = new AdminClassService(classMapper, userMapper, taskMapper, rbacMapper, auditService);
        aiService = new AdminAiService(llmConfigMapper, promptMapper, goldenMapper,
                new ObjectMapper(), auditService, mock(ApiKeyCipher.class), mock(LlmClient.class));
        systemService = new AdminSystemService(new AuthProperties(), userMapper, classMapper, taskMapper,
                submissionMapper, evaluationResultMapper, llmConfigMapper,
                promptMapper, goldenMapper, auditLogMapper);
    }

    private User user(long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRealName("测试" + id);
        user.setStatus(1);
        user.setFirstLogin(0);
        user.setClassId(1L);
        return user;
    }

    private SysRole role(long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setBuiltIn(1);
        return role;
    }

    /* ==================== 用户管理 ==================== */

    @Test
    void updateStatusSelfShouldReject() {
        assertThatThrownBy(() -> userService.updateStatus(1L, 0, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录账号");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void updateStatusAdminTargetShouldReject() {
        when(userMapper.selectById(2L)).thenReturn(user(2L, "admin2", "ADMIN"));
        when(rbacMapper.selectRoleCodes(2L)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> userService.updateStatus(2L, 0, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void updateStatusDisableShouldEvictCacheAndRevokeRefreshTokens() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "student", "STUDENT"));
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));

        userService.updateStatus(3L, 0, admin);

        verify(userMapper).update(any(), any());
        verify(redis).delete("auth:perm:3");
        verify(jwtService).revokeAllRefreshTokens(3L);
        verify(auditService).record(eq(admin), eq("user"), eq("DISABLE_USER"), eq("user:3"), any());
    }

    @Test
    void updateRolesSelfShouldReject() {
        assertThatThrownBy(() -> userService.updateRoles(1L, List.of("TEACHER"), admin))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateRolesUnknownRoleShouldReject() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "student", "STUDENT"));
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "STUDENT")));

        assertThatThrownBy(() -> userService.updateRoles(3L, List.of("TEACHER"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
        verify(userRoleMapper, never()).delete(any());
    }

    @Test
    void updateRolesSuccessShouldRebuildAndEvictCache() {
        when(userMapper.selectById(3L)).thenReturn(user(3L, "student", "STUDENT"));
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(2L, "TEACHER")));

        userService.updateRoles(3L, List.of("TEACHER"), admin);

        verify(userRoleMapper).delete(any());
        verify(rbacMapper).insertUserRole(3L, "TEACHER");
        verify(redis).delete("auth:perm:3");
        verify(auditService).record(eq(admin), eq("user"), eq("ASSIGN_ROLES"), eq("user:3"), any());
    }

    @Test
    void listShouldMapRolesAndClassName() {
        Page<User> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(user(3L, "student", "STUDENT")));
        when(userMapper.selectPage(any(), any())).thenReturn(page);
        when(rbacMapper.selectRoleCodes(3L)).thenReturn(List.of("STUDENT"));
        SysClass clazz = new SysClass();
        clazz.setId(1L);
        clazz.setClassName("软件11班");
        when(classMapper.selectBatchIds(anyList())).thenReturn(List.of(clazz));

        Page<?> result = userService.list(1, 10, "stu", 1, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void teachersShouldReturnTeacherAccounts() {
        when(roleMapper.selectOne(any())).thenReturn(role(2L, "TEACHER"));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(2L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(2L, "teacher", "TEACHER")));

        var teachers = userService.teachers();

        assertThat(teachers).hasSize(1);
        assertThat(teachers.get(0).username()).isEqualTo("teacher");
    }

    /* ==================== 用户增删改（M3 CRUD） ==================== */

    @Test
    void createUserShouldInsertWithRolesAndAudit() {
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode("Passw0rd1")).thenReturn("bcrypt");
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "STUDENT")));
        doAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        User created = user(20L, "newuser", "STUDENT");
        created.setRealName("新用户");
        when(userMapper.selectById(20L)).thenReturn(created);
        when(rbacMapper.selectRoleCodes(20L)).thenReturn(List.of("STUDENT"));

        var vo = userService.create(createUserRequest(), admin);

        assertThat(vo.username()).isEqualTo("newuser");
        verify(userMapper).insert(any(User.class));
        verify(rbacMapper).insertUserRole(20L, "STUDENT");
        verify(auditService).record(eq(admin), eq("user"), eq("CREATE_USER"), eq("user:20"), any());
    }

    @Test
    void createUserShouldRejectAdminRole() {
        when(userMapper.selectByUsername(any())).thenReturn(null);
        CreateUserRequest request = createUserRequest();
        request.setRoleCodes(List.of("ADMIN"));

        assertThatThrownBy(() -> userService.create(request, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能创建管理员");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void createUserShouldRejectDuplicateUsername() {
        when(userMapper.selectByUsername("newuser")).thenReturn(user(9L, "newuser", "STUDENT"));

        assertThatThrownBy(() -> userService.create(createUserRequest(), admin))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void updateUserShouldUpdateProfileAndAudit() {
        User original = user(5L, "stu01", "STUDENT");
        original.setRealName("旧名");
        original.setEmail("old@aies.local");
        User updated = user(5L, "stu01", "STUDENT");
        updated.setRealName("新名");
        updated.setEmail("new@aies.local");
        when(userMapper.selectById(5L)).thenReturn(original, updated);
        when(rbacMapper.selectRoleCodes(5L)).thenReturn(List.of("STUDENT"));

        var vo = userService.update(5L, updateUserRequest(), admin);

        assertThat(vo.realName()).isEqualTo("新名");
        verify(userMapper).update(any(), any());
        verify(auditService).record(eq(admin), eq("user"), eq("UPDATE_USER"), eq("user:5"), any());
    }

    @Test
    void updateUserShouldRejectAdminTarget() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "admin", "ADMIN"));
        when(rbacMapper.selectRoleCodes(1L)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> userService.update(1L, updateUserRequest(), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号资料");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void deleteUserShouldRejectWhenBusinessDataExists() {
        when(userMapper.selectById(6L)).thenReturn(user(6L, "stu06", "STUDENT"));
        when(rbacMapper.selectRoleCodes(6L)).thenReturn(List.of("STUDENT"));
        when(submissionMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> userService.delete(6L, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteUserShouldRemoveRolesAndUser() {
        when(userMapper.selectById(6L)).thenReturn(user(6L, "stu06", "STUDENT"));
        when(rbacMapper.selectRoleCodes(6L)).thenReturn(List.of("STUDENT"));

        userService.delete(6L, admin);

        verify(userRoleMapper).delete(any());
        verify(userMapper).deleteById(6L);
        verify(redis).delete("auth:perm:6");
        verify(auditService).record(eq(admin), eq("user"), eq("DELETE_USER"), eq("user:6"), any());
    }

    @Test
    void deleteUserShouldRejectSelf() {
        assertThatThrownBy(() -> userService.delete(1L, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录账号");
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    void forceDeleteShouldCascadeRecordsAndAudit() {
        when(userMapper.selectById(6L)).thenReturn(user(6L, "stu06", "STUDENT"));
        when(rbacMapper.selectRoleCodes(6L)).thenReturn(List.of("STUDENT"));
        UserDeletionImpactVO impact = new UserDeletionImpactVO(6L, "stu06", "测试6",
                List.of("STUDENT"), false, "", 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1);
        when(userCascadeService.impact(any(), anyList(), eq(false), eq(""))).thenReturn(impact);

        userService.forceDelete(6L, admin);

        verify(userCascadeService).forceDelete(6L);
        verify(jwtService).revokeAllRefreshTokens(6L);
        verify(auditService).record(eq(admin), eq("user"), eq("FORCE_DELETE_USER"),
                eq("user:6"), any());
    }

    /* ==================== 角色权限 ==================== */

    @Test
    void updateRolePermissionsAdminRoleShouldReject() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, "ADMIN"));

        assertThatThrownBy(() -> rbacService.updateRolePermissions(3L, List.of("task:view"), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员角色权限受保护");
        verify(rolePermMapper, never()).delete(any());
    }

    @Test
    void updateRolePermissionsSuccessShouldRebuildMatrixAndEvictUsers() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "TEACHER"));
        SysPermission perm = new SysPermission();
        perm.setId(7L);
        perm.setPermCode("task:view");
        perm.setModule("task");
        when(permissionMapper.selectList(any())).thenReturn(List.of(perm));
        when(rolePermMapper.selectList(any())).thenReturn(List.of());
        SysUserRole relation = new SysUserRole();
        relation.setUserId(2L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));

        rbacService.updateRolePermissions(2L, List.of("task:view"), admin);

        verify(rolePermMapper).delete(any());
        verify(rolePermMapper).insert(any(SysRolePermission.class));
        verify(redis).delete(anyList());
        verify(auditService).record(eq(admin), eq("system"), eq("UPDATE_ROLE_PERMISSIONS"),
                eq("role:TEACHER"), any());
    }

    @Test
    void rolesShouldIncludeMatrixAndUserCount() {
        when(permissionMapper.selectList(any())).thenReturn(List.of());
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "STUDENT")));
        when(rolePermMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.selectCount(any())).thenReturn(3L);

        var roles = rbacService.roles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).userCount()).isEqualTo(3);
    }

    /* ==================== 班级管理 ==================== */

    @Test
    void createClassDuplicateCodeShouldReject() {
        ClassCreateRequest request = classRequest();
        when(rbacMapper.selectRoleCodes(2L)).thenReturn(List.of("TEACHER"));
        when(classMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> classService.create(request, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级码已存在");
        verify(classMapper, never()).insert(any());
    }

    @Test
    void createClassNonTeacherShouldReject() {
        ClassCreateRequest request = classRequest();
        when(rbacMapper.selectRoleCodes(2L)).thenReturn(List.of("STUDENT"));

        assertThatThrownBy(() -> classService.create(request, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TEACHER");
    }

    @Test
    void createClassSuccessShouldInsertAndAudit() {
        ClassCreateRequest request = classRequest();
        when(rbacMapper.selectRoleCodes(2L)).thenReturn(List.of("TEACHER"));
        when(classMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user(2L, "teacher", "TEACHER")));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(taskMapper.selectCount(any())).thenReturn(0L);

        classService.create(request, admin);

        verify(classMapper).insert(any(SysClass.class));
        verify(auditService).record(eq(admin), eq("system"), eq("CREATE_CLASS"), any(), any());
    }

    @Test
    void deleteClassWithStudentsShouldReject() {
        SysClass clazz = new SysClass();
        clazz.setId(1L);
        clazz.setClassName("软件11班");
        when(classMapper.selectById(1L)).thenReturn(clazz);
        when(userMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> classService.delete(1L, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("名学生");
        verify(classMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteClassEmptyShouldDelete() {
        SysClass clazz = new SysClass();
        clazz.setId(1L);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(taskMapper.selectCount(any())).thenReturn(0L);

        classService.delete(1L, admin);

        verify(classMapper).deleteById(1L);
        verify(auditService).record(eq(admin), eq("system"), eq("DELETE_CLASS"), eq("class:1"), any());
    }

    /* ==================== AI 配置 ==================== */

    @Test
    void createPromptMissingPlaceholdersShouldReject() {
        CreatePromptRequest request = new CreatePromptRequest();
        request.setContent("没有占位符的模板");

        assertThatThrownBy(() -> aiService.createPrompt(request, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("{metrics}");
        verify(promptMapper, never()).insert(any());
    }

    @Test
    void createPromptShouldAppendDisabledVersion() {
        CreatePromptRequest request = new CreatePromptRequest();
        request.setContent("请评估 {metrics} 与 {parsed_text}");
        PromptTemplate latest = new PromptTemplate();
        latest.setId(1L);
        latest.setVersionNo(3);
        when(promptMapper.selectList(any())).thenReturn(List.of(latest));

        var created = aiService.createPrompt(request, admin);

        assertThat(created.versionNo()).isEqualTo(4);
        assertThat(created.enabled()).isZero();
        verify(promptMapper).insert(any(PromptTemplate.class));
        verify(auditService).record(eq(admin), eq("system"), eq("CREATE_PROMPT"), eq("prompt:v4"), any());
    }

    @Test
    void enablePromptShouldResetAllThenEnableOne() {
        PromptTemplate template = new PromptTemplate();
        template.setId(2L);
        template.setVersionNo(2);
        when(promptMapper.selectById(2L)).thenReturn(template);

        aiService.enablePrompt(2L, admin);

        verify(promptMapper, times(2)).update(any(), any());
        verify(auditService).record(eq(admin), eq("system"), eq("ENABLE_PROMPT"), eq("prompt:v2"), any());
    }

    @Test
    void updateLlmConfigShouldWriteRuntimeParams() {
        LlmConfig config = new LlmConfig();
        config.setId(1L);
        when(llmConfigMapper.selectById(1L)).thenReturn(config);

        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest();
        request.setProvider("OLLAMA");
        request.setModelName("qwen2.5:7b");
        request.setTimeoutSeconds(90);
        request.setMaxRetry(3);

        aiService.updateLlmConfig(1L, request, admin);

        verify(llmConfigMapper).update(any(), any());
        verify(auditService).record(eq(admin), eq("system"), eq("UPDATE_LLM_CONFIG"),
                eq("llm_config:1"), any());
    }

    @Test
    void setGoldenActiveShouldToggleAndAudit() {
        GoldenDataset sample = new GoldenDataset();
        sample.setId(1L);
        sample.setSampleFileKey("golden/S01.docx");
        when(goldenMapper.selectById(1L)).thenReturn(sample);

        aiService.setGoldenActive(1L, 0, admin);

        verify(goldenMapper).update(any(), any());
        verify(auditService).record(eq(admin), eq("system"), eq("DISABLE_GOLDEN"),
                eq("golden:1"), any());
    }

    /* ==================== 系统配置 / 审计 ==================== */

    @Test
    void configShouldExposeNonSecretSnapshot() {
        when(llmConfigMapper.selectList(any())).thenReturn(List.of());
        when(promptMapper.selectList(any())).thenReturn(List.of());
        when(goldenMapper.selectCount(any())).thenReturn(0L);

        var config = systemService.config();

        assertThat(config.smtpConfigured()).isFalse();
        assertThat(config.captchaExpireSeconds()).isEqualTo(300);
        assertThat(config.emailCooldownSeconds()).isEqualTo(60);
        assertThat(config.counts().users()).isZero();
    }

    @Test
    void auditLogsShouldBackfillOperatorName() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setOperatorId(1L);
        log.setModule("system");
        log.setAction("UPDATE_LLM_CONFIG");
        log.setCreateTime(LocalDateTime.now());
        Page<AuditLog> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(log));
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);
        User operator = user(1L, "admin", "ADMIN");
        operator.setRealName("系统管理员");
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(operator));

        var result = systemService.auditLogs(1, 20, "system", "LLM");

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).operatorName()).isEqualTo("系统管理员");
    }

    @Test
    void businessExceptionCarriesExpectedCode() {
        BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "测试");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
    }

    private ClassCreateRequest classRequest() {
        ClassCreateRequest request = new ClassCreateRequest();
        request.setClassName("软件11班");
        request.setClassCode("AIES11-2026");
        request.setTeacherId(2L);
        return request;
    }

    private CreateUserRequest createUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setRealName("新用户");
        request.setPassword("Passw0rd1");
        request.setEmail("");
        request.setPhone("");
        request.setRoleCodes(List.of("STUDENT"));
        return request;
    }

    private UpdateUserRequest updateUserRequest() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRealName("新名");
        request.setEmail("new@aies.local");
        request.setPhone("13900000003");
        return request;
    }
}
