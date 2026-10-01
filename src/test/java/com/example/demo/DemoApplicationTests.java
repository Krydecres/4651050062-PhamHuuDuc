package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.service.JwtService;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("1. Đăng nhập thành công với mật khẩu mã hoá Base64 (MTIzNDU2)")
    void testLoginWithBase64Password() throws Exception {
        String jsonRequest = """
                {
                    "userName": "admin",
                    "password": "MTIzNDU2"
                }
                """;

        MvcResult result = mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userName").value("admin"))
                .andExpect(jsonPath("$.idUser").value(1))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = responseNode.get("token").asText();

        // Kiểm tra token đã được lưu vào bảng User theo Phụ lục
        User user = userService.findByUserName("admin").orElseThrow();
        assertThat(user.getToken()).isEqualTo(token);

        // Kiểm tra JWT claims chứa đúng idUser và subject
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.extractIdUser(token)).isEqualTo(1);
    }

    @Test
    @DisplayName("2. Đăng nhập thành công với mật khẩu băm MD5 (e10adc3949ba59abbe56e057f20f883e)")
    void testLoginWithMd5Password() throws Exception {
        String jsonRequest = """
                {
                    "userName": "admin",
                    "password": "e10adc3949ba59abbe56e057f20f883e"
                }
                """;

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("3. Đăng nhập với mật khẩu chuỗi trần thông thường (123456)")
    void testLoginWithPlainTextPassword() throws Exception {
        String jsonRequest = """
                {
                    "userName": "admin",
                    "password": "123456"
                }
                """;

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("4. Đăng nhập thất bại khi sai mật khẩu")
    void testLoginFailureWrongPassword() throws Exception {
        String jsonRequest = """
                {
                    "userName": "admin",
                    "password": "wrong_password"
                }
                """;

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Middleware chặn API /hello khi không có token (401)")
    void testHelloWorldWithoutToken() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("6. Middleware chặn API /hello khi token không hợp lệ (401)")
    void testHelloWorldWithInvalidToken() throws Exception {
        mockMvc.perform(get("/hello")
                        .header("Authorization", "Bearer invalid_token_12345"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    @DisplayName("7. Middleware cho phép API /hello khi có JWT token hợp lệ (200)")
    void testHelloWorldWithValidToken() throws Exception {
        String validToken = jwtService.generateToken(1, "admin");

        MvcResult result = mockMvc.perform(get("/hello")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("Hello World!");
    }

    @Test
    @DisplayName("8. API /auth xác thực token thành công")
    void testAuthEndpointValidToken() throws Exception {
        String validToken = jwtService.generateToken(1, "admin");

        // Gọi POST /auth với header Authorization Bearer
        mockMvc.perform(post("/auth")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token hợp lệ"))
                .andExpect(jsonPath("$.userName").value("admin"))
                .andExpect(jsonPath("$.idUser").value(1));

        // Gọi POST /auth với JSON body { "token": "..." }
        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + validToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Gọi GET /auth với query param ?token=
        mockMvc.perform(get("/auth").param("token", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("9. API /auth báo lỗi khi token sai hoặc thiếu")
    void testAuthEndpointInvalidOrMissingToken() throws Exception {
        // Thiếu token -> 400
        mockMvc.perform(post("/auth"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Token sai -> 401
        mockMvc.perform(post("/auth")
                        .header("Authorization", "Bearer wrong_token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
