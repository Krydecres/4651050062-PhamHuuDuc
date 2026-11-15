package com.example.orderservice.controller;

import com.example.orderservice.dto.ApiResponse;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.UpdateOrderRequest;
import com.example.orderservice.filter.AuthInterceptor;
import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** GET /orders - Lấy danh sách tất cả các đơn hàng (kèm mặt hàng) */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", orderService.getAllOrders()));
    }

    /** GET /orders/{id} - Lấy thông tin chi tiết một đơn hàng */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", orderService.getOrder(id)));
    }

    /** POST /orders - Tạo đơn hàng mới (kiểm tra tồn kho qua Product Service) */
    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_HEADER_ATTRIBUTE) String authorizationHeader) {
        Order created = orderService.createOrder(request, authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thành công", created));
    }

    /** PUT /orders/{id} - Cập nhật trạng thái đơn hàng */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> updateOrder(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật đơn hàng thành công", orderService.updateOrder(id, request)));
    }

    /** DELETE /orders/{id} - Xóa một đơn hàng (xóa luôn các mặt hàng của đơn) */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Integer id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa đơn hàng ID = " + id + " thành công"));
    }
}
