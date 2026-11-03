package com.group11.aievaluate.controller;

import com.group11.aievaluate.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * multipart 解析阶段异常的兜底处理。
 *
 * <p>文件超出大小限制时，异常在 Controller 匹配之前由 multipart 解析器抛出，
 * 此时 DispatcherServlet 还没有 handler，带 assignableTypes 限定的 advice 无法命中，
 * 因此这里使用不带限定条件的 advice，统一转成主项目的 Result 格式。</p>
 *
 * <p>MultipartException 只会出现在文件上传请求上，不会影响其他业务接口。</p>
 */
@RestControllerAdvice
public class MultipartExceptionHandler {

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> multipartTooLarge(Exception e) {
        return Result.error("请求体超过大小限制：请调小分片大小或减少批量文件数（上限见 application.yml）");
    }
}
