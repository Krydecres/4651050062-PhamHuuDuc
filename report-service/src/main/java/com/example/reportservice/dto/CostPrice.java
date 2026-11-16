package com.example.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Giá nhập (giá vốn) của 1 sản phẩm: { "product_id": 1, "unit_cost": 25000000 } */
public class CostPrice {

    @NotNull(message = "product_id trong cost_prices không được để trống")
    @Min(value = 1, message = "product_id phải lớn hơn 0")
    @JsonProperty("product_id")
    @JsonAlias("productId")
    private Integer productId;

    @NotNull(message = "unit_cost trong cost_prices không được để trống")
    @DecimalMin(value = "0.0", message = "unit_cost phải lớn hơn hoặc bằng 0")
    @Digits(integer = 8, fraction = 2, message = "unit_cost tối đa 99999999.99 và tối đa 2 chữ số thập phân")
    @JsonProperty("unit_cost")
    @JsonAlias("unitCost")
    private BigDecimal unitCost;

    public CostPrice() {
    }

    public CostPrice(Integer productId, BigDecimal unitCost) {
        this.productId = productId;
        this.unitCost = unitCost;
    }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
