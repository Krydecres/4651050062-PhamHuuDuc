package com.example.demo.filter;

import com.example.demo.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Middleware (Filter) xác thực JWT token theo yêu cầu đề bài.
 * Thực hiện xác thực token cho API in dòng "Hello World" ở bài thực hành số 1.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        // Nếu request có mang theo header Authorization dạng Bearer token
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();

            // Nếu token sai định dạng hoặc hết hạn -> chặn ngay lập tức với lỗi 401
            if (!jwtService.validateToken(token)) {
                sendUnauthorizedError(response, "Invalid or expired token");
                return;
            }

            // Token hợp lệ: Trích xuất username và thiết lập thông tin xác thực vào SecurityContext
            String username = jwtService.extractUsername(token);
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    username,
                    null,
                    List.of());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Chuyển tiếp request cho filter tiếp theo trong chuỗi
        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\"}", message));
    }
}