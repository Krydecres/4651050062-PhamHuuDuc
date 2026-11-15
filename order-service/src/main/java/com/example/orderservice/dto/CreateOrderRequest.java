package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body POST /orders:
 * {
 *   "customer_name": "Nguyễn Văn A",
 *   "customer_email": "a@gmail.com",
 *   "items": [ { "product_id": 1, "quantity": 2 } ]
 * }
 */
public class CreateOrderRequest {

    @NotBlank(message = "customer_name không được để trống")
    @Size(max = 255, message = "customer_name không được vượt quá 255 ký tự")
    @JsonProperty("customer_name")
    @JsonAlias("customerName")
    private String customerName;

    @NotBlank(message = "customer_email không được để trống")
    @Email(message = "customer_email không đúng định dạng email")
    @Size(max = 255, message = "customer_email không được vượt quá 255 ký tự")
    @JsonProperty("customer_email")
    @JsonAlias("customerEmail")
    private String customerEmail;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm (items)")
    @Valid
    private List<OrderItemLine> items;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String customerName, String customerEmail, List<OrderItemLine> items) {
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.items = items;
    }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public List<OrderItemLine> getItems() { return items; }
    public void setItems(List<OrderItemLine> items) { this.items = items; }
}
