package com.example.reportservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Bảng product_reports theo Phụ lục đề bài:
 * id, order_report_id, product_id, total_sold, revenue, cost, profit (= revenue - cost)
 *
 * - order_report_id có giá trị: dòng báo cáo của 1 sản phẩm trong 1 đơn hàng (tạo kèm POST /reports/orders).
 * - order_report_id = null    : báo cáo tổng hợp 1 sản phẩm trên mọi đơn hàng đã hoàn thành (POST /reports/products).
 *
 * Bổ sung: product_name, stock_quantity (hàng tồn tại thời điểm lập báo cáo), created_at.
 */
@Entity
@Table(name = "product_reports", indexes = @Index(name = "idx_product_reports_order_report", columnList = "order_report_id"))
@JsonPropertyOrder({"id", "order_report_id", "product_id", "product_name", "total_sold", "stock_quantity",
        "revenue", "cost", "profit", "created_at"})
public class ProductReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_report_id")
    @JsonProperty("order_report_id")
    private Integer orderReportId;

    /** ID sản phẩm bên Product Service (chỉ lưu vết) */
    @Column(name = "product_id", nullable = false)
    @JsonProperty("product_id")
    private Integer productId;

    @Column(name = "product_name", length = 255)
    @JsonProperty("product_name")
    private String productName;

    @Column(name = "total_sold", nullable = false)
    @JsonProperty("total_sold")
    private Integer totalSold;

    /** Số lượng tồn kho tại thời điểm lập báo cáo (null nếu sản phẩm đã bị xóa bên Product Service) */
    @Column(name = "stock_quantity")
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;

    @Column(name = "revenue", precision = 10, scale = 2, nullable = false)
    private BigDecimal revenue;

    @Column(name = "cost", precision = 10, scale = 2, nullable = false)
    private BigDecimal cost;

    @Column(name = "profit", precision = 10, scale = 2, nullable = false)
    private BigDecimal profit;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOrderReportId() { return orderReportId; }
    public void setOrderReportId(Integer orderReportId) { this.orderReportId = orderReportId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getTotalSold() { return totalSold; }
    public void setTotalSold(Integer totalSold) { this.totalSold = totalSold; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public BigDecimal getProfit() { return profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
