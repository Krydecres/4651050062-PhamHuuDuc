package com.example.productservice.service;

import com.example.productservice.dto.AuthValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client gọi sang Auth Service (Bài thực hành số 2) để kiểm tra token.
 *
 * Theo nguyên tắc SOA, Product Service không tự giải mã JWT mà hỏi Auth Service:
 *     GET http://localhost:8080/auth
 *     Authorization: Bearer <token>
 * Auth Service (AuthController bài 2) trả về:
 *     200 { success: true,  userName, idUser }  -> token hợp lệ
 *     401 { success: false, message }           -> token sai / hết hạn
 *     400 { success: false, message }           -> thiếu token
 */
@Service
public class AuthServiceClient {

    public static final String AUTH_UNAVAILABLE_PREFIX = "Không thể kết nối đến Auth Service";

    private final String authServiceUrl;
    private final RestClient restClient;

    public AuthServiceClient(
            @Value("${auth.service.url:http://localhost:8080/auth}") String authServiceUrl,
            @Value("${auth.service.timeout-ms:5000}") long timeoutMs) {
        this.authServiceUrl = authServiceUrl;

        // Đặt thời gian chờ để Product Service không bị treo khi Auth Service chậm/không phản hồi
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Gửi token sang Auth Service để kiểm tra tính hợp lệ.
     *
     * @param rawAuthHeader giá trị header Authorization client gửi tới Product Service
     * @return kết quả xác thực do Auth Service trả về
     */
    public AuthValidationResult validateTokenWithAuthService(String rawAuthHeader) {
        if (rawAuthHeader == null || rawAuthHeader.isBlank()) {
            return new AuthValidationResult(false, "Thiếu Bearer Token trong header Authorization");
        }

        try {
            AuthValidationResult result = restClient.get()
                    .uri(authServiceUrl)
                    .header(HttpHeaders.AUTHORIZATION, rawAuthHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AuthValidationResult.class);

            if (result != null && result.isSuccess()) {
                return result;
            }
            String msg = (result != null && result.getMessage() != null)
                    ? result.getMessage()
                    : "Token không hợp lệ hoặc đã hết hạn";
            return new AuthValidationResult(false, msg);

        } catch (HttpClientErrorException ex) {
            // Auth Service trả 4xx (400 thiếu token, 401 token sai/hết hạn, 403 ...)
            return new AuthValidationResult(false,
                    "Xác thực thất bại từ Auth Service: Token không hợp lệ hoặc đã hết hạn");
        } catch (ResourceAccessException ex) {
            // Không kết nối được (Auth Service chưa bật) hoặc quá thời gian chờ
            return new AuthValidationResult(false,
                    AUTH_UNAVAILABLE_PREFIX + " tại " + authServiceUrl
                            + ". Vui lòng khởi động Dịch vụ xác thực (Bài thực hành 2) ở cổng 8080!");
        } catch (HttpServerErrorException ex) {
            return new AuthValidationResult(false,
                    AUTH_UNAVAILABLE_PREFIX + ": Auth Service đang gặp lỗi (" + ex.getStatusCode().value() + ")");
        }
    }
}
