# W8-M1 收尾包实现规格（spec-w8-m1）

> 范围：①E-02~E-06复核域 ②V6 trace补偿表 ③V7 prompt V2锚点校准 ④前端注册/忘记密码页 ⑤覆盖率维持战
> 依据：03-API接口契约 §七、02-用例规约 UC-22~25、04-LLD §4.7类图、02-数据库设计 §3.5/3.6、W7-C2验收裁决（PM）

## 一、E-02~E-06 复核域（后端）

### 契约要点（已核）
| 接口 | 方法/路径 | 权限点 | 前置 | 关键校验 |
|------|----------|--------|------|---------|
| E-02 | PUT /api/evaluations/{id}/review | evaluation:review（已有常量） | status=AI_DONE | reviewComment trim后≥10字否则30007；指标集/权重/Σ校验 |
| E-03 | POST /api/evaluations/{id}/re-evaluate | evaluation:re-evaluate（已有） | status∈{REVIEWED,FAILED} | 置PENDING后发eval.submit消息；audit_log审计一条 |
| E-04 | GET /api/evaluations/{id}/history | evaluation:history:view（已有） | — | cursor=Base64(versionNo)不透明游标；limit默认20、1~50越界→30000；versionNo倒序 |
| E-05 | GET /api/evaluations/{id}/history/{versionNo} | 同上 | — | 完整快照（metric_scores/评语/理由）；学生裁剪traceId |
| E-06 | PUT /api/evaluations/{id}/manual | evaluation:review | status=FAILED | 同E-02；source=TEACHER_REVIEW（UC-24复用复核） |

### 已决策（不再回问）
1. **版本语义**：history为追加式版本账本，nextVersion = 该evaluation已有最大version_no+1（无行则1）。AI路径（persistEvaluation）已按此写v1；FAILED从不写history行，故FAILED重评/人工评分写v1、AI_DONE复核写v2、REVIEWED重评写v3——uk_eval_version天然不冲突，persistEvaluation无须改动。
2. **IDOR口径**：STUDENT访问任何评价接口→40400同形（与E-01一致）；TEACHER须为task.teacherId本人，否则40400同形；ADMIN放行。
3. **状态违规码**：前置不满足（如对REVIEWED再复核、对AI_DONE重评）→30000 PARAM_ERROR，message具体化（契约异常流仅定义30007/乐观锁，30000为请求类统一兜底口径）。
4. **乐观锁**：result.updateById走@Version，返回0行→BusinessException(30000,"复核并发冲突，请刷新后重试")（UC-22 E2，MVP不自动重试）。
5. **教师输入校验**（对齐SchemaValidator纪律）：指标键集合=task指标集、每项0≤score≤weight、Σ指标分=totalScore±0.5（复用SUM_TOLERANCE常量）、totalScore∈[0,100]，违规均30000。
6. **复核版本快照**：TEACHER_REVIEW版本保留原aiComment（教师不改评语），teacherComment=理由、reviewerId=操作人；result同步totalScore/metricScores/status=REVIEWED/reviewerId/reviewTime。
7. **重评触发**：非事务；REVIEWED先bump currentVersionNo再置PENDING（worker幂等只放行PENDING），FAILED只置PENDING；rabbitTemplate发EvalMessage至aies.eval.exchange/eval.submit；submission.parseStatus必须SUCCESS否则30000。
8. **重评审计**：新建AuditLog entity+AuditLogMapper（audit_log表V4已建，首个写入方，S-09仅INSERT/SELECT），module=evaluation/action=RE_EVALUATE/target="evaluation:{id}"/detail={submissionId,operatorId}。
9. **reviewer_name**：E-04列表join sys_user.real_name（AI版本null）——UserMapper.selectById。

### 新增/改动文件
| 文件 | 动作 |
|------|------|
| dto/ReviewEvaluationRequest.java | 新增：totalScore@NotNull、metricScores@NotEmpty(Map)、reviewComment |
| dto/EvaluationHistoryVO.java | 新增：versionNo/source/totalScore/reviewerName/createTime + E-05扩展metricScores/aiComment/teacherComment/traceId |
| dto/EvaluationHistoryPageVO.java | 新增：list/nextCursor/hasMore |
| entity/AuditLog.java、mapper/AuditLogMapper.java | 新增 |
| service/EvaluationCommandService.java | 新增：review/manualScore/reEvaluate（review与manual共用私有doReview(expectedStatus)） |
| service/EvaluationQueryService.java | 扩展：getHistoryPage/getVersion（LLD §4.7 pageHistory归查询域） |
| controller/EvaluationController.java | 扩展五端点，@PreAuthorize三权限点 |

