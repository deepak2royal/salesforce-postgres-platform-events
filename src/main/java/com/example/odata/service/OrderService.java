package com.example.odata.service;

import com.example.odata.entity.CustomerOrder;
import com.example.odata.model.CustomerOrderResponse;
import com.example.odata.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public List<CustomerOrderResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CustomerOrderResponse> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CustomerOrderResponse findById(String orderId) {
        CustomerOrder order = repository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found: " + orderId));
        return toResponse(order);
    }

    private CustomerOrderResponse toResponse(CustomerOrder order) {
        return new CustomerOrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getProductName(),
                order.getAmount(),
                order.getStatus(),
                order.getOrderDate() == null
                        ? null
                        : order.getOrderDate().atOffset(ZoneOffset.UTC)
        );
    }
}
