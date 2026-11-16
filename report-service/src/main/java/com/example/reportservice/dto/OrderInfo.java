package com.example.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Đơn hàng nhận về từ Order Service (GET /orders, GET /orders/{id}) */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderInfo {

    private Integer id;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    private String status;

    private List<OrderItemInfo> items = new ArrayList<>();

    public OrderInfo() {
    }

    public OrderInfo(Integer id, String status, List<OrderItemInfo> items) {
        this.id = id;
        this.status = status;
        this.items = items;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItemInfo> getItems() { return items == null ? List.of() : items; }
    public void setItems(List<OrderItemInfo> items) { this.items = items; }
}
