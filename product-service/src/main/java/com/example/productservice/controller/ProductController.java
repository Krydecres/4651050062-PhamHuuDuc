package com.example.productservice.controller;

import com.example.productservice.dto.ApiResponse;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.model.Product;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /products
     * Lấy danh sách tất cả các sản phẩm
     */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", products));
    }

    /**
     * GET /products/{id}
     * Lấy thông tin chi tiết một sản phẩm
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Integer id) {
        Optional<Product> productOpt = productService.getProductById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Không tìm thấy sản phẩm có ID = " + id));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", productOpt.get()));
    }

    /**
     * POST /products
     * Thêm một sản phẩm mới
     */
    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<Object>> createProduct(
            @Valid @RequestBody ProductRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Dữ liệu sản phẩm không hợp lệ";
            return ResponseEntity.badRequest().body(ApiResponse.error(errorMessage));
        }

        Product created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Thêm sản phẩm mới thành công", created));
    }

    /**
     * PUT /products/{id}
     * Cập nhật thông tin sản phẩm
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Dữ liệu sản phẩm không hợp lệ";
            return ResponseEntity.badRequest().body(ApiResponse.error(errorMessage));
        }

        Optional<Product> updatedOpt = productService.updateProduct(id, request);
        if (updatedOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Không tìm thấy sản phẩm có ID = " + id + " để cập nhật"));
        }

        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin sản phẩm thành công", updatedOpt.get()));
    }

    /**
     * DELETE /products/{id}
     * Xóa một sản phẩm
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        boolean deleted = productService.deleteProduct(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Không tìm thấy sản phẩm có ID = " + id + " để xóa"));
        }
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm ID = " + id + " thành công"));
    }
}
