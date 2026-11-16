# BÀI THỰC HÀNH SỐ 5 – DỊCH VỤ BÁO CÁO (REPORTING SERVICE)

**Sinh viên:** Phạm Hữu Đức – MSSV 4651050062
**Công nghệ:** Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation), H2 Database

Dịch vụ thống kê **hàng tồn, hàng bán được, doanh thu, chi phí, lợi nhuận**, lập báo cáo theo **đơn hàng** và theo **sản phẩm**. Dịch vụ được xây dựng theo kiến trúc **SOA**:

| Yêu cầu của đề | Cách đáp ứng |
|---|---|
| Chạy độc lập trên một cổng mới (`localhost:***3`) | Cổng **8083** (Auth 8080, Product 8081, Order 8082) |
| Cơ sở dữ liệu riêng | Database **`reportdb`** riêng (bảng `orders_reports`, `product_reports`) |
| 8 API `/reports/products`, `/reports/orders` | `ProductReportController`, `OrderReportController` + `ReportService` |
| Báo cáo đơn hàng dựa trên dữ liệu dịch vụ đơn hàng | `OrderServiceClient` gọi **`GET localhost:8082/orders/{id}`** (bài 4) |
| Báo cáo sản phẩm dựa trên dữ liệu dịch vụ sản phẩm + đơn hàng | `ProductServiceClient` gọi **`GET :8081/products/{id}`** (bài 3) và `OrderServiceClient` gọi **`GET :8082/orders`** (bài 4) |
| Xác thực qua service ở bài thực hành số 2 | `AuthInterceptor` gửi token sang **`GET localhost:8080/auth`** (bài 2) |

---

## 1. Kiến trúc

```
                                  +------------------+
                                  |      CLIENT      |
                                  |     (Postman)    |
                                  +------------------+
                                           | /reports/...  Authorization: Bearer <token>
                                           v
+----------------------+  GET /auth  +----------------------------+  GET /orders, /orders/{id}  +----------------------+
| AUTH SERVICE (Bài 2) |<------------|  REPORT SERVICE (Bài 5)    |---------------------------->| ORDER SERVICE (Bài 4)|
| localhost:8080       |------------>|  localhost:8083            |<----------------------------| localhost:8082       |
+----------------------+  200 / 401  |  - AuthInterceptor         |  đơn hàng + mặt hàng        +----------------------+
                                     |  - OrderReportController   |
                                     |  - ProductReportController |  GET /products/{id}         +----------------------+
                                     |  - ReportService           |---------------------------->|PRODUCT SERVICE(Bài 3)|
                                     +-------------+--------------+<----------------------------| localhost:8081       |
                                                   |                  tên, tồn kho              +----------------------+
                                            +------v-------+
                                            |   reportdb   |
                                            | orders_reports |
                                            | product_reports|
                                            +----------------+
```

- Report Service **không đọc CSDL** của service khác. Mọi dữ liệu đều lấy qua REST API, và service chuyển tiếp nguyên token của người dùng khi gọi.
- `order_id` và `product_id` chỉ **lưu vết** (không có khóa ngoại sang CSDL khác).

---

## 2. Công thức và quy tắc nghiệp vụ

| Chỉ tiêu | Công thức / nguồn dữ liệu |
|---|---|
| Hàng bán được (`total_sold`) | Σ `quantity` của sản phẩm trong các đơn hàng (Order Service) |
| Hàng tồn (`stock_quantity`) | `quantity` hiện tại của sản phẩm bên Product Service, lúc lập báo cáo |
| Doanh thu (`revenue`) | Σ `quantity × unit_price` (giá bán tại thời điểm đặt hàng) |
| Chi phí (`cost`) | Σ `quantity × unit_cost` (giá nhập) |
| Lợi nhuận (`profit`) | `revenue − cost` |
| Tổng đơn (`total_revenue`, `total_cost`, `total_profit`) | Tổng các dòng `product_reports` của đơn đó |

**Giá nhập (`unit_cost`)**: Product Service (bài 3) không lưu giá nhập, nên giá nhập là **tham số đầu vào** khi tạo báo cáo:
- Gửi `unit_cost` thì dùng đúng giá đó.
- Không gửi thì giá nhập = giá bán × **`report.default-cost-ratio`** (mặc định `0.7`, tức 70% giá bán; đổi được trong `application.properties`).

**Quy tắc**

| Quy tắc | Chi tiết |
|---|---|
| Chỉ tính đơn đã hoàn thành | Chỉ đơn `completed` mới có doanh thu. Báo cáo đơn `pending` hoặc `cancelled` trả **409**. Báo cáo sản phẩm bỏ qua các đơn này. |
| Mỗi đơn 1 báo cáo | Lập báo cáo lần 2 cho cùng `order_id` trả **409** (xóa báo cáo cũ rồi lập lại). |
| Báo cáo đơn hàng | Tạo 1 dòng `orders_reports` và mỗi sản phẩm trong đơn 1 dòng `product_reports` (có `order_report_id`). |
| Báo cáo sản phẩm | 1 dòng `product_reports` với `order_report_id = null`, cộng dồn trên **mọi** đơn đã hoàn thành. Có thể lập nhiều lần (mỗi lần là một bản chụp số liệu). |
| Xóa | Xóa báo cáo đơn thì xóa luôn các dòng sản phẩm của nó. Xóa 1 dòng sản phẩm thuộc báo cáo đơn thì tổng của báo cáo đơn được **tính lại**. |
| Giới hạn tiền | `DECIMAL(10, 2)` nên tối đa 99 999 999.99. Nếu vượt thì trả **400**. |
| Service khác bị tắt | Auth, Product hoặc Order không phản hồi thì trả **503** kèm hướng dẫn bật service. |

