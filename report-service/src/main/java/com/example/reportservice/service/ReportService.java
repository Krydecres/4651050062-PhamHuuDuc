package com.example.reportservice.service;

import com.example.reportservice.dto.ApiException;
import com.example.reportservice.dto.CostPrice;
import com.example.reportservice.dto.CreateOrderReportRequest;
import com.example.reportservice.dto.CreateProductReportRequest;
import com.example.reportservice.dto.OrderInfo;
import com.example.reportservice.dto.OrderItemInfo;
import com.example.reportservice.dto.ProductInfo;
import com.example.reportservice.model.OrderReport;
import com.example.reportservice.model.ProductReport;
import com.example.reportservice.repository.OrderReportRepository;
import com.example.reportservice.repository.ProductReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ báo cáo.
 *
 * Công thức (theo phụ lục):
 *   doanh thu (revenue)  = Σ quantity × unit_price      (giá bán lúc đặt hàng, lấy từ Order Service)
 *   chi phí   (cost)     = Σ quantity × unit_cost       (giá nhập: lấy từ tham số đầu vào,
 *                                                         không có thì = unit_price × default-cost-ratio)
 *   lợi nhuận (profit)   = revenue - cost
 *   hàng bán được        = total_sold = Σ quantity
 *   hàng tồn             = stock_quantity lấy từ Product Service tại thời điểm lập báo cáo
 *
 * Chỉ đơn hàng ở trạng thái "completed" mới được tính doanh thu
 * (pending chưa bán xong, cancelled không phát sinh doanh thu).
 */
@Service
public class ReportService {

    public static final String STATUS_COMPLETED = "completed";

    /** Giới hạn của kiểu DECIMAL(10, 2) theo phụ lục */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private final OrderReportRepository orderReportRepository;
    private final ProductReportRepository productReportRepository;
    private final OrderServiceClient orderServiceClient;
    private final ProductServiceClient productServiceClient;
    private final BigDecimal defaultCostRatio;

    public ReportService(OrderReportRepository orderReportRepository,
                         ProductReportRepository productReportRepository,
                         OrderServiceClient orderServiceClient,
                         ProductServiceClient productServiceClient,
                         @Value("${report.default-cost-ratio:0.7}") BigDecimal defaultCostRatio) {
        this.orderReportRepository = orderReportRepository;
        this.productReportRepository = productReportRepository;
        this.orderServiceClient = orderServiceClient;
        this.productServiceClient = productServiceClient;
        this.defaultCostRatio = defaultCostRatio;
    }

    // ======================= BÁO CÁO ĐƠN HÀNG =======================

    @Transactional(readOnly = true)
    public List<OrderReport> getAllOrderReports() {
        List<OrderReport> reports = orderReportRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        reports.forEach(r -> r.setProductReports(productReportRepository.findByOrderReportIdOrderByIdAsc(r.getId())));
        return reports;
    }

    @Transactional(readOnly = true)
    public OrderReport getOrderReport(Integer id) {
        OrderReport report = findOrderReport(id);
        report.setProductReports(productReportRepository.findByOrderReportIdOrderByIdAsc(id));
        return report;
    }

