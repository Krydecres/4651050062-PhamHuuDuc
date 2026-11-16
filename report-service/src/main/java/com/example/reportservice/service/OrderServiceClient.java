package com.example.reportservice.service;

import com.example.reportservice.dto.ApiException;
import com.example.reportservice.dto.ApiResponse;
import com.example.reportservice.dto.OrderInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client gọi sang Order Service (Bài thực hành số 4) để lấy dữ liệu đơn hàng.
 * Report Service KHÔNG đọc CSDL orderdb mà gọi REST API, chuyển tiếp token của người dùng:
 *     GET http://localhost:8082/orders        (mọi đơn hàng, kèm items)
 *     GET http://localhost:8082/orders/{id}   (một đơn hàng, kèm items)
 */
@Service
public class OrderServiceClient {

    private final String orderServiceUrl;
    private final RestClient restClient;

    public OrderServiceClient(
            @Value("${order.service.url:http://localhost:8082}") String orderServiceUrl,
            @Value("${order.service.timeout-ms:5000}") long timeoutMs) {
        this.orderServiceUrl = orderServiceUrl.endsWith("/")
                ? orderServiceUrl.substring(0, orderServiceUrl.length() - 1)
                : orderServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /** Lấy một đơn hàng. @throws ApiException 404 nếu không tồn tại, 503 nếu Order Service không phản hồi */
    public OrderInfo getOrder(Integer orderId, String authorizationHeader) {
        ApiResponse<OrderInfo> response = call(() -> restClient.get()
                .uri(orderServiceUrl + "/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<OrderInfo>>() {}),
                "Không tìm thấy đơn hàng có ID = " + orderId + " trong Order Service");

        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng có ID = " + orderId + " trong Order Service");
        }
        return response.getData();
    }

    /** Lấy tất cả đơn hàng (kèm items) */
    public List<OrderInfo> getAllOrders(String authorizationHeader) {
        ApiResponse<List<OrderInfo>> response = call(() -> restClient.get()
                .uri(orderServiceUrl + "/orders")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<OrderInfo>>>() {}),
                "Order Service không tìm thấy danh sách đơn hàng");

        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    private <T> T call(Supplier<T> request, String notFoundMessage) {
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Order Service từ chối token: Token không hợp lệ hoặc đã hết hạn");
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Order Service trả về lỗi " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối đến Order Service tại " + orderServiceUrl
                            + ". Vui lòng khởi động Dịch vụ quản lý đơn hàng (Bài thực hành 4) ở cổng 8082!");
        } catch (HttpServerErrorException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Order Service đang gặp lỗi (" + ex.getStatusCode().value() + ")");
        }
    }
}
