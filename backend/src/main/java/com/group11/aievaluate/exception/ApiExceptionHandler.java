package com.group11.aievaluate.exception;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.group11.aievaluate.common.BusinessException;
import com.group11.aievaluate.common.ErrorCode;
import com.group11.aievaluate.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局统一异常处理。
 *
 * <p>所有异常都转换为统一的 Result{code, message, data} 结构：</p>
 * <ul>
 *   <li>未登录：HTTP 401 + code 40100</li>
 *   <li>无权限：HTTP 403 + code 40300</li>
 *   <li>业务错误（密码错误等）：HTTP 200 + 对应业务 code</li>
 *   <li>参数错误：HTTP 400 + code 40000</li>
 *   <li>未知异常：HTTP 500 + code 50000</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /* ==================== 1. 登录 / 权限 ==================== */

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLogin(NotLoginException e) {
        String message = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN -> "未提供登录凭证，请先登录";
            case NotLoginException.INVALID_TOKEN -> "登录凭证无效，请重新登录";
            case NotLoginException.TOKEN_TIMEOUT -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "账号已在另一处登录";
            case NotLoginException.KICK_OUT -> "账号已被强制下线";
            case NotLoginException.TOKEN_FREEZE -> "账号已被封禁";
            case NotLoginException.NO_PREFIX -> "登录凭证格式错误";
            default -> "未登录，请先登录";
        };
        return Result.error(ErrorCode.UNAUTHORIZED.getCode(), message);
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNoPermission(Exception e) {
        String message = ErrorCode.FORBIDDEN.getMessage();
        if (e instanceof NotPermissionException permissionException
                && permissionException.getPermission() != null) {
            message = "无操作权限，需要权限：" + permissionException.getPermission();
        } else if (e instanceof NotRoleException roleException && roleException.getRole() != null) {
            message = "无操作权限，需要角色：" + roleException.getRole();
        }
        return Result.error(ErrorCode.FORBIDDEN.getCode(), message);
    }

    @ExceptionHandler(DisableServiceException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleDisabled(DisableServiceException e) {
        return Result.error(ErrorCode.FORBIDDEN.getCode(), "账号已被封禁");
    }

    /* ==================== 2. 业务异常 ==================== */

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return Result.error(ErrorCode.USER_EXISTS.getCode(), ErrorCode.USER_EXISTS.getMessage());
    }

    /* ==================== 3. 参数校验 ==================== */

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequest(Exception e) {
        // 打印具体解析原因（例如 JSON 字段类型不匹配、请求体编码不是 UTF-8），便于排查
        log.warn("请求参数解析失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(),
                "请求体格式错误：请确认是 UTF-8 编码的合法 JSON");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /* ==================== 4. 文件上传 / 404 / 兜底 ==================== */

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMultipart(MultipartException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(),
                "请求体超过大小限制：请调小分片大小或减少批量文件数（上限见 application.yml）");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.error(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
