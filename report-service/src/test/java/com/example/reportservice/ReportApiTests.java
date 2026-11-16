package com.example.reportservice;

import com.example.reportservice.dto.ApiException;
import com.example.reportservice.dto.AuthValidationResult;
import com.example.reportservice.dto.OrderInfo;
import com.example.reportservice.dto.OrderItemInfo;
import com.example.reportservice.dto.ProductInfo;
import com.example.reportservice.repository.OrderReportRepository;
import com.example.reportservice.repository.ProductReportRepository;
import com.example.reportservice.service.AuthServiceClient;
import com.example.reportservice.service.OrderServiceClient;
import com.example.reportservice.service.ProductServiceClient;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử API Report Service. Auth, Product và Order Service được giả lập (mock)
 * để test chạy độc lập.
 *
 * Dữ liệu giả lập:
 *   Sản phẩm 1: Laptop, giá 1000, tồn 10     Sản phẩm 2: Chuột, giá 50, tồn 5
 *   Đơn 1 (completed): Laptop x2, Chuột x3   -> doanh thu 2150
 *   Đơn 2 (completed): Chuột x1              -> doanh thu 50
 *   Đơn 3 (pending)  : Laptop x5             -> không tính
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:report-api-test", "report.default-cost-ratio=0.7"})
class ReportApiTests {

    private static final String TOKEN = "Bearer test-token";

    @LocalServerPort
    private int port;

    @Autowired
    private OrderReportRepository orderReports;

    @Autowired
    private ProductReportRepository productReports;

    @MockitoBean
    private AuthServiceClient authService;

    @MockitoBean
    private ProductServiceClient productService;

    @MockitoBean
    private OrderServiceClient orderService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        productReports.deleteAll();
        orderReports.deleteAll();

        when(authService.validateTokenWithAuthService(anyString()))
                .thenReturn(new AuthValidationResult(false, "Token không hợp lệ hoặc đã hết hạn"));
        when(authService.validateTokenWithAuthService(TOKEN))
                .thenReturn(new AuthValidationResult(true, "Token hợp lệ", "admin", 1));

