# 02-00 项目决策日志（补建于 W9-M2）

> 说明：决策日志此前仅在周报/验收口径中维护，未落盘——W9-M2 复盘发现后补建。
> D-09~D-20 系早期裁决，原文不可考（登记存在性，明细见各阶段文档注记）；
> D-21 起为可考裁决摘要，随新裁决增量登记。

## 早期裁决（存在性登记）

| 编号 | 主题 | 落点 |
|------|------|------|
| D-09~D-20 | 架构/包结构/安全/测试框架等早期裁决 | 各阶段文档注记（04-LLD ADR/05规约） |

## 可考裁决（D-21 起）

| 编号 | 主题 | 裁决 |
|------|------|------|
| D-21 | 测试框架文档集（05集）落盘 | W7-C1 五轮跳票后总清账兑现 |
| D-22 | 包结构扁平口径 | com.aies 扁平包既有裁决；W8-M1 evaluation.client 纵向包待 L1 businessX 铺开时统一切 |
| D-23 | MinIO 桶化 | W7-C1 兑现（26对象同形迁移零迁移成本） |
| D-24 | SecurityConfig 豁免 | 配置类 Bean jacoco exclusions 追加（04-06 §5.1 注记） |
| D-25 | coverage report-only | CI 侧覆盖报告为例外通道登记口径（W9-M2 CI 首跑验证 ✓） |
| D-26 | LangChain4j→OkHttp 追认 | LlmClient 抽象必须保持，ArchUnit/评审守护 |
| D-27 | dev 种子密码 Passw0rd | 仅 dev 库；文档不写明文；生产走正式初始化 |
| D-28 | V6 trace_compensation 表结构自设计追认 | 规约侧回写对齐（02-数据库设计 §3.7 已落） |
| D-29 | Prompt 工程纪律 | 数字锚反噬+指令密度优先（05-AI模块专项编码规约 §四已落） |
| D-30 | 65% 组合裁决 | M1以12样本75%闭环不回退；04-08四项登记（EX-D30）；NFR-03靶子重设20样本口径；选项3挪门柱否决 |
| D-31 | P2 三路径排序 | few-shot(W10小试,带V7红线)→rubric操作化(中期)→judge模型升级(最后)；成本递增可逆性递减 |
| D-32 | 分支保护与M1封版解耦 | main分支保护从门禁/挂账剥离为"M2-PR前置建议项"；M1材料包即刻封版不等待用户侧动作 |
| D-33 | 工作区双编辑者纪律 | 执行侧对非自己产生的文件零删除权/零修复权（异常→报告+绕行）；用户未提交文件默认"用户领地"零接触；推送前三查升级四查（+git status/diff --cached 暂存集核验）；共享文件错峰+事前告知（04-06 本地操作纪律节同步收录） |
| D-34 | 认证域方向=A（M3+ 认证增强） | 用户裁决：SaToken 定性 M3+ 增强方向（Spring Security 保留，短信登录为新增方式）——规约先行：03-API 契约补 UC 编号+S 规约安全审查（短信码防枚举/频控/生命周期对标 S-02 邮箱码）后开工；替换 Security 方案评估撤销；两周时限解除；SaToken 完成前勿推 feature/QQ 纪律维持 |
| D-35 | git worktree 构建隔离 | 双编辑者矛盾从文件系统层上移到 git 层：执行侧独立 worktree ../11-exec（纪律入 06 附录A），构建/开发/推送全隔离，主工作区零写入——不改用户一行代码解掉全部构建阻塞 |
| D-36 | C1 两确认点裁决 | 权限矩阵收敛批准（/me 端点消除 studentId 暴露+管理员 M3+ 留痕回写 03-API）；缓存"主动失效+TTL 兜底"双保险（ApplicationEvent 经 common 解耦+AFTER_COMMIT 监听+盲点1异常吞噬 TTL 收敛）；用户终审合并至 03-API 回写批量终审 |

## 过程注记（W11-C1）

