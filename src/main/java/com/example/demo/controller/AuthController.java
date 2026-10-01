package com.example.demo.controller;

import com.example.demo.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // API xác thực token theo yêu cầu đề bài: localhost:[port]/auth
    // Hỗ trợ cả phương thức GET và POST.
    // Linh hoạt nhận token qua:
    // 1. Header Authorization (Bearer <token> hoặc <token>)
    // 2. Header token
    // 3. Query Param ?token=
    // 4. Request Body { "token": "..." }
    @RequestMapping(value = {"/auth", "/auth/"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> auth(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String paramToken,
            @RequestBody(required = false) Map<String, Object> body) {

        String token = null;

        // 1. Lấy từ header Authorization
        if (authorization != null && !authorization.isBlank()) {
            if (authorization.startsWith("Bearer ")) {
                token = authorization.substring(7).trim();
            } else {
                token = authorization.trim();
            }
        }

        // 2. Lấy từ header token
        if (token == null && headerToken != null && !headerToken.isBlank()) {
            token = headerToken.trim();
        }

        // 3. Lấy từ query param
        if (token == null && paramToken != null && !paramToken.isBlank()) {
            token = paramToken.trim();
        }

        // 4. Lấy từ request body JSON
        if (token == null && body != null && body.containsKey("token")) {
            Object tokenObj = body.get("token");
            if (tokenObj != null) {
                token = tokenObj.toString().trim();
            }
        }

        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Missing token"));
        }

        if (!jwtService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Token không hợp lệ hoặc đã hết hạn"));
        }

        String username = jwtService.extractUsername(token);
        Integer idUser = jwtService.extractIdUser(token);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token hợp lệ",
                "userName", username,
                "idUser", idUser != null ? idUser : 1));
    }
}