package com.aies.exception;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiExceptionHandler 单测（S-03/D-17：401xx→401 / 40301→403 / 30010→429 / 30000+40006→400 /
 * 40400→404 / 50000→500 / 其余业务码→200；Security异常401+403 / 唯一冲突30012 /
 * 参数校验三径30000 / 上传超限30003 / 404同形 / 兜底500）。
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /** 例1 AuthenticationException → HTTP 401 + 40101（S-02统一文案） */
    @Test
    void authenticationExceptionReturns401With40101() {
        ResponseEntity<Result<Void>> response =
                handler.handleAuthentication(new BadCredentialsException("bad credentials"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
    }

    /** 例2 AccessDeniedException → HTTP 403 + 40301（S-06 @PreAuthorize拦截） */
    @Test
    void accessDeniedReturns403With40301() {
        ResponseEntity<Result<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    /** 例3 业务码分段映射：401xx→401 / 40301→403 / 30010→429 / 30000+40006→400 / 40400→404 / 50000→500 / 其余→200 */
    @Test
    void businessExceptionMapsHttpStatusBySegment() {
        record Segment(ErrorCode error, int httpStatus) {
        }
        List<Segment> segments = List.of(
                new Segment(ErrorCode.UNAUTHORIZED, 401),
                new Segment(ErrorCode.ACCOUNT_LOCKED, 401),
                new Segment(ErrorCode.FORBIDDEN, 403),
                new Segment(ErrorCode.CODE_SEND_TOO_FREQUENT, 429),
                new Segment(ErrorCode.PARAM_ERROR, 400),
                new Segment(ErrorCode.ILLEGAL_SORT_FIELD, 400),
                new Segment(ErrorCode.NOT_FOUND, 404),
                new Segment(ErrorCode.SYSTEM_ERROR, 500),
                new Segment(ErrorCode.EMAIL_CODE_INVALID, 200),
                new Segment(ErrorCode.METRIC_WEIGHT_SUM_INVALID, 200),
                new Segment(ErrorCode.FILE_TOO_LARGE, 200));
        for (Segment segment : segments) {
            ResponseEntity<Result<Void>> response =
                    handler.handleBusiness(new BusinessException(segment.error()));
            assertThat(response.getStatusCode().value())
                    .as(segment.error().name()).isEqualTo(segment.httpStatus());
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode())
                    .as(segment.error().name()).isEqualTo(segment.error().getCode());
        }
    }

    /** 例4 BusinessException自定义文案透传（40101同code承载不同文案的S-02防枚举场景） */
    @Test
    void businessExceptionCustomMessagePassthrough() {
        ResponseEntity<Result<Void>> response = handler.handleBusiness(
                new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("账号或密码错误");
    }

    /** 例5 DuplicateKeyException → HTTP 200 + 30012学号已注册（唯一索引冲突兜底） */
    @Test
    void duplicateKeyReturnsUsernameRegistered() {
        ResponseEntity<Result<Void>> response =
                handler.handleDuplicateKey(new DuplicateKeyException("uk_sys_user_username"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.USERNAME_REGISTERED.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.USERNAME_REGISTERED.getMessage());
    }

    /** 例6 BindException → HTTP 400 + 30000，取首个字段错误的默认文案 */
    @Test
    void bindExceptionReturnsFirstFieldErrorMessage() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "邮箱格式非法"));

        ResponseEntity<Result<Void>> response = handler.handleBind(new BindException(bindingResult));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("邮箱格式非法");
    }

    /** 例7 ConstraintViolationException → HTTP 400 + 30000默认文案 */
    @Test
    void constraintViolationReturnsParamError() {
        ResponseEntity<Result<Void>> response = handler.handleConstraintViolation(
                new ConstraintViolationException("violation", Collections.emptySet()));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getMessage());
    }

    /** 例8 请求体不可读与缺参 → HTTP 400 + 30000统一JSON文案 */
    @Test
    void unreadableBodyAndMissingParamReturnJsonFormatMessage() {
        ResponseEntity<Result<Void>> unreadable = handler.handleBadRequest(
                new HttpMessageNotReadableException("bad json"));
        ResponseEntity<Result<Void>> missing = handler.handleBadRequest(
                new MissingServletRequestParameterException("page", "long"));

        assertThat(unreadable.getStatusCode().value()).isEqualTo(400);
        assertThat(unreadable.getBody()).isNotNull();
        assertThat(unreadable.getBody().getMessage()).isEqualTo("请求体格式错误：请确认是 UTF-8 编码的合法 JSON");
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
    }

    /** 例9 IllegalArgumentException → HTTP 400 + 30000 + 原始消息透传 */
    @Test
    void illegalArgumentReturnsBadRequestWithOriginalMessage() {
        ResponseEntity<Result<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("非法排序字段"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("非法排序字段");
    }

    /** 例10 MultipartException → HTTP 400 + 30000分片超限文案（批量上传兜底） */
    @Test
    void multipartExceptionReturnsSizeLimitMessage() {
        ResponseEntity<Result<Void>> response =
                handler.handleMultipart(new MultipartException("upload too large"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("请求体超过大小限制：请调小分片大小或减少批量文件数（上限见 application.yml）");
    }

    /** 例11 MaxUploadSizeExceededException → HTTP 200 + 30003（UC-16提交超限与应用层同径） */
    @Test
    void maxUploadSizeReturnsFileTooLarge() {
        ResponseEntity<Result<Void>> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getCode());
    }

    /** 例12 NoResourceFoundException → HTTP 404 + 40400（D-17业务级404同形） */
    @Test
    void noResourceFoundReturns404With40400() {
        ResponseEntity<Result<Void>> response =
                handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/unknown"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    /** 例13 兜底Exception → HTTP 500 + 50000（不向调用方泄露内部堆栈细节） */
    @Test
    void unexpectedExceptionReturnsSystemError() {
        ResponseEntity<Result<Void>> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }
}
