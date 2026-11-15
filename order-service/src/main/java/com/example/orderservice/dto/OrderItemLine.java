package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Một dòng sản phẩm trong body POST /orders: { "product_id": 1, "quantity": 2 } */
public class OrderItemLine {

    @NotNull(message = "product_id không được để trống")
    @Min(value = 1, message = "product_id phải lớn hơn 0")
    @JsonProperty("product_id")
    @JsonAlias("productId")
    private Integer productId;

    @NotNull(message = "quantity không được để trống")
    @Min(value = 1, message = "quantity phải lớn hơn hoặc bằng 1")
    private Integer quantity;

    public OrderItemLine() {
    }

    public OrderItemLine(Integer productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