    /**
     * Lập báo cáo cho 1 đơn hàng: lấy đơn từ Order Service, tính doanh thu / chi phí / lợi nhuận
     * của cả đơn và tạo kèm 1 dòng product_reports cho mỗi sản phẩm trong đơn.
     */
    @Transactional
    public OrderReport createOrderReport(CreateOrderReportRequest request, String authorizationHeader) {
        if (orderReportRepository.existsByOrderId(request.getOrderId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Đơn hàng ID = " + request.getOrderId() + " đã có báo cáo. Xóa báo cáo cũ nếu muốn lập lại");
        }

        OrderInfo order = orderServiceClient.getOrder(request.getOrderId(), authorizationHeader);
        ensureCompleted(order);
        if (order.getItems().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Đơn hàng ID = " + order.getId() + " không có mặt hàng nào");
        }

        Map<Integer, BigDecimal> unitCosts = toCostMap(request.getCostPrices());

        // Gộp các dòng cùng sản phẩm trong đơn
        Map<Integer, ProductReport> lines = new LinkedHashMap<>();
        for (OrderItemInfo item : order.getItems()) {
            ProductReport line = lines.computeIfAbsent(item.getProductId(), pid -> newProductReport(pid, item.getProductName()));
            BigDecimal unitCost = unitCosts.getOrDefault(item.getProductId(), defaultCost(item.getUnitPrice()));
            addSale(line, item, unitCost);
        }

        // Hàng tồn hiện tại của từng sản phẩm (sản phẩm đã bị xóa bên Product Service -> để trống)
        for (ProductReport line : lines.values()) {
            ProductInfo product = findProductOrNull(line.getProductId(), authorizationHeader);
            if (product != null) {
                line.setProductName(product.getName());
                line.setStockQuantity(product.getQuantity());
            }
            line.setProfit(line.getRevenue().subtract(line.getCost()));
            checkAmounts(line.getRevenue(), line.getCost(), line.getProfit());
        }

        OrderReport report = new OrderReport();
        report.setOrderId(order.getId());
        applyTotals(report, lines.values());
        report = orderReportRepository.save(report);

        for (ProductReport line : lines.values()) {
            line.setOrderReportId(report.getId());
        }
        report.setProductReports(productReportRepository.saveAll(lines.values()));
        return report;
    }

    @Transactional
    public void deleteOrderReport(Integer id) {
        OrderReport report = findOrderReport(id);
        productReportRepository.deleteByOrderReportId(report.getId());
        orderReportRepository.delete(report);
    }

    // ======================= BÁO CÁO SẢN PHẨM =======================

    @Transactional(readOnly = true)
    public List<ProductReport> getAllProductReports(Integer orderReportId) {
        if (orderReportId != null) {
            findOrderReport(orderReportId);
            return productReportRepository.findByOrderReportIdOrderByIdAsc(orderReportId);
        }
        return productReportRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public ProductReport getProductReport(Integer id) {
        return findProductReport(id);
    }

    /**
     * Lập báo cáo tổng hợp cho 1 sản phẩm: lấy sản phẩm từ Product Service (tên, tồn kho)
     * và cộng dồn số lượng bán / doanh thu trên mọi đơn hàng "completed" từ Order Service.
     */
    @Transactional
    public ProductReport createProductReport(CreateProductReportRequest request, String authorizationHeader) {
        ProductInfo product = productServiceClient.getProduct(request.getProductId(), authorizationHeader);
        List<OrderInfo> orders = orderServiceClient.getAllOrders(authorizationHeader);

        ProductReport report = newProductReport(product.getId(), product.getName());
        report.setStockQuantity(product.getQuantity());

        for (OrderInfo order : orders) {
            if (!STATUS_COMPLETED.equalsIgnoreCase(order.getStatus())) {
                continue;
            }
            for (OrderItemInfo item : order.getItems()) {
                if (product.getId().equals(item.getProductId())) {
                    BigDecimal unitCost = request.getUnitCost() != null
                            ? request.getUnitCost()
                            : defaultCost(item.getUnitPrice());
                    addSale(report, item, unitCost);
                }
            }
        }
        report.setProfit(report.getRevenue().subtract(report.getCost()));
        checkAmounts(report.getRevenue(), report.getCost(), report.getProfit());
        return productReportRepository.save(report);
    }

    /**
     * Xóa báo cáo sản phẩm. Nếu dòng này thuộc một báo cáo đơn hàng thì
     * tổng doanh thu / chi phí / lợi nhuận của báo cáo đơn hàng đó được tính lại.
     */
    @Transactional
    public void deleteProductReport(Integer id) {
        ProductReport report = findProductReport(id);
        productReportRepository.delete(report);
        productReportRepository.flush();

        if (report.getOrderReportId() != null) {
            orderReportRepository.findById(report.getOrderReportId()).ifPresent(parent -> {
                applyTotals(parent, productReportRepository.findByOrderReportIdOrderByIdAsc(parent.getId()));
                orderReportRepository.save(parent);
            });
        }
    }

    // ============================ HELPERS ============================

    private OrderReport findOrderReport(Integer id) {
        return orderReportRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo đơn hàng có ID = " + id));
    }

    private ProductReport findProductReport(Integer id) {
        return productReportRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo sản phẩm có ID = " + id));
    }

    private void ensureCompleted(OrderInfo order) {
        if (!STATUS_COMPLETED.equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Đơn hàng ID = " + order.getId() + " đang ở trạng thái '" + order.getStatus()
                            + "'. Chỉ lập báo cáo cho đơn hàng đã hoàn thành (completed)");
        }
    }

    private Map<Integer, BigDecimal> toCostMap(List<CostPrice> costPrices) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (CostPrice cp : costPrices) {
            if (map.put(cp.getProductId(), cp.getUnitCost()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "product_id = " + cp.getProductId() + " bị khai báo giá nhập nhiều lần trong cost_prices");
            }
        }
        return map;
    }

    private BigDecimal defaultCost(BigDecimal unitPrice) {
        return unitPrice.multiply(defaultCostRatio).setScale(2, RoundingMode.HALF_UP);
    }

    private ProductReport newProductReport(Integer productId, String productName) {
        ProductReport report = new ProductReport();
        report.setProductId(productId);
        report.setProductName(productName);
        report.setTotalSold(0);
        report.setRevenue(BigDecimal.ZERO);
        report.setCost(BigDecimal.ZERO);
        report.setProfit(BigDecimal.ZERO);
        return report;
    }

    private void addSale(ProductReport report, OrderItemInfo item, BigDecimal unitCost) {
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
        BigDecimal revenue = item.getTotalPrice() != null ? item.getTotalPrice() : item.getUnitPrice().multiply(quantity);
        report.setTotalSold(report.getTotalSold() + item.getQuantity());
        report.setRevenue(report.getRevenue().add(revenue));
        report.setCost(report.getCost().add(unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP)));
    }

    private void applyTotals(OrderReport report, Iterable<ProductReport> lines) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (ProductReport line : lines) {
            revenue = revenue.add(line.getRevenue());
            cost = cost.add(line.getCost());
        }
        BigDecimal profit = revenue.subtract(cost);
        checkAmounts(revenue, cost, profit);
        report.setTotalRevenue(revenue);
        report.setTotalCost(cost);
        report.setTotalProfit(profit);
    }

    private void checkAmounts(BigDecimal... amounts) {
        for (BigDecimal amount : amounts) {
            if (amount.abs().compareTo(MAX_AMOUNT) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Giá trị báo cáo vượt quá giới hạn 99999999.99 của kiểu DECIMAL(10, 2)");
            }
        }
    }

    private ProductInfo findProductOrNull(Integer productId, String authorizationHeader) {
        try {
            return productServiceClient.getProduct(productId, authorizationHeader);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }
}
