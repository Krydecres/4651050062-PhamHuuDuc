package com.example.demo.service;

import com.example.demo.model.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    // Danh sách lưu trữ người dùng mô phỏng Bảng người dùng (User)
    private final Map<String, User> userTable = new ConcurrentHashMap<>();

    public UserService() {
        // Khởi tạo tài khoản mẫu theo thực tế bài thực hành:
        // IdUser = 1, userName = "admin", password = "123456", token = null
        User defaultUser = new User(1, "admin", "123456", null);
        userTable.put(defaultUser.getUserName(), defaultUser);
    }

    /**
     * Tìm người dùng theo userName
     */
    public Optional<User> findByUserName(String userName) {
        if (userName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userTable.get(userName));
    }

    /**
     * Kiểm tra mật khẩu do client gửi lên:
     * Hỗ trợ đối soát theo yêu cầu đề bài:
     * 1. Mật khẩu mã hoá Base64 tại client (ví dụ "123456" -> "MTIzNDU2")
     * 2. Mật khẩu băm MD5 tại client (ví dụ "123456" -> "e10adc3949ba59abbe56e057f20f883e")
     * 3. Mật khẩu dạng chuỗi trần (Plain-text "123456") để thuận tiện khi kiểm thử
     */
    public boolean checkPassword(String clientPassword, String storedPassword) {
        if (clientPassword == null || storedPassword == null) {
            return false;
        }

        // 1. So khớp trực tiếp (Plain-text)
        if (clientPassword.equals(storedPassword)) {
            return true;
        }

        // 2. So khớp nếu client gửi chuỗi mã hoá Base64
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(clientPassword);
            String decodedPassword = new String(decodedBytes, StandardCharsets.UTF_8);
            if (decodedPassword.equals(storedPassword)) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            // Không phải định dạng Base64 hợp lệ, bỏ qua
        }

        // 3. So khớp nếu client gửi mã băm MD5
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(storedPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String expectedMd5 = sb.toString();
            if (expectedMd5.equalsIgnoreCase(clientPassword)) {
                return true;
            }
        } catch (NoSuchAlgorithmException ignored) {
            // Trường hợp không tìm thấy thuật toán MD5
        }

        return false;
    }

    /**
     * Cập nhật Token cho người dùng vào bảng User
     */
    public void updateToken(String userName, String token) {
        User user = userTable.get(userName);
        if (user != null) {
            user.setToken(token);
        }
    }
}
