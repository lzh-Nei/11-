package com.aies.service;

import com.aies.entity.GoldenDataset;
import com.aies.mapper.GoldenDatasetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 黄金集扩容 runner（WP-2025-W9-M2 路 A：12→20样本，黄金集门禁正式闭环载体）。
 *
 * <p>触发：{@code --golden.expand=true}（容器内离线执行，与 GoldenEvalRunner 同风格）。
 * 幂等：按 sample_file_key 已存在即跳过（重跑安全）。POI XWPF 生成 8 个分档差异化
 * 学生作业文档（S13~S20）上传热桶 golden/ 前缀 + golden_dataset 入库（active=1，
 * last_consistency 留待送评回填）。</p>
 *
 * <p>分档设计（对齐 W7-C2 偏差分布：≥90两档/中段三档/≤55三档——低档三样本用于
 * 复验 V2' 锚点校准对未见样本的泛化）：S13=92 S14=90 / S15=82 S16=76 S17=70 /
 * S18=54 S19=48 S20=44。teacher_metrics 三指标（功能正确性40/代码质量35/文档规范25，
 * 任务2指标集同名）逐样本拆分（Σ=总分、逐项≤权重上限）。内容差异化：不同学生/
 * 项目/功能清单/代码质量/文档详尽度——避免内容雷同导致 LLM 打分趋同。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "golden.expand", havingValue = "true")
public class GoldenExpandRunner implements CommandLineRunner {

    private final GoldenDatasetMapper goldenDatasetMapper;
    private final FileStorage fileStorage;

    /** 样本规格：学号/姓名/项目名/教师总分/三指标分/档位 */
    private record SampleSpec(String no, String name, String project,
                              int total, int m1, int m2, int m3, String tier) {
    }

    private static final List<SampleSpec> SPECS = List.of(
            new SampleSpec("2023013", "李慕白", "宿舍智能温控系统", 92, 38, 33, 21, "excellent"),
            new SampleSpec("2023014", "王思远", "图书馆座位预约平台", 90, 37, 32, 21, "excellent"),
            new SampleSpec("2023015", "陈嘉怡", "校园二手交易平台", 82, 33, 29, 20, "medium"),
            new SampleSpec("2023016", "赵子轩", "在线考试与题库系统", 76, 31, 27, 18, "medium"),
            new SampleSpec("2023017", "刘雨欣", "食堂排队查询小程序", 70, 29, 24, 17, "medium"),
            new SampleSpec("2023018", "周天乐", "运动打卡应用", 54, 22, 19, 13, "poor"),
            new SampleSpec("2023019", "吴思琪", "校园点餐系统", 48, 20, 17, 11, "poor"),
            new SampleSpec("2023020", "郑皓然", "电动车充电桩管理", 44, 18, 16, 10, "poor"));

    @Override
    public void run(String... args) throws Exception {
        int created = 0;
        int skipped = 0;
        for (SampleSpec spec : SPECS) {
            String key = "golden/golden_S" + spec.no().substring(4) + ".docx";
            String fileKey = "golden_S" + spec.no().substring(4) + ".docx";
            Long exists = goldenDatasetMapper.selectCount(
                    new LambdaQueryWrapper<GoldenDataset>()
                            .eq(GoldenDataset::getSampleFileKey, key));
            if (exists != null && exists > 0) {
                log.info("样本已存在，跳过: {}（幂等）", fileKey);
                skipped++;
                continue;
            }
            byte[] docx = buildDocx(spec);
            fileStorage.store(key, new ByteArrayInputStream(docx), docx.length);

            GoldenDataset row = new GoldenDataset();
            row.setSampleFileKey(key);
            row.setTeacherTotal(BigDecimal.valueOf(spec.total()));
            row.setTeacherMetrics("{\"功能正确性\":" + spec.m1()
                    + ",\"代码质量\":" + spec.m2()
                    + ",\"文档规范\":" + spec.m3() + "}");
            row.setActive(1);
            goldenDatasetMapper.insert(row);
            created++;
            log.info("扩容样本入库: {} 教师总分={} 档位={} {}B", fileKey, spec.total(), spec.tier(), docx.length);
        }
        log.info("=== 黄金集扩容完成: 新增{} 跳过{} 现active样本数={}（目标20，含原12） ===",
                created, skipped, goldenDatasetMapper.selectCount(
                        new LambdaQueryWrapper<GoldenDataset>().eq(GoldenDataset::getActive, 1)));
        System.exit(0);
    }

    /** 按档位生成差异化作业文档（XWPF 段落流；档位决定功能完整度/代码质量/文档详尽度） */
    private byte[] buildDocx(SampleSpec s) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            para(doc, "软件工程实训任务2作业——" + s.project());
            para(doc, "学号：" + s.no() + "  姓名：" + s.name() + "  班级：软件工程11班");

            para(doc, "一、任务理解与需求分析");
            switch (s.tier()) {
                case "excellent" -> {
                    para(doc, "本作业基于任务2要求完成" + s.project() + "的设计与实现。首先梳理业务角色（管理员/普通用户）"
                            + "与核心用例，绘制用例图与流程图，识别出登录鉴权、信息管理、核心业务操作、查询统计四类功能域，"
                            + "并针对并发操作设计了乐观锁与事务边界；数据层遵循三范式设计，对高频查询字段建立索引。");
                }
                case "medium" -> {
                    para(doc, "本作业围绕" + s.project() + "展开，参考任务2要求实现了主要功能模块。设计阶段完成了基本用例梳理，"
                            + "划分了用户管理、信息管理与核心业务三个模块；数据库表按业务实体建立，部分字段存在冗余，"
                            + "未做完整范式化处理。");
                }
                default -> {
                    para(doc, "本作业尝试实现" + s.project() + "。由于时间安排不合理，仅完成了部分功能，"
                            + "设计文档撰写不完整，需求分析阶段没有形成正式文档。");
                }
            }