        when(productService.getProduct(eq(1), anyString())).thenReturn(new ProductInfo(1, "Laptop", new BigDecimal("1000.00"), 10));
        when(productService.getProduct(eq(2), anyString())).thenReturn(new ProductInfo(2, "Chuột", new BigDecimal("50.00"), 5));
        when(productService.getProduct(eq(99), anyString()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm có ID = 99"));

        OrderInfo order1 = new OrderInfo(1, "completed", List.of(
                new OrderItemInfo(1, "Laptop", 2, new BigDecimal("1000.00")),
                new OrderItemInfo(2, "Chuột", 3, new BigDecimal("50.00"))));
        OrderInfo order2 = new OrderInfo(2, "completed", List.of(new OrderItemInfo(2, "Chuột", 1, new BigDecimal("50.00"))));
        OrderInfo order3 = new OrderInfo(3, "pending", List.of(new OrderItemInfo(1, "Laptop", 5, new BigDecimal("1000.00"))));
        when(orderService.getOrder(eq(1), anyString())).thenReturn(order1);
        when(orderService.getOrder(eq(2), anyString())).thenReturn(order2);
        when(orderService.getOrder(eq(3), anyString())).thenReturn(order3);
        when(orderService.getOrder(eq(99), anyString()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng có ID = 99"));
        when(orderService.getAllOrders(anyString())).thenReturn(List.of(order1, order2, order3));
    }

    @Test
    void rejectsRequestsWithoutValidToken() throws Exception {
        assertEquals(401, send("GET", "/reports/orders", null, null).statusCode());
        assertEquals(401, send("GET", "/reports/products", null, "Bearer wrong").statusCode());
    }

    @Test
    void createsOrderReportWithGivenAndDefaultCosts() throws Exception {
        // Laptop: giá nhập 800 do người dùng gửi; Chuột: không gửi -> 50 x 0.7 = 35
        var response = send("POST", "/reports/orders",
                "{\"order_id\":1,\"cost_prices\":[{\"product_id\":1,\"unit_cost\":800}]}", TOKEN);
        assertEquals(201, response.statusCode(), response.body());
        JsonNode report = json.readTree(response.body()).get("data");

        assertMoney("2150.00", report.get("total_revenue"));
        assertMoney("1705.00", report.get("total_cost"));     // 2*800 + 3*35
        assertMoney("445.00", report.get("total_profit"));
        assertEquals(2, report.get("product_reports").size());

        JsonNode mouse = report.get("product_reports").get(1);
        assertEquals(3, mouse.get("total_sold").asInt());
        assertEquals(5, mouse.get("stock_quantity").asInt());
        assertMoney("150.00", mouse.get("revenue"));
        assertMoney("105.00", mouse.get("cost"));
        assertMoney("45.00", mouse.get("profit"));

        int id = report.get("id").asInt();
        assertEquals(200, send("GET", "/reports/orders/" + id, null, TOKEN).statusCode());
        assertEquals(2, data(send("GET", "/reports/products?order_report_id=" + id, null, TOKEN)).size());
    }

    @Test
    void rejectsInvalidOrderReports() throws Exception {
        assertEquals(201, send("POST", "/reports/orders", "{\"order_id\":1}", TOKEN).statusCode());
        assertEquals(409, send("POST", "/reports/orders", "{\"order_id\":1}", TOKEN).statusCode()); // trùng
        assertEquals(409, send("POST", "/reports/orders", "{\"order_id\":3}", TOKEN).statusCode()); // pending
        assertEquals(404, send("POST", "/reports/orders", "{\"order_id\":99}", TOKEN).statusCode());
        assertEquals(400, send("POST", "/reports/orders", "{}", TOKEN).statusCode());
        assertEquals(400, send("POST", "/reports/orders",
                "{\"order_id\":2,\"cost_prices\":[{\"product_id\":2,\"unit_cost\":-1}]}", TOKEN).statusCode());
        assertEquals(1, orderReports.count());
    }

    @Test
    void createsProductReportFromCompletedOrdersOnly() throws Exception {
        JsonNode laptop = data(send("POST", "/reports/products", "{\"product_id\":1,\"unit_cost\":800}", TOKEN));
        assertTrue(laptop.get("order_report_id").isNull());
        assertEquals(2, laptop.get("total_sold").asInt());      // đơn 3 pending không tính
        assertEquals(10, laptop.get("stock_quantity").asInt());
        assertMoney("2000.00", laptop.get("revenue"));
        assertMoney("1600.00", laptop.get("cost"));
        assertMoney("400.00", laptop.get("profit"));

        JsonNode mouse = data(send("POST", "/reports/products", "{\"product_id\":2}", TOKEN));
        assertEquals(4, mouse.get("total_sold").asInt());       // 3 + 1
        assertMoney("200.00", mouse.get("revenue"));
        assertMoney("140.00", mouse.get("cost"));              // 4 x 35

        assertEquals(404, send("POST", "/reports/products", "{\"product_id\":99}", TOKEN).statusCode());
        assertEquals(400, send("POST", "/reports/products", "{}", TOKEN).statusCode());
        assertEquals(200, send("GET", "/reports/products/" + mouse.get("id").asInt(), null, TOKEN).statusCode());
    }

    @Test
    void deletingReportsKeepsTotalsConsistent() throws Exception {
        JsonNode report = data(send("POST", "/reports/orders", "{\"order_id\":1}", TOKEN));
        int reportId = report.get("id").asInt();
        int laptopLine = report.get("product_reports").get(0).get("id").asInt();

        assertEquals(200, send("DELETE", "/reports/products/" + laptopLine, null, TOKEN).statusCode());
        assertEquals(404, send("GET", "/reports/products/" + laptopLine, null, TOKEN).statusCode());
        JsonNode updated = data(send("GET", "/reports/orders/" + reportId, null, TOKEN));
        assertMoney("150.00", updated.get("total_revenue"));
        assertMoney("105.00", updated.get("total_cost"));
        assertMoney("45.00", updated.get("total_profit"));

        assertEquals(200, send("DELETE", "/reports/orders/" + reportId, null, TOKEN).statusCode());
        assertEquals(404, send("GET", "/reports/orders/" + reportId, null, TOKEN).statusCode());
        assertEquals(404, send("DELETE", "/reports/orders/" + reportId, null, TOKEN).statusCode());
        assertEquals(0, productReports.count());
    }

    private void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but was " + actual);
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertTrue(response.statusCode() < 300, response.body());
        return json.readTree(response.body()).get("data");
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
