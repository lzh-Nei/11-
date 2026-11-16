package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.EvaluationHistoryPageVO;
import com.aies.dto.EvaluationHistoryVO;
import com.aies.dto.EvaluationVO;
import com.aies.dto.EvaluationAppealRequest;
import com.aies.dto.ReviewEvaluationRequest;
import com.aies.security.LoginPrincipal;
import com.aies.service.EvaluationAppealService;
import com.aies.service.EvaluationCommandService;
import com.aies.service.EvaluationQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价接口（03-03 §七 E-01~E-06）。
 *
 * <p>E-01 UC-21报告页（W7-C2）；E-02复核/E-03重评/E-04历史分页/E-05单版本快照/
 * E-06手动评分旁路（W8-M1，UC-22~25）。UC-20触发AI评分为MQ系统内部链路，无REST接口。</p>
 */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationQueryService evaluationQueryService;
    private final EvaluationCommandService evaluationCommandService;
    private final EvaluationAppealService evaluationAppealService;

    /** E-01 评价当前值：STUDENT限本人(40400同形)；教师视角另含trace_id */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_VIEW + "')")
    @GetMapping("/{id}")
    public Result<EvaluationVO> detail(@PathVariable long id) {
        return Result.success(evaluationQueryService.getById(id, currentPrincipal()));
    }

    /** E-02 教师复核改分（UC-22：AI_DONE→REVIEWED，理由≥10字，乐观锁防并发） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_REVIEW + "')")
    @PutMapping("/{id}/review")
    public Result<EvaluationVO> review(@PathVariable long id,
                                       @Valid @RequestBody ReviewEvaluationRequest request) {
        return Result.success(evaluationCommandService.review(id, request, currentPrincipal()));
    }

    /** E-03 触发重评（UC-23：REVIEWED/FAILED→PENDING→MQ重走管道，RE_EVALUATE版本+审计） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_RE_EVALUATE + "')")
    @PostMapping("/{id}/re-evaluate")
    public Result<Void> reEvaluate(@PathVariable long id) {
        evaluationCommandService.reEvaluate(id, currentPrincipal());
        return Result.success(null);
    }

    /** E-04 历史版本分页（UC-25：倒序+不透明游标；STUDENT限本人，TEACHER限本人任务） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_HISTORY_VIEW + "')")
    @GetMapping("/{id}/history")
    public Result<EvaluationHistoryPageVO> history(@PathVariable long id,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(required = false) Integer limit) {
        return Result.success(evaluationQueryService.getHistoryPage(id, cursor, limit, currentPrincipal()));
    }

    /** E-05 单版本完整快照（UC-25：metric_scores/评语/理由；学生裁剪trace_id） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_HISTORY_VIEW + "')")
    @GetMapping("/{id}/history/{versionNo}")
    public Result<EvaluationHistoryVO> historyVersion(@PathVariable long id,
                                                      @PathVariable int versionNo) {
        return Result.success(evaluationQueryService.getVersion(id, versionNo, currentPrincipal()));
    }

    /** E-06 手动评分旁路（UC-24：FAILED直接打分，复用复核链source=TEACHER_REVIEW） */
    @PreAuthorize("hasAuthority('" + PermissionConstants.EVALUATION_REVIEW + "')")
    @PutMapping("/{id}/manual")
    public Result<EvaluationVO> manualScore(@PathVariable long id,
                                            @Valid @RequestBody ReviewEvaluationRequest request) {
        return Result.success(evaluationCommandService.manualScore(id, request, currentPrincipal()));
    }

    /** 学生对已复核评价发起申诉：随机改派其他教师复评。 */
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUBMISSION_UPLOAD + "')")
    @PostMapping("/{id}/appeal")
    public Result<EvaluationAppealService.AppealResult> appeal(
            @PathVariable long id,
            @Valid @RequestBody EvaluationAppealRequest request) {
        return Result.success(evaluationAppealService.appeal(id, request.getReason(), currentPrincipal()));
    }

    /** 当前主体（JWT过滤器装配；未登录时此路径不可达） */
    private LoginPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new com.aies.common.BusinessException(com.aies.common.ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
