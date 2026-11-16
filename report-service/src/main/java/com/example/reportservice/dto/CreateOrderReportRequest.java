package com.example.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Body POST /reports/orders:
 * {
 *   "order_id": 1,
 *   "cost_prices": [ { "product_id": 1, "unit_cost": 25000000 } ]   // tùy chọn
 * }
 * Sản phẩm không có trong cost_prices -> giá nhập = giá bán x report.default-cost-ratio.
 */
public class CreateOrderReportRequest {

    @NotNull(message = "order_id không được để trống")
    @Min(value = 1, message = "order_id phải lớn hơn 0")
    @JsonProperty("order_id")
    @JsonAlias("orderId")
    private Integer orderId;

    @Valid
    @JsonProperty("cost_prices")
    @JsonAlias("costPrices")
    private List<CostPrice> costPrices = new ArrayList<>();

    public CreateOrderReportRequest() {
    }

    public CreateOrderReportRequest(Integer orderId, List<CostPrice> costPrices) {
        this.orderId = orderId;
        this.costPrices = costPrices;
    }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public List<CostPrice> getCostPrices() { return costPrices == null ? List.of() : costPrices; }
    public void setCostPrices(List<CostPrice> costPrices) { this.costPrices = costPrices; }
}
