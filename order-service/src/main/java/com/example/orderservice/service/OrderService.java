package com.example.orderservice.service;

import com.example.orderservice.dto.ApiException;
import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderItemLine;
import com.example.orderservice.dto.ProductInfo;
import com.example.orderservice.dto.UpdateOrderItemRequest;
import com.example.orderservice.dto.UpdateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.repository.OrderItemRepository;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Nghiệp vụ quản lý đơn hàng + chi tiết đơn hàng.
 *
 * Quy tắc:
 *  - Tạo đơn / thêm / sửa mặt hàng: gọi Product Service để kiểm tra sản phẩm tồn tại và
 *    tổng số lượng đặt (của cùng 1 sản phẩm trong đơn) không vượt quá tồn kho.
 *  - product_name, unit_price được chụp lại tại thời điểm đặt hàng; total_price = quantity * unit_price.
 *  - total_amount của đơn = tổng total_price các mặt hàng, tự tính lại mỗi khi mặt hàng thay đổi.
 *  - Trạng thái: pending -> completed | cancelled. Đơn đã completed/cancelled không đổi trạng thái
 *    và không thêm/sửa/xóa mặt hàng được nữa.
 */
@Service
public class OrderService {

    /** Giới hạn của kiểu DECIMAL(10, 2) theo phụ lục */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductServiceClient productServiceClient;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        ProductServiceClient productServiceClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productServiceClient = productServiceClient;
    }

    // =========================== ORDERS ===========================

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        orders.forEach(o -> o.setItems(orderItemRepository.findByOrderIdOrderByIdAsc(o.getId())));
        return orders;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Integer id) {
        Order order = findOrder(id);
        order.setItems(orderItemRepository.findByOrderIdOrderByIdAsc(id));
        return order;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request, String authorizationHeader) {
        // Gộp số lượng theo product_id (trường hợp 1 sản phẩm xuất hiện nhiều dòng) để kiểm tra tồn kho
        Map<Integer, Integer> requestedByProduct = new LinkedHashMap<>();
        for (OrderItemLine line : request.getItems()) {
            requestedByProduct.merge(line.getProductId(), line.getQuantity(), Math::addExact);
        }

        // Kiểm tra tồn kho qua Product Service
        Map<Integer, ProductInfo> products = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : requestedByProduct.entrySet()) {
            ProductInfo product = productServiceClient.getProduct(entry.getKey(), authorizationHeader);
            checkStock(product, entry.getValue());
            products.put(entry.getKey(), product);
        }

        // Tạo các mặt hàng (một dòng cho mỗi product_id)
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : requestedByProduct.entrySet()) {
            OrderItem item = buildItem(products.get(entry.getKey()), entry.getValue());
            items.add(item);
            total = total.add(item.getTotalPrice());
        }
        checkAmount(total);

        Order order = new Order(request.getCustomerName().trim(), request.getCustomerEmail().trim());
        order.setStatus(Order.STATUS_PENDING);
        order.setTotalAmount(total);
        order = orderRepository.save(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        order.setItems(orderItemRepository.saveAll(items));
        return order;
    }

    @Transactional
    public Order updateOrder(Integer id, UpdateOrderRequest request) {
        Order order = findOrder(id);
        String newStatus = request.getStatus().trim().toLowerCase(Locale.ROOT);

        if (!Order.VALID_STATUSES.contains(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Trạng thái '" + request.getStatus() + "' không hợp lệ. Chỉ chấp nhận: pending, completed, cancelled");
        }
        if (!order.getStatus().equals(newStatus) && !Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Đơn hàng ID = " + id + " đã ở trạng thái '" + order.getStatus() + "', không thể chuyển sang '" + newStatus + "'");
        }
        if (Order.STATUS_PENDING.equals(newStatus) && !Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Không thể chuyển đơn hàng về lại trạng thái pending");
        }

        order.setStatus(newStatus);
        if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
            order.setCustomerName(request.getCustomerName().trim());
        }
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
            order.setCustomerEmail(request.getCustomerEmail().trim());
        }
        order = orderRepository.saveAndFlush(order);
        order.setItems(orderItemRepository.findByOrderIdOrderByIdAsc(id));
        return order;
    }

    @Transactional
    public void deleteOrder(Integer id) {
        Order order = findOrder(id);
        orderItemRepository.deleteByOrderId(order.getId());
        orderRepository.delete(order);
    }

    // ========================= ORDER ITEMS =========================

    @Transactional(readOnly = true)
    public List<OrderItem> getAllItems(Integer orderId) {
        if (orderId != null) {
            findOrder(orderId);
            return orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        }
        return orderItemRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public OrderItem getItem(Integer id) {
        return findItem(id);
    }

    @Transactional
    public OrderItem createItem(CreateOrderItemRequest request, String authorizationHeader) {
        Order order = findOrder(request.getOrderId());
        ensurePending(order);

        ProductInfo product = productServiceClient.getProduct(request.getProductId(), authorizationHeader);
        int alreadyOrdered = quantityInOrder(order.getId(), product.getId(), null);
        checkStock(product, alreadyOrdered + request.getQuantity());

        OrderItem item = buildItem(product, request.getQuantity());
        item.setOrderId(order.getId());
        checkAmount(order.getTotalAmount().add(item.getTotalPrice()));

        item = orderItemRepository.save(item);
        recalculateOrderTotal(order);
        return item;
    }

    @Transactional
    public OrderItem updateItem(Integer id, UpdateOrderItemRequest request, String authorizationHeader) {
        OrderItem item = findItem(id);
        Order order = findOrder(item.getOrderId());
        ensurePending(order);

        Integer productId = request.getProductId() != null ? request.getProductId() : item.getProductId();
        ProductInfo product = productServiceClient.getProduct(productId, authorizationHeader);
        int otherLines = quantityInOrder(order.getId(), productId, item.getId());
        checkStock(product, otherLines + request.getQuantity());

        // Cập nhật lại tên, giá theo Product Service tại thời điểm sửa
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(request.getQuantity());
        item.recalculateTotal();

        BigDecimal newTotal = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(i -> !i.getId().equals(item.getId()))
                .map(OrderItem::getTotalPrice)
                .reduce(item.getTotalPrice(), BigDecimal::add);
        checkAmount(newTotal);

        OrderItem saved = orderItemRepository.save(item);
        recalculateOrderTotal(order);
        return saved;
    }

    @Transactional
    public void deleteItem(Integer id) {
        OrderItem item = findItem(id);
        Order order = findOrder(item.getOrderId());
        ensurePending(order);
        orderItemRepository.delete(item);
        orderItemRepository.flush();
        recalculateOrderTotal(order);
    }

    // ============================ HELPERS ============================

    private Order findOrder(Integer id) {
        return orderRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng có ID = " + id));
    }

    private OrderItem findItem(Integer id) {
        return orderItemRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy mặt hàng có ID = " + id));
    }

    private void ensurePending(Order order) {
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Đơn hàng ID = " + order.getId() + " đã ở trạng thái '" + order.getStatus()
                            + "', không thể thay đổi mặt hàng");
        }
    }

    private void checkStock(ProductInfo product, int requestedQuantity) {
        int stock = product.getQuantity() == null ? 0 : product.getQuantity();
        if (requestedQuantity > stock) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Sản phẩm '" + product.getName() + "' (ID = " + product.getId() + ") không đủ hàng trong kho: "
                            + "yêu cầu " + requestedQuantity + ", tồn kho chỉ còn " + stock);
        }
    }

    private void checkAmount(BigDecimal amount) {
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Tổng tiền đơn hàng vượt quá giới hạn 99999999.99 (DECIMAL(10, 2))");
        }
    }

    private int quantityInOrder(Integer orderId, Integer productId, Integer excludeItemId) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .filter(i -> i.getProductId().equals(productId))
                .filter(i -> !Objects.equals(i.getId(), excludeItemId))
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    private OrderItem buildItem(ProductInfo product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(quantity);
        item.recalculateTotal();
        return item;
    }

    private void recalculateOrderTotal(Order order) {
        BigDecimal total = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        orderRepository.save(order);
    }
}