- **UC-25 实现遵守测试同批上链纪律，推送延后至测试补齐**（255/255 全绿随实现批同推）；
- PM 三盲点预警处置：盲点1 监听器 try-catch+TTL 兜底（已补）/盲点2 三落库点 @Transactional 反射钉死（测试）/盲点3 回滚对照以实弹验证承载（单测无法跨真实事务，登记）；
- refresh 端点语义确认：仅 TEACHER 权限（任教 scope 手动兜底），学生侧失效完全事件驱动（数据变更源=教师复核/重评事件已覆盖），设计成立。

## 过程注记（W9-M2）

- D-30/D-31 落档验证通过，**锚点机制首轮实证**（W10-C1 首波 4/4 锚点点开核验）——汇报规约见 08 文档 §七。

## 过程注记（W9-M2）

- **PR 粒度首分支降级为全域承载（CI 补验优先），W9-M2 起恢复模块粒度**——首分支
  （chore/ci-first-run，PR#2）承载 backend 全域+CI 修复链，merge_commit=30f157c；
  后续报表域起恢复一域一 PR。两个工作分支已按指令合并清理（chore/ci-first-run、
  chore/1-bootstrap-scaffold 本地+远程双删，PR#2 由 GitHub 自动判定 merged）。

## 待裁决（W9-M2 扩容复验）

- **黄金集 20 样本一致率 65% < 70%**（12样本口径 75% ✓ 保持）：低档坍缩定性为 7B 模型
  与教师评分标准的系统性分歧（完成度线索 vs 主流程跑通），prompt 迭代链五轮证伪
  （V7→V8→V9→V10→V11回滚），详见 golden-eval-report-W7C2.md W9-M2 附录 §四——
  三选项（例外登记/P2技术路径/样本调整）待 PM 裁决。

## 待裁决（WP8-B6/B8 前端验收发现，2026-11-16）

- **学生端评价历史版本（02-05 #08，P0）与权限矩阵冲突**：V1__rbac.sql 的 STUDENT 角色
  未授予 `evaluation:history:view`（D-36 矩阵 STUDENT=7 项口径），E-04/E-05 对学生会返回
  40301。前端已按权限显隐历史区（不伪造访问）。需裁决：
  A) 补授 STUDENT 该权限点（新增 V 迁移 + 回写 03-API 权限矩阵与 02-05 页面范围）；
  B) 修订页面清单，将 #08 定性为教师侧能力（学生仅看当前生效版本）。
- **教师端提交总览（#14）/报表导出（#16）缺后端端点**：SubmissionController 仅 `/mine`
  （本人视角），ReportController 无 exports 链（`report:export` 仍为预留权限点）。两页
  暂无法真实承载，维持 M3 规划，不计入本轮前端验收缺口。
- **E-04/E-05 契约口径回写**：EvaluationHistoryPageVO 实为
  `list/nextCursor/hasMore`，列表项仅 `versionNo/source/totalScore/reviewerName/createTime`；
  `teacherComment/metricScores/aiComment/traceId` 仅 E-05 快照返回。前端曾按
  `items/operatorName/createdAt` 实现（渲染崩溃根因），B6 已校正——建议 03-API 回写该细节。
- **A-10 重置密码不清 `first_login`（UC-07 遗留）**：登录后自助改密页（B8，A-06 邮箱码 →
  A-10 重置）实测成功（重置→强制重登→新密码登录闭环），但 `sys_user.first_login` 仍为 1，
  "首次登录请改密"提示在改密后持续存在。建议认证域微修：`AuthService.resetPassword`
  成功后同置 `first_login=0`（或登录成功后按 `password_changed_at` 推导）；前端不做本地掩盖。

## 过程注记（WP8-C1 模块化信息架构）

- **左右布局**：每角色左侧功能模块导航 + 右侧模块页面；模块集合由角色 × 权限矩阵生成。
  - 学生：工作台 / 任务中心 / 提交档案 / 我的评价 / 文件中心 / 个人中心 / 账号安全
  - 教师：工作台 / 任务管理 / 评价复核 / 报表驾驶舱 / 文件中心 / 个人中心 / 账号安全
  - 管理员：工作台 / 任务管理 / 评价复核 / 文件中心 / 个人中心 / 账号安全 / 系统管理
