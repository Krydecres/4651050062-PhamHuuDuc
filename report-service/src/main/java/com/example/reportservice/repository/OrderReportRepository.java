package com.example.reportservice.repository;

import com.example.reportservice.model.OrderReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReportRepository extends JpaRepository<OrderReport, Integer> {

    boolean existsByOrderId(Integer orderId);
}