**Cột bổ sung ngoài phụ lục** (để báo cáo đủ thông tin): `product_reports.product_name`, `product_reports.stock_quantity` (hàng tồn), và `created_at` ở cả 2 bảng.

---

## 3. Danh sách API (cổng 8083)

Mọi API đều cần header `Authorization: Bearer <token>`. Phản hồi có dạng `{ "success", "message", "data" }`.

| Method | URL | Body mẫu | Kết quả |
|---|---|---|---|
| GET | `/reports/products` (lọc: `?order_report_id=1`) | – | 200 |
| GET | `/reports/products/{id}` | – | 200 / 404 |
| POST | `/reports/products` | `{"product_id":3,"unit_cost":2000000}` (`unit_cost` tùy chọn) | 201 / 400 / 404 |
| DELETE | `/reports/products/{id}` | – | 200 / 404 |
| GET | `/reports/orders` | – | 200 |
| GET | `/reports/orders/{id}` | – | 200 / 404 |
| POST | `/reports/orders` | `{"order_id":1,"cost_prices":[{"product_id":1,"unit_cost":25000000},{"product_id":3,"unit_cost":2000000}]}` (`cost_prices` tùy chọn) | 201 / 400 / 404 / 409 |
| DELETE | `/reports/orders/{id}` | – | 200 / 404 |

Ví dụ: đơn hàng gồm Laptop ×1 (28 500 000) và Chuột ×2 (2 490 000), giá nhập lần lượt 25 000 000 và 2 000 000. Phản hồi của `POST /reports/orders` (201):

```json
{
  "success": true,
  "message": "Tạo báo cáo đơn hàng thành công",
  "data": {
    "id": 1, "order_id": 1,
    "total_revenue": 33480000.00, "total_cost": 29000000.00, "total_profit": 4480000.00,
    "created_at": "2026-11-16T10:05:46",
    "product_reports": [
      { "id": 1, "order_report_id": 1, "product_id": 1, "product_name": "Laptop Dell XPS 15",
        "total_sold": 1, "stock_quantity": 15,
        "revenue": 28500000.00, "cost": 25000000.00, "profit": 3500000.00 },
      { "id": 2, "order_report_id": 1, "product_id": 3, "product_name": "Chuột Logitech MX Master 3S",
        "total_sold": 2, "stock_quantity": 50,
        "revenue": 4980000.00, "cost": 4000000.00, "profit": 980000.00 }
    ]
  }
}
```

---

## 4. Cách chạy

Cần **Java 21**. Bật đủ **4 service** theo thứ tự (mỗi service 1 cửa sổ terminal):

```bash
# Bài 2 - Auth (8080) - ở thư mục gốc repo
mvnw.cmd spring-boot:run
# Bài 3 - Product (8081)
cd product-service && mvnw.cmd spring-boot:run
# Bài 4 - Order (8082)
cd order-service && mvnw.cmd spring-boot:run
# Bài 5 - Report (8083)
cd report-service && mvnw.cmd spring-boot:run
```

(macOS / Linux thay `mvnw.cmd` bằng `sh mvnw`.)

Test tự động (không cần bật service nào khác): `cd report-service && mvnw.cmd test` (5 test).

Xem CSDL: http://localhost:8083/h2-console (JDBC URL `jdbc:h2:file:./data/reportdb`, User `sa`, Password để trống).

---

## 5. Kiểm thử bằng Postman

1. **Import** file `report-service/Postman_Collection_BTH5.json` (Postman ở **Cloud View**, không dùng Local View).
2. Bật đủ 4 service.
3. Chuột phải vào collection → **Run collection** → **Run**.
4. **Đạt: 31 request, Passed 72 / Failed 0.**

Nhóm 0 tự tạo dữ liệu mẫu ở Order Service (một đơn `completed`, một đơn `pending`). Nhóm 5 tự xóa dữ liệu mẫu, nên chạy lại bao nhiêu lần cũng được.

| # | Kiểm tra | Kết quả đúng |
|---|---|---|
| 0.1 – 0.4 | Đăng nhập, tạo đơn mẫu, hoàn thành đơn, tạo đơn pending | 200 / 201 / 200 / 201 |
| 1.1 / 1.2 | Không có token / token sai | 401 |
| 2.1 | Lập báo cáo đơn hàng | 201; doanh thu 33 480 000, chi phí 29 000 000, lợi nhuận 4 480 000 |
| 2.2 / 2.3 / 2.4 / 2.5 | Trùng / đơn pending / đơn không tồn tại / thiếu `order_id` | 409 / 409 / 404 / 400 |
| 2.6 – 2.8 | Danh sách / chi tiết / ID không tồn tại | 200 / 200 / 404 |
| 3.1 | Báo cáo sản phẩm Chuột có giá nhập | 201; `cost = total_sold × 2 000 000`, `profit = revenue − cost` |
| 3.2 | Báo cáo Laptop không gửi giá nhập | 201; `cost = 70% revenue` |
| 3.3 / 3.4 | Sản phẩm không tồn tại / giá nhập âm | 404 / 400 |
| 3.5 – 3.8 | Danh sách, lọc theo báo cáo đơn, chi tiết, ID không tồn tại | 200 / 200 (2 dòng) / 200 / 404 |
| 4.1 – 4.7 | Xóa báo cáo sản phẩm, xóa 1 dòng của báo cáo đơn (tổng được tính lại còn 4 980 000), xóa báo cáo đơn | 200 / 404 / 200 / 200 / 200 / 404 / 404 |
| 5.1 / 5.2 | Dọn đơn hàng mẫu | 200 |
