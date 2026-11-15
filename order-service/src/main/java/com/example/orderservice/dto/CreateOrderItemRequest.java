package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body POST /order_items: { "order_id": 1, "product_id": 2, "quantity": 1 } */
public class CreateOrderItemRequest {

    @NotNull(message = "order_id không được để trống")
    @Min(value = 1, message = "order_id phải lớn hơn 0")
    @JsonProperty("order_id")
    @JsonAlias("orderId")
    private Integer orderId;

    @NotNull(message = "product_id không được để trống")
    @Min(value = 1, message = "product_id phải lớn hơn 0")
    @JsonProperty("product_id")
    @JsonAlias("productId")
    private Integer productId;

    @NotNull(message = "quantity không được để trống")
    @Min(value = 1, message = "quantity phải lớn hơn hoặc bằng 1")
    private Integer quantity;

    public CreateOrderItemRequest() {
    }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
