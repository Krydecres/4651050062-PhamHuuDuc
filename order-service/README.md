# BÀI THỰC HÀNH SỐ 4 – DỊCH VỤ QUẢN LÝ ĐƠN HÀNG (ORDER SERVICE)

**Sinh viên:** Phạm Hữu Đức – MSSV 4651050062
**Công nghệ:** Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation), H2 Database

Dịch vụ RESTful quản lý đơn hàng và chi tiết đơn hàng, xây dựng theo kiến trúc **SOA**:

| Yêu cầu của đề                                        | Cách đáp ứng                                                                 |
|-------------------------------------------------------|------------------------------------------------------------------------------|
| Chạy độc lập trên một cổng mới (`localhost:***2`)     | Cổng **8082** (Auth 8080, Product 8081)                                      |
| Cơ sở dữ liệu riêng                                   | Database **`orderdb`** riêng (bảng `orders`, `order_items`)                  |
| 5 API `/orders` + 5 API `/order_items`                | `OrderController`, `OrderItemController` + `OrderService`                    |
| Tạo đơn hàng **kiểm tra tồn kho**                     | `ProductServiceClient` gọi **`GET localhost:8081/products/{id}`** (bài 3)    |
| Cập nhật trạng thái đơn hàng                          | `PUT /orders/{id}` với `pending → completed / cancelled`                     |
| Xác thực qua service ở bài thực hành số 2             | `AuthInterceptor` gửi token sang **`GET localhost:8080/auth`** (bài 2)       |

---

## 1. Kiến trúc

```
                               +-----------------------+
                               |        CLIENT         |
                               |      (Postman)        |
                               +-----------------------+
          1. POST / (đăng nhập)    |               | 3. /orders, /order_items
             -> nhận JWT token     |               |    Authorization: Bearer <token>
                                   v               v
+---------------------------+  2. GET /auth  +---------------------------+  4. GET /products/{id}  +---------------------------+
|  AUTH SERVICE (Bài 2)     |<---------------|  ORDER SERVICE (Bài 4)    |----------------------->| PRODUCT SERVICE (Bài 3)   |
|  localhost:8080           |--------------->|  localhost:8082           |<-----------------------| localhost:8081            |
|  kiểm tra token hợp lệ    |  200 / 401     |  - AuthInterceptor        |  tên, giá, tồn kho     | (cũng xác thực qua :8080) |
+---------------------------+                |  - OrderController        |                        +-------------+-------------+
                                             |  - OrderItemController    |                                      |
                                             +-------------+-------------+                               +------v------+
                                                           |                                             |  productdb  |
                                                    +------v------+                                      +-------------+
                                                    |   orderdb   |
                                                    | orders      |
                                                    | order_items |
                                                    +-------------+
```

- Order Service **không đọc CSDL của Product Service**. Muốn biết tên, giá, số lượng tồn kho thì gọi API của Product Service (chuyển tiếp nguyên token của người dùng).
- `order_items.product_id` chỉ **lưu vết** ID sản phẩm, không có khóa ngoại sang CSDL khác.
- `product_name`, `unit_price` được **chụp lại tại thời điểm đặt hàng**, nên đổi giá sản phẩm sau này không làm sai đơn cũ.

### Quy tắc nghiệp vụ

| Quy tắc | Chi tiết |
|---|---|
| Kiểm tra tồn kho | Khi tạo đơn, thêm hoặc sửa mặt hàng: tổng số lượng đặt của **cùng một sản phẩm trong đơn** phải ≤ `quantity` tồn kho bên Product Service. Nếu vượt thì trả **409**. |
| Sản phẩm không tồn tại | Product Service trả 404, Order Service trả lại **404** kèm thông báo. |
| Tính tiền tự động | `total_price = quantity × unit_price`; `total_amount` = tổng `total_price`, tự tính lại mỗi khi thêm, sửa hoặc xóa mặt hàng. |
| Giới hạn tiền | `DECIMAL(10, 2)` nên tối đa 99 999 999.99. Nếu vượt thì trả **400**. |
| Trạng thái | Chỉ nhận `pending`, `completed`, `cancelled`. Chỉ đi được `pending → completed` hoặc `pending → cancelled`. Đơn đã `completed`/`cancelled` thì không đổi trạng thái được nữa (**409**). |
| Khóa đơn | Đơn không còn `pending` thì không thêm, sửa, xóa mặt hàng được (**409**). |
| Xóa đơn | Xóa luôn toàn bộ mặt hàng của đơn đó. |
| Service khác bị tắt | Auth hoặc Product Service không phản hồi thì trả **503** kèm hướng dẫn bật service. |

> Đề bài yêu cầu *kiểm tra* tồn kho nên service chỉ kiểm tra, **không trừ** số lượng bên Product Service.

---

## 2. Cấu trúc thư mục

