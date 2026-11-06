package com.example.odata.controller;

import com.example.odata.model.CustomerOrderResponse;
import com.example.odata.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    private final OrderService service;

    public OrderRestController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerOrderResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<CustomerOrderResponse> getByCustomer(
            @PathVariable String customerId) {
        return service.findByCustomerId(customerId);
    }

    @GetMapping("/{orderId}")
    public CustomerOrderResponse getById(@PathVariable String orderId) {
        return service.findById(orderId);
    }
}
