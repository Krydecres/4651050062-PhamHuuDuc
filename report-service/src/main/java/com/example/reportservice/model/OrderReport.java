package com.example.reportservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Bảng orders_reports theo Phụ lục đề bài:
 * id, order_id, total_revenue, total_cost, total_profit (= total_revenue - total_cost)
 *
 * Bổ sung: created_at (thời điểm lập báo cáo).
 */
@Entity
@Table(name = "orders_reports")
@JsonPropertyOrder({"id", "order_id", "total_revenue", "total_cost", "total_profit", "created_at", "product_reports"})
public class OrderReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /** ID đơn hàng bên Order Service (chỉ lưu vết, không có khóa ngoại sang CSDL khác) */
    @Column(name = "order_id", nullable = false, unique = true)
    @JsonProperty("order_id")
    private Integer orderId;

    @Column(name = "total_revenue", precision = 10, scale = 2, nullable = false)
    @JsonProperty("total_revenue")
    private BigDecimal totalRevenue;

    @Column(name = "total_cost", precision = 10, scale = 2, nullable = false)
    @JsonProperty("total_cost")
    private BigDecimal totalCost;

    @Column(name = "total_profit", precision = 10, scale = 2, nullable = false)
    @JsonProperty("total_profit")
    private BigDecimal totalProfit;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /** Báo cáo của từng sản phẩm trong đơn - nạp từ bảng product_reports khi trả về */
    @Transient
    @JsonProperty("product_reports")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductReport> productReports;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public BigDecimal getTotalProfit() { return totalProfit; }
    public void setTotalProfit(BigDecimal totalProfit) { this.totalProfit = totalProfit; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ProductReport> getProductReports() { return productReports; }
    public void setProductReports(List<ProductReport> productReports) { this.productReports = productReports; }
}
