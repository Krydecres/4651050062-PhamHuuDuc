package com.example.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-service-context-test")
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