### 测试（新增自带覆盖，守60.51%）
- EvaluationCommandServiceTest（新，~16例）：复核正常链/理由空30007/理由<10字30007/状态非AI_DONE 30000/总分越界/指标缺项/超权重/Σ不符各30000/学生40400/非归属教师40400/乐观锁冲突30000/manual FAILED正常/manual REVIEWED拒绝30000/re-evaluate REVIEWED bump+发消息/re-evaluate FAILED不bump/re-evaluate非法状态30000/re-evaluate审计行
- EvaluationQueryServiceTest增补（~8例）：首页倒序/翻页cursor/cursor非法30000/limit越界30000/hasMore与nextCursor/学生裁剪traceId/学生IDOR 40400/E-05快照与版本不存在40400
- EvaluationControllerTest增补（5例）：五端点契约形状（@PreAuthorize负例归实弹，既有登记口径）

## 二、V6 trace补偿表（W8-D4硬边界）

### 决策
1. 表结构规约无原文（LLD仅"失败→补偿日志表+定时任务重试补写"），按03-02风格自设计，迁移头注记偏差登记：`trace_compensation(id PK, trace_id VARCHAR(64) UNIQUE, object_key VARCHAR(128), payload JSON NOT NULL, status VARCHAR(16) PENDING|DONE, retry_count INT, last_error VARCHAR(512), next_retry_time DATETIME, create_time, update_time)`。
2. TraceRecorder.record写冷桶失败→TraceCompensationService.enqueue(traceId,key,bytes,reason)（自身try/catch，DB也挂→降级ERROR日志）；ERROR日志主入口迁至表。
3. TraceCompensationService.retryPending @Scheduled(fixedDelay=60s)：扫PENDING且next_retry_time≤now→storeCold→DONE；失败retry_count+1、指数退避next_retry_time=now+2^n分钟、last_error留存；retry_count≥10保留PENDING+WARN（人工兜底）。
4. 新增config/SchedulingConfig（@EnableScheduling，主代码现无调度器）。
5. 文件：V6__trace_compensation.sql、entity/TraceCompensation.java、mapper/TraceCompensationMapper.java、service/TraceCompensationService.java、TraceRecorder改造。
6. 测试：TraceRecorderTest增补（失败→enqueue）、TraceCompensationServiceTest（成功DONE/失败退避/未到期跳过/超限保留/enqueue自身异常不抛）。

## 三、V7 prompt V2锚点校准

### 决策
1. V7__prompt_template_v2_anchor.sql：INSERT v2行(enabled=1) + UPDATE v1 enabled=0（增量迁移不破V1校验和口径）；锚例取S03(93优秀)/S08(73中等)/S09(60待改进)三档教师标注（teacher_metrics实际值实现时docker exec查库后定稿入SQL），指令含"按锚点区间内插、禁止向中段收敛、低分档允许50以下"。
2. 重跑流程：mvn package（**禁clean后不打包**红线）→docker restart aies-dev-app→`--eval.replay=true`跑GoldenEvalRunner→读偏差表→≥70%（≥9/12）达标；<70%第二轮迭代（锚例措辞/分档边界调整），两轮预算封顶。
3. last_consistency由runner自动回写（已有机制）；偏差表更新进 golden-eval-report-W7C2.md（追加W8章节）。

## 四、前端注册页+忘记密码页（W8-D2硬边界）

### 决策
1. 后端全就绪（A-06~A-09端点+EmailScene三值REGISTER/LOGIN/RESET_PASSWORD实测确认），纯前端交付。
2. api/auth.ts扩展emailCode/register/forgotPassword/resetPassword；请求字段按DTO实测：EmailCodeSendRequest{username,email,scene,captchaId,captchaCode}、RegisterRequest{username,password,realName,email,classCode,emailCode,captchaId,captchaCode}、ForgotPasswordRequest{username,email,captchaId,captchaCode}、ResetPasswordRequest{username,emailCode,newPassword}。
3. 新页RegisterView.vue（scene=REGISTER）+ForgotPasswordView.vue（A-09统一文案防枚举；RESET_PASSWORD码发送复用email-code scene=RESET_PASSWORD，SMTP未就绪40004→B计划降级提示"演示走账密+图形码主路径"）；router公开路由/register、/forgot-password；LoginView加入口链接。
4. 验证：vite build + 浏览器实测（图形码实读→发送码→注册成功登录）。

## 五、覆盖率维持战与汇报

1. 新增实现行数预估≈400行，自带测试如上≈900行测试代码；完成后全量jacoco流程（MAVEN_OPTS挂agent→test→report→csv）验证核心口径≥60.51%不被稀释。
2. 汇报三件套：核心覆盖率% + 新增代码自身覆盖 + V2偏差分布表；偏差登记四条：V6表自设计、状态违规=30000口径、audit_log首用、RE_EVALUATE版本语义。

## 六、执行顺序与验证

1. E-02~E-06实现+测试 → 2. V6补偿域 → 3. mvn test全绿（-DforkCount=0）+中期jacoco → 4. V7迁移+统一package+restart+黄金集重跑（一次容器动作串起V6/V7）→ 5. 前端两页+浏览器走查 → 6. 终跑覆盖率+四段式汇报。

ArchUnit红线自检：controller不触mapper/entity（VO出参）；@Transactional仅在service；mapper不反向依赖service。
