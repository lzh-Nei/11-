package com.aies.exception;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局统一异常处理——所有异常转换为统一 Result{code, message, data}。
 *
 * <p>HTTP 状态按错误码分段映射（02-02 §九分段语义 + 04-04 S-03）：
 * 401xx→401、40301→403、30010→429（限流）、30000/40006→400、50000→500，其余业务码→200。</p>
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /* ==================== 1. 认证 / 权限（Spring Security） ==================== */

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    /** S-06：@PreAuthorize 拦截 → 403/40301 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    /* ==================== 2. 业务异常（按码映射状态） ==================== */

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return build(statusOf(e.getCode()),
                Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return build(HttpStatus.OK, Result.error(
                ErrorCode.USERNAME_REGISTERED.getCode(), ErrorCode.USERNAME_REGISTERED.getMessage()));
    }

    /* ==================== 3. 参数校验 ==================== */

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return build(HttpStatus.BAD_REQUEST, Result.error(
                ErrorCode.PARAM_ERROR.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return build(HttpStatus.BAD_REQUEST, Result.error(
                ErrorCode.PARAM_ERROR.getCode(), message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        // 打印具体解析原因（例如 JSON 字段类型不匹配、请求体编码不是 UTF-8），便于排查
        log.warn("请求参数解析失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, Result.error(
                ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误：请确认是 UTF-8 编码的合法 JSON"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, Result.error(
                ErrorCode.PARAM_ERROR.getCode(), e.getMessage()));
    }

    /* ==================== 4. 文件上传 / 404 / 兜底 ==================== */

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException e) {
        return build(HttpStatus.BAD_REQUEST, Result.error(
                ErrorCode.PARAM_ERROR.getCode(),
                "请求体超过大小限制：请调小分片大小或减少批量文件数（上限见 application.yml）"));
    }

    /** S-01/UC-16：提交文件超 multipart 上限 → 与应用层 30003 同径（先于 MultipartException 更具体分支） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return build(HttpStatus.OK, Result.error(
                ErrorCode.FILE_TOO_LARGE.getCode(), ErrorCode.FILE_TOO_LARGE.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
    }

    /* ==================== 私有：状态映射与组装 ==================== */

    private HttpStatus statusOf(int code) {
        if (code == ErrorCode.UNAUTHORIZED.getCode() || code == ErrorCode.ACCOUNT_LOCKED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.FORBIDDEN.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.CODE_SEND_TOO_FREQUENT.getCode()) {
            // S-03：限流 → 429
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code == ErrorCode.PARAM_ERROR.getCode() || code == ErrorCode.ILLEGAL_SORT_FIELD.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ErrorCode.NOT_FOUND.getCode()) {
            // D-17：40400 统一 HTTP 404（业务级资源不存在与 NoResourceFoundException 同形）
            return HttpStatus.NOT_FOUND;
        }
        if (code == ErrorCode.SYSTEM_ERROR.getCode()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.OK;
    }

    private ResponseEntity<Result<Void>> build(HttpStatus status, ErrorCode errorCode) {
        return ResponseEntity.status(status)
                .body(Result.error(errorCode.getCode(), errorCode.getMessage()));
    }

    private ResponseEntity<Result<Void>> build(HttpStatus status, Result<Void> body) {
        return ResponseEntity.status(status).body(body);
    }
}
