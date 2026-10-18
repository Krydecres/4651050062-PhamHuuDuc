package com.example.productservice.filter;

import com.example.productservice.dto.AuthValidationResult;
import com.example.productservice.service.AuthServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor kiểm tra xác thực cho mọi yêu cầu gọi vào API Quản lý sản phẩm (/products/**).
 * Thực hiện xác thực thông qua Auth Service đã xây dựng ở Bài thực hành số 2.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthServiceClient authServiceClient;

    public AuthInterceptor(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Bỏ qua các pre-flight request CORS OPTIONS nếu có
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Lấy Token từ Header: hỗ trợ "Authorization: Bearer <token>" hoặc "token: <token>"
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            String tokenHeader = request.getHeader("token");
            if (tokenHeader != null && !tokenHeader.isBlank()) {
                authorizationHeader = "Bearer " + tokenHeader.trim();
            }
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED,
                    "Yêu cầu xác thực: Vui lòng cung cấp JWT Token trong header 'Authorization: Bearer <token>'");
            return false;
        }

        // Gọi sang Auth Service (BTH2) để kiểm tra tính hợp lệ
        AuthValidationResult result = authServiceClient.validateTokenWithAuthService(authorizationHeader);

        if (!result.isSuccess()) {
            HttpStatus status = HttpStatus.UNAUTHORIZED;
            if (result.getMessage() != null && result.getMessage().startsWith(AuthServiceClient.AUTH_UNAVAILABLE_PREFIX)) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
            sendErrorResponse(response, status, result.getMessage());
            return false;
        }

        // Lưu thông tin người dùng đã xác thực vào request để controller có thể sử dụng nếu cần
        request.setAttribute("authenticatedUser", result.getUserName());
        request.setAttribute("authenticatedUserId", result.getIdUser());

        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = String.format("{\"success\":false,\"message\":\"%s\"}", message.replace("\"", "\\\""));
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
