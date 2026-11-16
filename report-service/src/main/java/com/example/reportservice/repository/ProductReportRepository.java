package com.example.reportservice.repository;

import com.example.reportservice.model.ProductReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReportRepository extends JpaRepository<ProductReport, Integer> {

    List<ProductReport> findByOrderReportIdOrderByIdAsc(Integer orderReportId);

    List<ProductReport> findAllByOrderByIdAsc();

    void deleteByOrderReportId(Integer orderReportId);
}
