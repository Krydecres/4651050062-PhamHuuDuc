package com.example.orderservice.repository;

import com.example.orderservice.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Integer orderId);

    List<OrderItem> findAllByOrderByIdAsc();

    void deleteByOrderId(Integer orderId);
}
