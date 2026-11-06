package com.example.odata.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CustomerOrderResponse {

    private String orderId;
    private String customerId;
    private String productName;
    private BigDecimal amount;
    private String status;
    private OffsetDateTime orderDate;

    public CustomerOrderResponse() {
    }

    public CustomerOrderResponse(
            String orderId,
            String customerId,
            String productName,
            BigDecimal amount,
            String status,
            OffsetDateTime orderDate) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productName = productName;
        this.amount = amount;
        this.status = status;
        this.orderDate = orderDate;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(OffsetDateTime orderDate) {
        this.orderDate = orderDate;
    }
}
