package com.example.orderservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Bảng đơn hàng (orders) theo Phụ lục đề bài:
 * id, customer_name, customer_email, total_amount, status, created_at, updated_at
 */
@Entity
@Table(name = "orders")
@JsonPropertyOrder({"id", "customer_name", "customer_email", "total_amount", "status", "created_at", "updated_at", "items"})
public class Order {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final List<String> VALID_STATUSES = List.of(STATUS_PENDING, STATUS_COMPLETED, STATUS_CANCELLED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "customer_name", nullable = false, length = 255)
    @JsonProperty("customer_name")
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 255)
    @JsonProperty("customer_email")
    private String customerEmail;

    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    @JsonProperty("total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "status", length = 50, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /** Danh sách mặt hàng - không lưu trong bảng orders, được nạp từ bảng order_items khi trả về. */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<OrderItem> items;

    public Order() {
    }

    public Order(String customerName, String customerEmail) {
        this.customerName = customerName;
        this.customerEmail = customerEmail;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = STATUS_PENDING;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
