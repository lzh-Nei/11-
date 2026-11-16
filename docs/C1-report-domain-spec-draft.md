# C1 报表域详细规约（代拟稿 v1，WP-2025-W10-C2）

> 模式：框架先行（PM）+ 执行侧填充 + **用户终审追认**（02-00 注记在案）。
> 定性：UC-25 API 契约为**实现期新增**（原 03-API 仅 T-04 一笔带过）——契约演进透明登记，终审通过后回写 03-API。

## 一、端点清单与权限矩阵

| # | 端点 | 方法 | 权限点（新增，S-xx 序） | 说明 |
|---|------|------|------------------------|------|
| 1 | /api/reports/overview | GET | report:view:teacher（仅教师） | 四图聚合（提交率/均分分布/指标雷达/分差分布），scope=task 可选参数（默认全部任教任务） |
| 2 | /api/reports/overview/me | GET | report:view:student（学生） | 学生个人四图（仅本人提交数据，IDOR 同形 40400） |
| 3 | /api/reports/exports | POST | report:export:teacher | 发起异步导出（PDF），body={taskId?, format:"pdf"}，返回 exportTaskId |
| 4 | /api/reports/exports/{id} | GET | report:export:teacher / report:view:student（本人任务） | 导出状态查询（PENDING/RUNNING/DONE/FAILED + MinIO 签名 URL 300s，对标 S-02） |
| 5 | /api/reports/refresh | POST | report:refresh:teacher | 缓存强制重建（UC-30 对标），幂等 |

权限矩阵终审点：教师=任教任务聚合（默认）；学生=仅本人（overview/me）；管理员暂不开放全局聚合（M2 范围收敛，答辩演示链更清晰）——**请终审确认**。

## 二、响应契约（DTO 形状）

- OverviewDTO：submissionRate{taskId,total,submitted,rate}[] / scoreDist{range,count}[]（CASE 分段 0-59/60-69/70-79/80-89/90-100）/ metricRadar{metricName,avgScore,avgWeightRatio}[] / scoreGapDist{range,count}[]（REVIEWED 版本教师分−AI 分差值段）
- ExportTaskDTO：id/status/format/createTime/doneTime/downloadUrl（DONE 时，签名 URL）
- 分页：导出任务列表游标分页（对标 S-04 钳制口径 pageSize≤50）

## 三、数据口径（LLD §五 全文继承）

- 仅 JOIN evaluation_result **当前值**（ADR-004 红线）；FAILED 不计入均分、计入提交率分母
- 指标雷达从 metric_scores JSON 聚合（键=任务指标集）
- 分差分布仅取 history.source=TEACHER_REVIEW/RE_EVALUATE 的版本对（教师分−AI 分）

## 四、缓存一致性（LLD 未写明，C1 定死——终审确认点）

- Redis key=report:overview:{scope}，TTL 10 分钟（LLD 口径）
- **复核/人工评分/重评落库时主动失效**对应 scope 缓存（EvaluationCommandService 三写路径挂失效钩子）——理由：教师复核后立即回看报表是演示链路，10min 陈旧窗口会演示出"改了分但报表没变"的尴尬；主动失效成本≈零
- 备选（若终审嫌耦合）：接受 10min 陈旧+refresh 端点兜底

## 五、异步导出链（对齐 LLD，不自创）

- report_export_task 落库（PENDING）→ MQ（复用现有交换机新增 report.export 路由，对标 eval.submit 模式）→ ExportWorker 虚拟线程生成 PDF（report 数据快照+图表渲染）→ MinIO 热桶 export/{taskId}.pdf → 状态 DONE+签名 URL
- 幂等键：同一 scope+同一数据版本号重复发起→返回既有 taskId（防重）
- 失败：FAILED+errorCode，不阻塞主链

## 六、错误码（D-17 口径）

| 场景 | 码 |
|------|-----|
| 学生查他人报表/不存在 | 40400 同形 |
| 导出任务不存在/非本人 | 40400 |
| 重复发起（幂等命中） | 200 返回既有 taskId |
| 前置不满足（无任教任务等） | 30000 具体文案 |
| 导出失败 | 任务状态 FAILED+errorCode，HTTP 侧查询时返回 |

## 七、测试要求（维持战纪律）

- Service 层：四图聚合口径单测（含 FAILED 计分母不计均分/JSON 聚合/缓存失效钩子）+ 导出幂等
- Controller 层：MockMvc 契约（权限负例/40400 同形/分页钳制）
- 目标：新增代码自带覆盖 ≥60%，62.30% 分母增量同步覆盖