- **下钻页新窗口**：提交作业（/tasks/:id/submit）、评价报告（/evaluations/:id）、复核台
  （/evaluations/:id/review）以 `window.open` 新窗口打开，仍复用同一外壳（左侧模块 + 右侧页面）。
- **特殊模块**：报表驾驶舱点击直接新开 `/report.html`（独立静态驾驶舱），不出现在右侧路由区。
- **管理员口径**：C1 裁决管理员暂不开放全局聚合，管理员工作台不请求 `/reports/overview`
  （避免 403），仅展示管理导览；`report:view:teacher` 缺失时导航不显示报表模块。
- 角色硬边界：模块路由 meta.roles 守卫，越权直达 → /403（服务端权限点仍为最终兜底）。

## 过程注记（M3 管理端交付，WP8-C2，2026-11-16）

- **范围**：系统管理 8 个 M3 规划模块全部落地为真实端点 + 真实界面（无 mock）：
  用户管理（检索/禁用启用/角色分配/教师下拉）、角色权限矩阵（含 ADMIN 只读）、班级管理
  （CRUD + 占用删除保护）、审计日志（只读检索）、系统配置（只读运行快照）、LLM 参数、
  Prompt 模板（新建/唯一启用版）、黄金数据集（清单/标注/参与回归启停）。
- **后端**：`AdminController` 单入口 `/api/admin/**`，权限点复用 V1 system 段 8 项（未新增矩阵项）；
  写操作全部 `AdminAuditService` 留痕（S-09，含 before/after 快照）；用户禁用同时作废 RT 并清
  `auth:perm` 缓存；角色权限变更即时清理该角色全部用户权限缓存（UC-32 即时生效，TTL 兜底）。
  护栏：禁止修改当前登录账号；ADMIN 账号状态/角色与 ADMIN 角色矩阵只读（防锁死）；班级有学生或
  任务时禁删；Prompt 必须含 `{metrics}`/`{parsed_text}` 占位符。
- **测试**：AdminServiceTest 24 例；全量 279/279 绿；checkstyle 绿（顺带修复 HEAD 遗留 4 处
  checkstyle：2 处未用导入、BD 私有方法命名、1 处测试未用导入——均无行为变化）；spotbugs 绿。
- **构建隔离**：主工作区存在用户未提交的 SaToken/短信 WIP（类名/文件名不符 + 缺依赖，整仓编译
  中断，按 D-33 零接触）。管理端后端在独立 worktree `../11-exec-admin`（分支 feature/admin-m3）
  实现、测试、打包，再 ff-merge 回 feature/QQ；运行 jar 由干净 worktree 产出并投放
  `backend/target`。用户 WIP 收口前，主工作区 `mvn package` 仍会失败，与本次交付无关。
- 管理端发布前提醒：M3 管理端点首版未加操作频控/二次确认口令；生产环境建议补审计告警与
  RBAC 变更双人复核（04-08 §三例外登记候选）。

## 过程注记（WP8-C3 滚动容器规约，2026-11-16）

- 新增通用 `ScrollArea`（限高 + `overscroll-behavior: contain` + 统一细滚动条），作为
  "长内容只在本组件内滚动"的统一承载，覆盖三层：表格层 `DataTableShell`、页面级卡片/列表
  （任务中心/任务管理/复核台最近记录与任务表/评价历史/个人中心权限清单）、管理端矩阵与
  Prompt 版本表。
- Element Plus 表格改为身体区 `.el-scrollbar__wrap` 内部纵向滚动（保留横向滚动与右侧固定列），
  表头常驻；原生表格（文件中心/Prompt/复核台）表头 `position: sticky`。
- 验证口径：内层 `scrollTop` 变化时 `window.scrollY` 与 `document.scrollingElement.scrollTop`
  恒为 0，`body.scrollHeight` 等于视口高度；审计长列表（16+ 行）实测通过。

