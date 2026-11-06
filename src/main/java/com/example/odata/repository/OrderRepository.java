package com.example.odata.repository;

import com.example.odata.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, String> {
    List<CustomerOrder> findByCustomerId(String customerId);
}
