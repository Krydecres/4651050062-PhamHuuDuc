package com.example.reportservice.service;

import com.example.reportservice.dto.ApiException;
import com.example.reportservice.dto.ApiResponse;
import com.example.reportservice.dto.ProductInfo;
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

/**
 * Client gọi sang Product Service (Bài thực hành số 3) để lấy thông tin sản phẩm
 * (tên, giá, số lượng tồn kho) khi lập báo cáo.
 *
 * Theo SOA, Report Service KHÔNG đọc trực tiếp CSDL productdb mà gọi qua REST API:
 *     GET http://localhost:8081/products/{id}
 *     Authorization: Bearer <token>   (chuyển tiếp token của người dùng)
 */
@Service
public class ProductServiceClient {

    private final String productServiceUrl;
    private final RestClient restClient;

    public ProductServiceClient(
            @Value("${product.service.url:http://localhost:8081}") String productServiceUrl,
            @Value("${product.service.timeout-ms:5000}") long timeoutMs) {
        this.productServiceUrl = productServiceUrl.endsWith("/")
                ? productServiceUrl.substring(0, productServiceUrl.length() - 1)
                : productServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Lấy thông tin sản phẩm theo ID từ Product Service.
     *
     * @throws ApiException 404 nếu sản phẩm không tồn tại, 503 nếu Product Service không phản hồi
     */
    public ProductInfo getProduct(Integer productId, String authorizationHeader) {
        try {
            ApiResponse<ProductInfo> response = restClient.get()
                    .uri(productServiceUrl + "/products/{id}", productId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<ProductInfo>>() {});

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm có ID = " + productId + " trong Product Service");
            }
            return response.getData();

        } catch (HttpClientErrorException.NotFound ex) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm có ID = " + productId + " trong Product Service");
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Product Service từ chối token: Token không hợp lệ hoặc đã hết hạn");
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Product Service trả về lỗi " + ex.getStatusCode().value() + " khi lấy sản phẩm ID = " + productId);
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối đến Product Service tại " + productServiceUrl
                            + ". Vui lòng khởi động Dịch vụ quản lý sản phẩm (Bài thực hành 3) ở cổng 8081!");
        } catch (HttpServerErrorException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Product Service đang gặp lỗi (" + ex.getStatusCode().value() + ")");
        }
    }
}
