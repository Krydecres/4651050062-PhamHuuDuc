package com.example.productservice.config;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                Product p1 = new Product(
                        null,
                        "Laptop Dell XPS 15",
                        "Laptop văn phòng cao cấp, Intel Core i7, 16GB RAM, 512GB SSD",
                        new BigDecimal("28500000.00"),
                        15,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

                Product p2 = new Product(
                        null,
                        "iPhone 16 Pro Max",
                        "Điện thoại thông minh Apple 256GB Titan tự nhiên",
                        new BigDecimal("34990000.00"),
                        25,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

                Product p3 = new Product(
                        null,
                        "Chuột Logitech MX Master 3S",
                        "Chuột không dây công thái học cao cấp cho lập trình viên",
                        new BigDecimal("2490000.00"),
                        50,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

                productRepository.saveAll(List.of(p1, p2, p3));
                System.out.println(">>> Đã khởi tạo dữ liệu mẫu thành công cho Bảng products (3 sản phẩm ban đầu)");
            }
        };
    }
}
