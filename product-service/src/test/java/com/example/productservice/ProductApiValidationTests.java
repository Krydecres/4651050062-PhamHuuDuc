package com.example.productservice;

import com.example.productservice.dto.AuthValidationResult;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.service.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:product-api-validation-test")
class ProductApiValidationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ProductRepository products;

    @MockitoBean
    private AuthServiceClient authService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private Product original;

    @BeforeEach
    void prepareProduct() {
        when(authService.validateTokenWithAuthService("Bearer validation-test"))
                .thenReturn(new AuthValidationResult(true, "Valid", "admin", 1));
        products.deleteAll();
        original = products.saveAndFlush(new Product("Original", "Unchanged", new BigDecimal("12.34"), 7));
        original = products.findById(original.getId()).orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,100000000,2", "PUT,100000000,2",
            "POST,1.999,2", "PUT,1.999,2",
            "POST,12.34,1.9", "PUT,12.34,1.9",
            "POST,12.34,-0.9", "PUT,12.34,-0.9"
    })
    void rejectsInvalidNumbersWithoutChangingStoredData(String method, String price, String quantity) throws Exception {
        var response = send(method, body(price, quantity), "application/json");

        assertError(response, 400);
        assertOriginalUnchanged();
    }

    @ParameterizedTest
    @CsvSource({"POST", "PUT"})
    void returns415ForUnsupportedContentType(String method) throws Exception {
        var response = send(method, body("12.34", "2"), "text/plain");

        assertError(response, 415);
        assertOriginalUnchanged();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,99999999.99,2147483647", "PUT,99999999.99,2147483647",
            "POST,0,0", "PUT,0,0"
    })
    void acceptsValidBoundaryValuesAndReturnsTheStoredValues(String method, String price, int quantity) throws Exception {
        var response = send(method, body(price, Integer.toString(quantity)), "application/json");

        assertEquals(method.equals("POST") ? 201 : 200, response.statusCode());
        var created = json.readTree(response.body());
        assertTrue(created.get("success").asBoolean());
        int id = created.get("data").get("id").asInt();
        Product stored = products.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal(price).compareTo(stored.getPrice()));
        assertEquals(quantity, stored.getQuantity());

        var get = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/products/" + id))
                .header("Authorization", "Bearer validation-test").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, get.statusCode());
        var retrieved = json.readTree(get.body()).get("data");
        assertEquals(0, created.get("data").get("price").decimalValue().compareTo(retrieved.get("price").decimalValue()));
        assertEquals(quantity, retrieved.get("quantity").asInt());
    }

    private HttpResponse<String> send(String method, String body, String contentType) throws Exception {
        String path = "/products" + (method.equals("PUT") ? "/" + original.getId() : "");
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer validation-test")
                .header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String body(String price, String quantity) {
        return "{\"name\":\"Validation test\",\"price\":" + price + ",\"quantity\":" + quantity + "}";
    }

    private void assertError(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        var error = json.readTree(response.body());
        assertFalse(error.get("success").asBoolean());
        assertFalse(error.get("message").asString().isBlank());
    }

    private void assertOriginalUnchanged() {
        assertEquals(1, products.count());
        Product stored = products.findById(original.getId()).orElseThrow();
        assertEquals("Original", stored.getName());
        assertEquals("Unchanged", stored.getDescription());
        assertEquals(0, new BigDecimal("12.34").compareTo(stored.getPrice()));
        assertEquals(7, stored.getQuantity());
        assertEquals(original.getCreatedAt(), stored.getCreatedAt());
        assertEquals(original.getUpdatedAt(), stored.getUpdatedAt());
    }
}
