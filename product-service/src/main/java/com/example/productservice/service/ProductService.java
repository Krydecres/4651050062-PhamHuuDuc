package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Lấy danh sách tất cả các sản phẩm
     */
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * Lấy thông tin chi tiết một sản phẩm theo ID
     */
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Integer id) {
        return productRepository.findById(id);
    }

    /**
     * Thêm một sản phẩm mới
     */
    @Transactional
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /**
     * Cập nhật thông tin sản phẩm
     */
    @Transactional
    public Optional<Product> updateProduct(Integer id, ProductRequest request) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(request.getName().trim());
            existing.setDescription(request.getDescription());
            existing.setPrice(request.getPrice());
            existing.setQuantity(request.getQuantity());
            existing.setUpdatedAt(LocalDateTime.now());
            return productRepository.save(existing);
        });
    }

    /**
     * Xóa một sản phẩm
     */
    @Transactional
    public boolean deleteProduct(Integer id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