```
order-service/
├── pom.xml
├── Postman_Collection_BTH4.json        <- import vào Postman để kiểm thử
├── src/main/resources/application.properties   (cổng 8082, orderdb, URL auth/product)
├── src/main/java/com/example/orderservice/
│   ├── OrderServiceApplication.java
│   ├── model/        Order.java, OrderItem.java            (bảng orders, order_items)
│   ├── repository/   OrderRepository, OrderItemRepository
│   ├── dto/          CreateOrderRequest, UpdateOrderRequest, CreateOrderItemRequest,
│   │                 UpdateOrderItemRequest, OrderItemLine, ProductInfo, ApiResponse, ApiException ...
│   ├── service/      OrderService (nghiệp vụ), ProductServiceClient (gọi :8081),
│   │                 AuthServiceClient (gọi :8080)
│   ├── controller/   OrderController (/orders), OrderItemController (/order_items)
│   ├── filter/       AuthInterceptor (chặn request chưa xác thực)
│   └── config/       WebMvcConfig, GlobalExceptionHandler
└── src/test/java/.../OrderApiTests.java   (6 test tự động, giả lập Auth + Product)
```

---

## 3. Danh sách API (cổng 8082)

Mọi API đều cần header `Authorization: Bearer <token>` (lấy token từ Auth Service bài 2).
Mọi phản hồi đều có dạng `{ "success": true/false, "message": "...", "data": ... }`.

### Đơn hàng – `/orders`

| Method | URL | Body mẫu | Kết quả |
|---|---|---|---|
| GET | `/orders` | – | 200, danh sách đơn (kèm `items`) |
| GET | `/orders/{id}` | – | 200 / 404 |
| POST | `/orders` | `{"customer_name":"Nguyễn Văn A","customer_email":"a@gmail.com","items":[{"product_id":1,"quantity":1},{"product_id":3,"quantity":2}]}` | 201 / 400 / 404 / 409 |
| PUT | `/orders/{id}` | `{"status":"completed"}` (tùy chọn kèm `customer_name`, `customer_email`) | 200 / 400 / 404 / 409 |
| DELETE | `/orders/{id}` | – | 200 / 404 |

### Chi tiết đơn hàng – `/order_items`

| Method | URL | Body mẫu | Kết quả |
|---|---|---|---|
| GET | `/order_items` (lọc: `?order_id=1`) | – | 200 |
| GET | `/order_items/{id}` | – | 200 / 404 |
| POST | `/order_items` | `{"order_id":1,"product_id":3,"quantity":1}` | 201 / 400 / 404 / 409 |
| PUT | `/order_items/{id}` | `{"quantity":3}` (tùy chọn `product_id` để đổi sản phẩm) | 200 / 400 / 404 / 409 |
| DELETE | `/order_items/{id}` | – | 200 / 404 |

Ví dụ phản hồi `POST /orders` (201):

```json
{
  "success": true,
  "message": "Tạo đơn hàng thành công",
  "data": {
    "id": 1,
    "customer_name": "Nguyễn Văn A",
    "customer_email": "a@gmail.com",
    "total_amount": 33480000.00,
    "status": "pending",
    "created_at": "2026-11-15T13:33:22",
    "updated_at": "2026-11-15T13:33:22",
    "items": [
      { "id": 1, "order_id": 1, "product_id": 1, "product_name": "Laptop Dell XPS 15",
        "quantity": 1, "unit_price": 28500000.00, "total_price": 28500000.00 },
      { "id": 2, "order_id": 1, "product_id": 3, "product_name": "Chuột Logitech MX Master 3S",
        "quantity": 2, "unit_price": 2490000.00, "total_price": 4980000.00 }
    ]
  }
}
```

---

## 4. Cách chạy

Cần **Java 21**. Không cần cài Maven (đã có `mvnw`), không cần cài CSDL (H2 tự tạo file `./data/orderdb.mv.db`).

Mở **3 cửa sổ terminal**, chạy theo thứ tự:

```bash
# Terminal 1 - Auth Service (bài 2) - cổng 8080 - ở thư mục gốc repo
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# Terminal 2 - Product Service (bài 3) - cổng 8081
cd product-service
./mvnw spring-boot:run

# Terminal 3 - Order Service (bài 4) - cổng 8082
cd order-service
./mvnw spring-boot:run
```

Chạy test tự động (không cần bật 2 service kia):

```bash
cd order-service
./mvnw test
```

Xem dữ liệu trong CSDL: http://localhost:8082/h2-console
(JDBC URL `jdbc:h2:file:./data/orderdb`, User `sa`, Password để trống)

---

## 5. Kiểm thử bằng Postman

1. Postman → **Import** → chọn file `order-service/Postman_Collection_BTH4.json`.
2. Bật đủ 3 service (mục 4).
3. Chạy request **0.1 Đăng nhập** (`admin` / `123456`). Token được tự lưu vào biến `{{jwt_token}}` và mọi request khác dùng biến này.
4. Chạy lần lượt từ trên xuống, hoặc bấm chuột phải vào collection → **Run collection** → **Run**.
5. Mỗi request đã có sẵn test tự động (tab **Test Results**). Kết quả mong đợi: **27 request, 57 assertion, 0 failed**.
