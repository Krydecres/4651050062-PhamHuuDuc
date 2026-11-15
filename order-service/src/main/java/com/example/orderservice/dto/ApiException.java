package com.example.orderservice.dto;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ kèm mã HTTP (404 không tìm thấy, 409 hết hàng, 503 service khác không phản hồi...).
 * GlobalExceptionHandler chuyển thành JSON { success: false, message }.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
