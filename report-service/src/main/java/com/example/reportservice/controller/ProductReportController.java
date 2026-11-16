package com.example.reportservice.controller;

import com.example.reportservice.dto.ApiResponse;
import com.example.reportservice.dto.CreateProductReportRequest;
import com.example.reportservice.filter.AuthInterceptor;
import com.example.reportservice.model.ProductReport;
import com.example.reportservice.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reports/products")
public class ProductReportController {

    private final ReportService reportService;

    public ProductReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /reports/products (?order_report_id=1 để lọc theo báo cáo đơn hàng) */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<ProductReport>>> getAll(
            @RequestParam(value = "order_report_id", required = false) Integer orderReportId) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo sản phẩm thành công",
                reportService.getAllProductReports(orderReportId)));
    }

    /** GET /reports/products/{id} - Chi tiết báo cáo một sản phẩm */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductReport>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết báo cáo sản phẩm thành công",
                reportService.getProductReport(id)));
    }

    /** POST /reports/products - Tạo báo cáo sản phẩm từ dữ liệu Product Service + Order Service */
    @PostMapping({"", "/"})
    public ResponseEntity<ApiResponse<ProductReport>> create(
            @Valid @RequestBody CreateProductReportRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_HEADER_ATTRIBUTE) String authorizationHeader) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo báo cáo sản phẩm thành công",
                        reportService.createProductReport(request, authorizationHeader)));
    }

    /** DELETE /reports/products/{id} - Xóa báo cáo sản phẩm */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        reportService.deleteProductReport(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa báo cáo sản phẩm ID = " + id + " thành công"));
    }
}
