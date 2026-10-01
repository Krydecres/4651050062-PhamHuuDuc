package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.JwtService;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
public class LoginController {

    private final JwtService jwtService;
    private final UserService userService;

    public LoginController(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    // Endpoint đăng nhập theo yêu cầu đề bài:
    // - Url: localhost:[port]/ (hỗ trợ thêm cả /login)
    // - Tham số: userName, password (đã mã hoá base64 hoặc Md5 tại client)
    // - Hỗ trợ nhận qua JSON Body hoặc Form/Query params
    @PostMapping({"", "/", "/login"})
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) LoginRequest request,
            @RequestParam(value = "userName", required = false) String paramUserName,
            @RequestParam(value = "username", required = false) String paramUsernameLower,
            @RequestParam(value = "password", required = false) String paramPassword) {

        String userName = (request != null && request.userName() != null)
                ? request.userName()
                : (paramUserName != null ? paramUserName : paramUsernameLower);

        String password = (request != null && request.password() != null)
                ? request.password()
                : paramPassword;

        if (userName == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Thiếu tham số userName hoặc password"));
        }

        Optional<User> userOpt = userService.findByUserName(userName);

        // Kiểm tra mật khẩu (hỗ trợ Base64, MD5 và Plain-text)
        if (userOpt.isEmpty() || !userService.checkPassword(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Username hoặc password không đúng"));
        }

        User user = userOpt.get();
        // Sinh JWT token
        String token = jwtService.generateToken(user.getIdUser(), user.getUserName());
        // Lưu token vào bảng User theo Phụ lục
        userService.updateToken(user.getUserName(), token);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đăng nhập thành công",
                "idUser", user.getIdUser(),
                "userName", user.getUserName(),
                "token", token));
    }

    public record LoginRequest(
            @JsonAlias({"username", "userName", "email"})
            String userName,
            String password) {
    }
}