package com.group11.aievaluate.controller;

import com.group11.aievaluate.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 文件模块异常处理：统一返回主项目的 Result 格式（code/message/data）。
 *
 * <p>通过 assignableTypes 限定只作用于文件相关 Controller，
 * 避免影响其他业务接口的异常处理与返回格式。</p>
 *
 * <p>multipart 大小超限的异常发生在 handler 匹配之前，由
 * {@link MultipartExceptionHandler} 负责。</p>
 */
@RestControllerAdvice(assignableTypes = {
        UploadController.class,
        DownloadController.class,
        FileController.class
})
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> badRequest(IllegalArgumentException e) {
        return Result.error(e.getMessage());
    }
}
