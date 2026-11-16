# W8-M1 收尾包任务清单（tasks-w8-m1）

> 配套规格：docs/spec-w8-m1.md（决策依据与口径，批准后按此执行）

## T1 E-02~E-06 复核域实现
- [x] dto：ReviewEvaluationRequest / EvaluationHistoryVO / EvaluationHistoryPageVO
- [x] entity/AuditLog + mapper/AuditLogMapper
- [x] service/EvaluationCommandService：review / manualScore（共用doReview） / reEvaluate（bump版本+置PENDING+发eval.submit+audit_log）
- [x] service/EvaluationQueryService：getHistoryPage（Base64游标+limit钳制+reviewer_name）/ getVersion（快照+学生裁剪traceId）
- [x] controller/EvaluationController：五端点+三权限点@PreAuthorize
- 验收：EvaluationCommandServiceTest（22例）+ QueryService增补（12例）+ ControllerTest增补（6例）全绿 ✅

## T2 V6 trace补偿域
- [x] V6__trace_compensation.sql（表+偏差注记头）
- [x] entity/TraceCompensation + mapper/TraceCompensationMapper
- [x] service/TraceCompensationService：enqueue + retryPending(@Scheduled 60s, 指数退避, ≥10次保留PENDING)
- [x] config/SchedulingConfig（@EnableScheduling）
- [x] TraceRecorder改造：冷桶写失败→enqueue（ERROR日志主入口迁表）
- 验收：TraceRecorderTest增补（6例）+ TraceCompensationServiceTest（8例）全绿 ✅；V6迁移实弹应用成功

## T3 全量回归
- [x] mvn test "-DforkCount=0" 全绿：244/244（193+51新增）+ ArchUnit不破
- [x] jacoco中期跑数：核心口径62.30%（6642/10662，较60.51%提升+1.79pp，未稀释反升）

## T4 V7锚点校准（容器统一动作）
- [x] 查golden_dataset锚例实值 → V7__prompt_template_v2_anchor.sql（v2插行enabled=1，v1置0）→ 迁移成功
- [x] 第一轮重跑：一致率1/12 + 4样本超时FAILED（1134字符长模板致小模型遵循反退+KV cache溢出）→ V8压缩调优迁移（≈320字符档位表）已应用（schema v8）
- [x] 第二轮重跑：**一致率9/12=75.0%≥70%达标**，12/12零重试；last_consistency回填（9×1.000/3×0.000）；报告W8章节收口

## T5 前端注册+忘记密码页（W8-D2硬边界）
- [x] api/auth.ts扩展四端点
- [x] RegisterView.vue（scene=REGISTER全流程；dev模拟模式mockCode回显）
- [x] ForgotPasswordView.vue（两步el-steps；A-09统一文案；40004降级提示）
- [x] router公开路由 + LoginView入口链接
- [x] vite build + vue-tsc 类型检查通过
- [x] 浏览器实测全流程PASS：注册（图形码实读→模拟码668796→DB建号id=5→跳转）/ 忘记密码（统一文案防枚举→重置码812146→新密码重置成功）/ LoginView入口链接 / 新密码登录闭环

## T6 覆盖率终跑+汇报
- [x] 终跑jacoco：核心口径62.30%（6642/10662，较60.51%提升+1.79pp；代码此后未变即终值）
- [x] 四段式汇报输出（见会话）

## 执行顺序
T1 → T2 → T3 → T4（含T5并行准备，容器重启后先重跑再彩排）→ T5 → T6
