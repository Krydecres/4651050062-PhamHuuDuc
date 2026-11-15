package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body PUT /orders/{id}: cập nhật trạng thái đơn hàng.
 * { "status": "completed" }   (pending | completed | cancelled)
 * Có thể kèm customer_name / customer_email (tùy chọn) để sửa thông tin khách hàng.
 */
public class UpdateOrderRequest {

    @NotBlank(message = "status không được để trống (pending, completed, cancelled)")
    private String status;

    @Size(max = 255, message = "customer_name không được vượt quá 255 ký tự")
    @JsonProperty("customer_name")
    @JsonAlias("customerName")
    private String customerName;

    @Email(message = "customer_email không đúng định dạng email")
    @Size(max = 255, message = "customer_email không được vượt quá 255 ký tự")
    @JsonProperty("customer_email")
    @JsonAlias("customerEmail")
    private String customerEmail;

    public UpdateOrderRequest() {
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
}
