package com.simpissuehub.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ApiError error = new ApiError(HttpStatus.METHOD_NOT_ALLOWED.value(), "METHOD_NOT_ALLOWED",
                "当前资源不支持该 HTTP 方法", request.getRequestURI(), Instant.now(), null);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null && !exception.getSupportedHttpMethods().isEmpty()) {
            builder.allow(exception.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return builder.body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, code(status), exception.getReason() == null ? status.getReasonPhrase() : exception.getReason(), request, null);
    }

    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
    ResponseEntity<ApiError> notLogin(cn.dev33.satoken.exception.NotLoginException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求字段校验失败", request, fields);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数或请求体格式不正确", request, null);
    }

    @ExceptionHandler({OptimisticLockException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> optimistic(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "VERSION_CONFLICT", "资源已被其他请求修改，请刷新后重试", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "请求与现有数据冲突", request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "上传文件超过允许的大小限制", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unknown(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器暂时无法处理请求", request, null);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message, request.getRequestURI(), Instant.now(), fields));
    }
    private String code(HttpStatus status) { return switch (status) { case UNAUTHORIZED -> "UNAUTHENTICATED"; case FORBIDDEN -> "FORBIDDEN"; case NOT_FOUND -> "NOT_FOUND"; case CONFLICT -> "CONFLICT"; case BAD_REQUEST -> "INVALID_REQUEST"; default -> "HTTP_" + status.value(); }; }
    public record ApiError(int status, String code, String message, String path, Instant timestamp, Map<String, String> fields) {}
}