            para(doc, "二、功能实现清单");
            switch (s.tier()) {
                case "excellent" -> {
                    para(doc, "1.用户注册与登录：图形验证码校验、密码BCrypt加密存储、登录失败次数限制。"
                            + "2.信息管理：增删改查全部实现，含分页与条件过滤、参数校验（JSR-303）与全局异常处理。"
                            + "3.核心业务：" + s.project() + "主流程完整可用，含状态机流转与并发控制（乐观锁版本号）。"
                            + "4.查询统计：多条件组合查询与汇总报表输出，支持导出。"
                            + "5.系统健壮性：统一错误码、操作日志审计、关键路径单元测试覆盖（JUnit5+Mockito，覆盖率约75%）。");
                }
                case "medium" -> {
                    para(doc, "1.用户注册与登录：实现了基本登录功能，密码明文校验后改为MD5，未做失败次数限制。"
                            + "2.信息管理：增删改查已实现，分页可用，参数校验不完整（部分接口依赖前端校验）。"
                            + "3.核心业务：" + s.project() + "主流程基本可用，但边界场景（并发提交、空数据）处理粗糙。"
                            + "4.查询统计：仅实现简单查询，未做汇总统计。"
                            + "5.测试：编写了少量单元测试，覆盖率约30%，核心流程以手工测试为主。");
                }
                default -> {
                    para(doc, "1.用户登录：实现了登录表单页面，后端接口未完成，登录实际不可用。"
                            + "2.信息管理：仅完成列表展示，增删改功能未实现。"
                            + "3.核心业务：" + s.project() + "主流程未能跑通，提交后无响应。"
                            + "4.测试：未编写测试，功能验证不充分。");
                }
            }

            para(doc, "三、核心代码说明");
            switch (s.tier()) {
                case "excellent" -> {
                    para(doc, "服务层采用事务模板包裹业务操作，关键代码如下：");
                    para(doc, "@Transactional\npublic Result submit(OrderDTO dto) {\n    validate(dto);\n    Order order = mapper.selectForUpdate(dto.getId());\n    if (!\"DRAFT\".equals(order.getStatus())) {\n        throw new BusinessException(40001, \"状态不允许提交\");\n    }\n    order.setStatus(\"SUBMITTED\");\n    mapper.updateById(order);\n    eventPublisher.publish(new OrderEvent(order));\n    return Result.ok(order.getId());\n}");
                    para(doc, "说明：参数校验前置，状态检查使用条件更新避免并发覆盖，事件发布置于事务提交后（TransactionSynchronization），"
                            + "异常统一上抛由全局处理器转换为错误码。");
                }
                case "medium" -> {
                    para(doc, "核心代码如下：");
                    para(doc, "public Result submit(OrderDTO dto) {\n    Order order = mapper.selectById(dto.getId());\n    order.setStatus(\"SUBMITTED\");\n    mapper.updateById(order);\n    return Result.ok();\n}");
                    para(doc, "说明：实现了基本提交逻辑，未处理并发与异常场景，参数校验依赖前端。");
                }
                default -> {
                    para(doc, "代码尚未完成，仅有页面框架：");
                    para(doc, "// TODO: 提交接口未实现\npublic Result submit(OrderDTO dto) {\n    return null;\n}");
                }
            }

            para(doc, "四、测试与问题记录");
            switch (s.tier()) {
                case "excellent" -> {
                    para(doc, "测试策略：单元测试覆盖服务层全部分支（含异常流），集成测试验证事务回滚与并发提交；"
                            + "测试中发现并修复了状态机越权流转与缓存不一致两个缺陷，均已回归。性能方面对列表查询做了索引优化，"
                            + "1000条数据响应时间从1.2s降至120ms。");
                }
                case "medium" -> {
                    para(doc, "完成了登录与列表的手工测试，发现分页参数越界会报错（已做钳制修复）。并发场景未测试，"
                            + "存在重复提交风险，留待后续迭代。");
                }
                default -> {
                    para(doc, "未进行系统测试。已知问题：登录不可用、提交无响应、列表分页报错。");
                }
            }

            para(doc, "五、总结与反思");
            switch (s.tier()) {
                case "excellent" -> {
                    para(doc, "本次实训最大的收获是理解了事务边界与并发控制在真实业务中的重要性：最初版本在并发提交下出现数据覆盖，"
                            + "通过乐观锁与条件更新解决。不足之处在于前端体验打磨不够，下一步计划补充前端校验与性能监控。"
                            + "代码已按团队规范提交至版本库并附完整文档。");
                }
                case "medium" -> {
                    para(doc, "通过本次作业基本掌握了" + s.project() + "的开发流程，但在异常处理与测试方面投入不足，"
                            + "部分代码命名与结构仍需改进。后续计划补齐单元测试并完善文档。");
                }
                default -> {
                    para(doc, "时间管理失败导致功能未能完成，教训深刻。今后应尽早开工、按里程碑推进。"
                            + "文档与测试均未完成，代码质量自知不足。");
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void para(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
    }
}
