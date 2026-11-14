package com.baolong.wsw.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理：把各类异常收敛成统一响应，前端只认 code。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> onBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> onValid(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        // 只把中文提示带给用户：拼上字段名会显示成「realName 请填写真实姓名」，
        // 既暴露内部字段名，又不好读。
        return Result.fail(ResultCode.BAD_REQUEST, messageOf(first));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> onBind(BindException e) {
        return Result.fail(ResultCode.BAD_REQUEST, messageOf(e.getBindingResult().getFieldError()));
    }

    /** 校验失败的提示语：优先取注解里的中文 message，兜底给一句通用文案 */
    private static String messageOf(FieldError error) {
        if (error == null || error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()) {
            return "参数不合法";
        }
        return error.getDefaultMessage();
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> onParam(Exception e) {
        log.warn("参数异常: {}", e.getMessage());
        // 把「哪个字段、为什么」带出去，否则前端只能看到一句「请求参数不合法」，
        // 时间格式这类问题要翻服务端日志才能定位。
        String detail = e.getMessage();
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            detail = mismatch.getName() + " 类型不匹配";
        } else if (e instanceof MissingServletRequestParameterException missing) {
            detail = "缺少参数 " + missing.getParameterName();
        } else if (detail != null && detail.contains("LocalDateTime")) {
            detail = "时间格式无法解析，请用 yyyy-MM-dd HH:mm:ss 或 ISO-8601";
        }
        return Result.fail(ResultCode.BAD_REQUEST, "请求参数不合法：" + detail);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> onNotFound(NoHandlerFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 文件超过 multipart 上限。
     * <p>这个异常在进 Controller 之前就抛了，不单独接住的话会落到下面的兜底，
     * 用户只能看到「服务器开小差了」，完全猜不到是文件太大。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> onUploadTooLarge(MaxUploadSizeExceededException e) {
        return Result.fail(ResultCode.BAD_REQUEST, "文件太大了，单张图片请控制在 8MB 以内");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> onOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(ResultCode.SERVER_ERROR, "服务器开小差了，请稍后再试");
    }
}
