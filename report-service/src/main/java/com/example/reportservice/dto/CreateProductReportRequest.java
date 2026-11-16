package com.example.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body POST /reports/products:
 * { "product_id": 3, "unit_cost": 2000000 }   // unit_cost tùy chọn
 * Không gửi unit_cost -> giá nhập = giá bán x report.default-cost-ratio.
 */
public class CreateProductReportRequest {

    @NotNull(message = "product_id không được để trống")
    @Min(value = 1, message = "product_id phải lớn hơn 0")
    @JsonProperty("product_id")
    @JsonAlias("productId")
    private Integer productId;

    @DecimalMin(value = "0.0", message = "unit_cost phải lớn hơn hoặc bằng 0")
    @Digits(integer = 8, fraction = 2, message = "unit_cost tối đa 99999999.99 và tối đa 2 chữ số thập phân")
    @JsonProperty("unit_cost")
    @JsonAlias("unitCost")
    private BigDecimal unitCost;

    public CreateProductReportRequest() {
    }

    public CreateProductReportRequest(Integer productId, BigDecimal unitCost) {
        this.productId = productId;
        this.unitCost = unitCost;
    }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
