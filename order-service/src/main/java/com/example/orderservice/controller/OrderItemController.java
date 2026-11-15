package com.example.orderservice.controller;

import com.example.orderservice.dto.ApiResponse;
import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.UpdateOrderItemRequest;
import com.example.orderservice.filter.AuthInterceptor;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order_items")
public class OrderItemController {

    private final OrderService orderService;

    public OrderItemController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** GET /order_items (?order_id=1 để lọc theo đơn) - Lấy danh sách mặt hàng */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<OrderItem>>> getAllItems(
            @RequestParam(value = "order_id", required = false) Integer orderId) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách mặt hàng thành công", orderService.getAllItems(orderId)));
    }

    /** GET /order_items/{id} - Lấy thông tin chi tiết một mặt hàng */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderItem>> getItem(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết mặt hàng thành công", orderService.getItem(id)));
    }

    /** POST /order_items - Thêm mặt hàng mới vào đơn (kiểm tra tồn kho) */
    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<OrderItem>> createItem(
            @Valid @RequestBody CreateOrderItemRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_HEADER_ATTRIBUTE) String authorizationHeader) {
        OrderItem created = orderService.createItem(request, authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm mặt hàng vào đơn hàng thành công", created));
    }

    /** PUT /order_items/{id} - Cập nhật mặt hàng (số lượng / đổi sản phẩm) */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderItem>> updateItem(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateOrderItemRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_HEADER_ATTRIBUTE) String authorizationHeader) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật mặt hàng thành công",
                orderService.updateItem(id, request, authorizationHeader)));
    }

    /** DELETE /order_items/{id} - Xóa một mặt hàng trong đơn hàng */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Integer id) {
        orderService.deleteItem(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa mặt hàng ID = " + id + " thành công"));
    }
}
