package com.example.orderservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Bảng chi tiết đơn hàng (order_items) theo Phụ lục đề bài:
 * id, order_id, product_id, product_name, quantity, unit_price, total_price
 *
 * product_id chỉ "lưu vết" sang Product Service (không có khóa ngoại sang CSDL khác - SOA).
 */
@Entity
@Table(name = "order_items", indexes = @Index(name = "idx_order_items_order_id", columnList = "order_id"))
@JsonPropertyOrder({"id", "order_id", "product_id", "product_name", "quantity", "unit_price", "total_price"})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_id", nullable = false)
    @JsonProperty("order_id")
    private Integer orderId;

    @Column(name = "product_id", nullable = false)
    @JsonProperty("product_id")
    private Integer productId;

    @Column(name = "product_name", nullable = false, length = 255)
    @JsonProperty("product_name")
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    @JsonProperty("total_price")
    private BigDecimal totalPrice;

    public OrderItem() {
    }

    /** total_price = quantity * unit_price */
    public void recalculateTotal() {
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
