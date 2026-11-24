package com.aies.service;

import com.aies.config.AuthProperties;
import com.aies.dto.admin.AdminDtos.AuditLogFilter;
import com.aies.dto.admin.AdminDtos.AuditLogVO;
import com.aies.dto.admin.AdminDtos.CountsVO;
import com.aies.dto.admin.AdminDtos.SystemConfigVO;
import com.aies.entity.AuditLog;
import com.aies.entity.GoldenDataset;
import com.aies.entity.LlmConfig;
import com.aies.entity.PromptTemplate;
import com.aies.entity.User;
import com.aies.mapper.AuditLogMapper;
import com.aies.mapper.EvaluationResultMapper;
import com.aies.mapper.GoldenDatasetMapper;
import com.aies.mapper.LlmConfigMapper;
import com.aies.mapper.PromptTemplateMapper;
import com.aies.mapper.SubmissionMapper;
import com.aies.mapper.SysClassMapper;
import com.aies.mapper.TaskMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M3 管理端系统配置快照与审计日志查询（UC-33/40）。
 *
 * <p>配置面只读：SMTP 主机（环境变量）、验证码/邮箱码时效（auth.*）、LLM 当前模型
 * （llm_config）、启用 Prompt 版本、黄金样本启用数、数据规模计数；不暴露任何密钥。
 * 审计日志只读（S-09 应用侧仅 INSERT+SELECT）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminSystemService {

    private final AuthProperties authProperties;
    private final UserMapper userMapper;
    private final SysClassMapper classMapper;
    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final GoldenDatasetMapper goldenDatasetMapper;
    private final AuditLogMapper auditLogMapper;
    private final SysConfigService sysConfigService;
    private final ReportQueryService reportQueryService;
    private final AdminAuditService auditService;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    public SystemConfigVO config() {
        CountsVO counts = new CountsVO(
                userMapper.selectCount(null),
                classMapper.selectCount(null),
                taskMapper.selectCount(null),
                submissionMapper.selectCount(null),
                evaluationResultMapper.selectCount(null));

        AuthProperties.Captcha captcha = authProperties.getCaptcha();
        AuthProperties.Email email = authProperties.getEmail();

        List<LlmConfig> llmConfigs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>().orderByAsc(LlmConfig::getId));
        String llmModel = llmConfigs.isEmpty() ? null : llmConfigs.get(0).getModelName();

        List<PromptTemplate> enabledPrompts = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getEnabled, 1)
                        .orderByDesc(PromptTemplate::getVersionNo)
                        .last("LIMIT 1"));
        Integer activePromptVersion = enabledPrompts.isEmpty()
                ? null : enabledPrompts.get(0).getVersionNo();

        long goldenActive = goldenDatasetMapper.selectCount(
                new LambdaQueryWrapper<GoldenDataset>().eq(GoldenDataset::getActive, 1));

        boolean smtpConfigured = StringUtils.hasText(smtpHost);
        return new SystemConfigVO(counts, smtpConfigured, smtpConfigured ? smtpHost : null,
                captcha.getExpireSeconds(), captcha.getCodeCount(),
                email.getExpireSeconds(), email.getCooldownSeconds(),
                llmModel, activePromptVersion, goldenActive, sysConfigService.editable());
    }

    /** 更新白名单系统参数并清空报表缓存（变更即时生效） */
    public void updateConfig(Map<String, String> values, LoginPrincipal principal) {
        sysConfigService.apply(values);
        reportQueryService.evictAll();
        auditService.record(principal, "system", "UPDATE_SYSTEM_CONFIG", "sys_config",
                new java.util.LinkedHashMap<>(values));
    }

    /** 审计日志分页（模块/动作/关键字/操作人/时间窗筛选；operator 名称回填） */
    public Page<AuditLogVO> auditLogs(long page, long size, AuditLogFilter filter) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(filter.module())) {
            wrapper.eq(AuditLog::getModule, filter.module().trim());
        }
        if (StringUtils.hasText(filter.action())) {
            wrapper.like(AuditLog::getAction, filter.action().trim());
        }
        if (StringUtils.hasText(filter.keyword())) {
            String keyword = filter.keyword().trim();
            wrapper.and(w -> w.like(AuditLog::getAction, keyword)
                    .or().like(AuditLog::getTarget, keyword)
                    .or().like(AuditLog::getDetail, keyword));
        }
        if (StringUtils.hasText(filter.operator())) {
            List<Long> operatorIds = operatorIds(filter.operator().trim());
            if (operatorIds.isEmpty()) {
                Page<AuditLogVO> empty = new Page<>(page, size, 0);
                empty.setRecords(List.of());
                return empty;
            }
            wrapper.in(AuditLog::getOperatorId, operatorIds);
        }
        if (filter.start() != null) {
            wrapper.ge(AuditLog::getCreateTime, filter.start());
        }
        if (filter.end() != null) {
            wrapper.le(AuditLog::getCreateTime, filter.end());
        }
        wrapper.orderByDesc(AuditLog::getId);

        Page<AuditLog> result = auditLogMapper.selectPage(Page.of(page, size), wrapper);
        Map<Long, User> operators = operators(result.getRecords());
        List<AuditLogVO> records = result.getRecords().stream()
                .map(log -> new AuditLogVO(log.getId(), log.getOperatorId(),
                        operatorName(operators.get(log.getOperatorId())),
                        log.getModule(), log.getAction(), log.getTarget(),
                        log.getDetail(), log.getIp(), log.getCreateTime()))
                .toList();
        Page<AuditLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /** 按账号/姓名模糊匹配操作人（上限200，避免超长 IN 列表） */
    private List<Long> operatorIds(String keyword) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.select(User::getId)
                .and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword))
                .last("LIMIT 200");
        return userMapper.selectList(query).stream().map(User::getId).toList();
    }

    private Map<Long, User> operators(List<AuditLog> logs) {
        List<Long> ids = logs.stream().map(AuditLog::getOperatorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private String operatorName(User user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
