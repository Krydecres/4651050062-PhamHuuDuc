package com.example.reportservice.controller;

import com.example.reportservice.dto.ApiResponse;
import com.example.reportservice.dto.CreateOrderReportRequest;
import com.example.reportservice.filter.AuthInterceptor;
import com.example.reportservice.model.OrderReport;
import com.example.reportservice.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reports/orders")
public class OrderReportController {

    private final ReportService reportService;

    public OrderReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /reports/orders - Danh sách báo cáo theo đơn hàng */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<OrderReport>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo đơn hàng thành công",
                reportService.getAllOrderReports()));
    }

    /** GET /reports/orders/{id} - Chi tiết báo cáo một đơn hàng (kèm báo cáo từng sản phẩm) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderReport>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết báo cáo đơn hàng thành công",
                reportService.getOrderReport(id)));
    }

    /** POST /reports/orders - Tạo báo cáo đơn hàng từ dữ liệu Order Service */
    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<OrderReport>> create(
            @Valid @RequestBody CreateOrderReportRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_HEADER_ATTRIBUTE) String authorizationHeader) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo báo cáo đơn hàng thành công",
                        reportService.createOrderReport(request, authorizationHeader)));
    }

    /** DELETE /reports/orders/{id} - Xóa báo cáo đơn hàng (xóa luôn các báo cáo sản phẩm của nó) */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        reportService.deleteOrderReport(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa báo cáo đơn hàng ID = " + id + " thành công"));
    }
}
