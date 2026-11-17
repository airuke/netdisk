package com.airuike.netdisk.backend.exception;

import com.airuike.netdisk.backend.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");

        return createResponseEntity(
                ApiResponse.error(400, message),
                headers,
                status,
                request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (body instanceof ApiResponse<?>) {
            return new ResponseEntity<>(body, headers, statusCode);
        }

        String message = switch (statusCode.value()) {
            case 400 -> "请求参数错误";
            case 404 -> "接口或资源不存在";
            case 413 -> "单个文件不能超过 200 MB";
            case 416 -> "文件读取范围无效";
            case 405 -> "请求方法不支持";
            default -> statusCode.is5xxServerError()
                    ? "服务器内部错误"
                    : "请求失败";
        };

        return new ResponseEntity<>(
                ApiResponse.error(statusCode.value(), message),
                headers,
                statusCode);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {

        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getStatus().value(),
                        exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException exception) {

        // 数据库等内部故障应返回500，而不是误报密码错误。
        if (exception instanceof AuthenticationServiceException) {
            return handleUnexpectedException(exception);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "用户名或密码错误，或账号已禁用"));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "此位置已有同名文件或文件夹"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception) {

        log.error("接口处理发生未预期异常", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}