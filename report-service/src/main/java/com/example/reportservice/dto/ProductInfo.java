package com.example.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Thông tin sản phẩm nhận về từ Product Service (GET /products/{id}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductInfo {

    private Integer id;
    private String name;
    private BigDecimal price;
    private Integer quantity;

    public ProductInfo() {
    }

    public ProductInfo(Integer id, String name, BigDecimal price, Integer quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
