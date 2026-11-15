package com.example.orderservice;

import com.example.orderservice.dto.ApiException;
import com.example.orderservice.dto.AuthValidationResult;
import com.example.orderservice.dto.ProductInfo;
import com.example.orderservice.repository.OrderItemRepository;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.AuthServiceClient;
import com.example.orderservice.service.ProductServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử API Order Service. Auth Service và Product Service được giả lập (mock)
 * để test chạy độc lập, không cần bật 2 service kia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:order-api-test")
class OrderApiTests {

    private static final String TOKEN = "Bearer test-token";

    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderItemRepository items;

    @MockitoBean
    private AuthServiceClient authService;

    @MockitoBean
    private ProductServiceClient productService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        items.deleteAll();
        orders.deleteAll();
        when(authService.validateTokenWithAuthService(anyString()))
                .thenReturn(new AuthValidationResult(false, "Token không hợp lệ hoặc đã hết hạn"));
        when(authService.validateTokenWithAuthService(TOKEN))
                .thenReturn(new AuthValidationResult(true, "Token hợp lệ", "admin", 1));
        when(productService.getProduct(eq(1), anyString()))
                .thenReturn(new ProductInfo(1, "Laptop", new BigDecimal("1000.00"), 10));
        when(productService.getProduct(eq(2), anyString()))
                .thenReturn(new ProductInfo(2, "Chuột", new BigDecimal("50.50"), 5));
        when(productService.getProduct(eq(99), anyString()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm có ID = 99"));
    }

    @Test
    void rejectsRequestsWithoutValidToken() throws Exception {
        assertEquals(401, send("GET", "/orders", null, null).statusCode());
        assertEquals(401, send("GET", "/order_items", null, "Bearer wrong").statusCode());
    }

    @Test
    void createsOrderAndCalculatesTotals() throws Exception {
        JsonNode order = createOrder("[{\"product_id\":1,\"quantity\":2},{\"product_id\":2,\"quantity\":3}]");

        assertEquals("pending", order.get("status").asString());
        assertEquals(0, new BigDecimal("2151.50").compareTo(order.get("total_amount").decimalValue()));
        assertEquals(2, order.get("items").size());
        assertEquals("Chuột", order.get("items").get(1).get("product_name").asString());
        assertEquals(0, new BigDecimal("151.50").compareTo(order.get("items").get(1).get("total_price").decimalValue()));

        var get = send("GET", "/orders/" + order.get("id").asInt(), null, TOKEN);
        assertEquals(200, get.statusCode());
    }

    @Test
    void rejectsOrderWhenStockIsInsufficientOrProductMissing() throws Exception {
        var tooMany = send("POST", "/orders", orderBody("[{\"product_id\":2,\"quantity\":3},{\"product_id\":2,\"quantity\":3}]"), TOKEN);
        assertEquals(409, tooMany.statusCode());
        assertTrue(tooMany.body().contains("không đủ hàng"));

        assertEquals(404, send("POST", "/orders", orderBody("[{\"product_id\":99,\"quantity\":1}]"), TOKEN).statusCode());
        assertEquals(400, send("POST", "/orders", orderBody("[]"), TOKEN).statusCode());
        assertEquals(400, send("POST", "/orders", orderBody("[{\"product_id\":1,\"quantity\":0}]"), TOKEN).statusCode());
        assertEquals(0, orders.count());
    }

    @Test
    void updatesStatusWithValidTransitionsOnly() throws Exception {
        int id = createOrder("[{\"product_id\":1,\"quantity\":1}]").get("id").asInt();

        assertEquals(400, send("PUT", "/orders/" + id, "{\"status\":\"shipping\"}", TOKEN).statusCode());
        var ok = send("PUT", "/orders/" + id, "{\"status\":\"completed\"}", TOKEN);
        assertEquals(200, ok.statusCode());
        assertEquals("completed", json.readTree(ok.body()).get("data").get("status").asString());
        assertEquals(409, send("PUT", "/orders/" + id, "{\"status\":\"cancelled\"}", TOKEN).statusCode());
        assertEquals(409, send("POST", "/order_items",
                "{\"order_id\":" + id + ",\"product_id\":2,\"quantity\":1}", TOKEN).statusCode());
    }

    @Test
    void orderItemCrudKeepsOrderTotalInSync() throws Exception {
        int orderId = createOrder("[{\"product_id\":1,\"quantity\":1}]").get("id").asInt();

        var created = send("POST", "/order_items", "{\"order_id\":" + orderId + ",\"product_id\":2,\"quantity\":2}", TOKEN);
        assertEquals(201, created.statusCode());
        int itemId = json.readTree(created.body()).get("data").get("id").asInt();
        assertTotal(orderId, "1101.00");

        assertEquals(409, send("PUT", "/order_items/" + itemId, "{\"quantity\":6}", TOKEN).statusCode());
        assertEquals(200, send("PUT", "/order_items/" + itemId, "{\"quantity\":4}", TOKEN).statusCode());
        assertTotal(orderId, "1202.00");

        assertEquals(200, send("GET", "/order_items/" + itemId, null, TOKEN).statusCode());
        assertEquals(2, json.readTree(send("GET", "/order_items?order_id=" + orderId, null, TOKEN).body()).get("data").size());

        assertEquals(200, send("DELETE", "/order_items/" + itemId, null, TOKEN).statusCode());
        assertEquals(404, send("GET", "/order_items/" + itemId, null, TOKEN).statusCode());
        assertTotal(orderId, "1000.00");
    }

    @Test
    void deletingOrderRemovesItsItems() throws Exception {
        int id = createOrder("[{\"product_id\":1,\"quantity\":1},{\"product_id\":2,\"quantity\":1}]").get("id").asInt();

        assertEquals(200, send("DELETE", "/orders/" + id, null, TOKEN).statusCode());
        assertEquals(404, send("GET", "/orders/" + id, null, TOKEN).statusCode());
        assertEquals(404, send("DELETE", "/orders/" + id, null, TOKEN).statusCode());
        assertEquals(0, items.count());
    }

    private void assertTotal(int orderId, String expected) throws Exception {
        JsonNode order = json.readTree(send("GET", "/orders/" + orderId, null, TOKEN).body()).get("data");
        assertEquals(0, new BigDecimal(expected).compareTo(order.get("total_amount").decimalValue()));
    }

    private JsonNode createOrder(String itemsJson) throws Exception {
        var response = send("POST", "/orders", orderBody(itemsJson), TOKEN);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("data");
    }

    private String orderBody(String itemsJson) {
        return "{\"customer_name\":\"Nguyễn Văn A\",\"customer_email\":\"a@gmail.com\",\"items\":" + itemsJson + "}";
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) builder.header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
