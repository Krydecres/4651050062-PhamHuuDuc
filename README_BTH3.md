# BÀI THỰC HÀNH SỐ 3: DỊCH VỤ QUẢN LÝ SẢN PHẨM (PRODUCT MANAGEMENT SERVICE)
**Kiến trúc Hướng dịch vụ (SOA - Service-Oriented Architecture)**

---

## 1. Tổng quan Kiến trúc

Hệ thống được thiết kế theo đúng nguyên tắc **Kiến trúc SOA**:
- **Tính độc lập:** Mỗi dịch vụ chạy trên một tiến trình và cổng (Port) riêng.
- **Database-per-Service:** Mỗi dịch vụ quản lý cơ sở dữ liệu riêng, không truy cập chéo trực tiếp vào DB của nhau.
- **Giao tiếp qua API & Xác thực tập trung:** Mọi yêu cầu thao tác tới Dịch vụ Quản lý Sản phẩm (Product Service) đều được chặn và kiểm tra tính hợp lệ của JWT Token thông qua Dịch vụ Xác thực (Auth Service) ở Bài Thực Hành 2.

```
+-------------------------------------------------------+
|                       CLIENT                          |
|             (Postman / cURL / Frontend)               |
+-------------------------------------------------------+
          |                                  |
          | 1. POST /login                   | 3. GET/POST/PUT/DELETE /products
          |    (userName, password)          |    (Header: Authorization: Bearer <token>)
          v                                  v
+-------------------------------+  2. Check  +--------------------------------+
|     AUTH SERVICE (BTH 2)      |<-----------|    PRODUCT SERVICE (BTH 3)     |
|       (localhost:8080)        |   Token    |        (localhost:8081)        |
+-------------------------------+  (/auth)   +--------------------------------+
|       Database: User DB       |            |    Database: H2 (productdb)    |
|         (Bảng User)           |            |        (Bảng products)         |
+-------------------------------+            +--------------------------------+
```

---

## 2. Thông số Dịch vụ

| Dịch vụ | Cổng (Port) | Cơ sở dữ liệu | Chức năng chính |
| :--- | :--- | :--- | :--- |
| **Auth Service** (BTH2) | `8080` | Bảng `User` | Đăng nhập (`POST /login` hoặc `/`), Xác thực JWT (`GET/POST /auth`) |
| **Product Service** (BTH3) | `8081` | H2 DB (`productdb`) | CRUD Sản phẩm (`/products`), H2 Web Console (`/h2-console`) |

---

## 3. Cấu trúc Bảng Cơ Sở Dữ Liệu (`products`)

Được ánh xạ tự động qua Spring Data JPA theo đúng chuẩn phụ lục đề bài:

| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `INT` | `PRIMARY KEY, AUTO_INCREMENT` | ID duy nhất của sản phẩm |
| `name` | `VARCHAR(255)` | `NOT NULL` | Tên sản phẩm |
| `description` | `TEXT` | `NULLABLE` | Mô tả chi tiết sản phẩm |
| `price` | `DECIMAL(10, 2)`| `NOT NULL` | Giá bán sản phẩm |
| `quantity` | `INT` | `NOT NULL` | Số lượng tồn kho |
| `created_at` | `TIMESTAMP` | `NOT NULL, UPTODATE=FALSE` | Ngày tạo sản phẩm (tự động) |
| `updated_at` | `TIMESTAMP` | `NOT NULL` | Ngày cập nhật lần cuối (tự động) |

> **Xem trực quan bảng products qua H2 Console:**
> - Đường dẫn: `http://localhost:8081/h2-console`
> - JDBC URL: `jdbc:h2:mem:productdb`
> - User Name: `sa`
> - Password: *(để trống)*

*(Nếu muốn chuyển sang MySQL, chỉ cần mở comment 5 dòng cấu hình MySQL trong `product-service/src/main/resources/application.properties`)*.

---

## 4. Đặc tả RESTful API (Product Service - Cổng 8081)

Tất cả các API dưới đây **bắt buộc** phải kèm Header:
`Authorization: Bearer <jwt_token>`

| STT | Phương thức | Endpoint | Mô tả | Mã HTTP thành công |
| :---: | :---: | :--- | :--- | :---: |
| 1 | `GET` | `/products` | Lấy danh sách tất cả sản phẩm | `200 OK` |
| 2 | `GET` | `/products/{id}` | Lấy chi tiết một sản phẩm theo ID | `200 OK` (hoặc `404 Not Found`) |
| 3 | `POST` | `/products` | Thêm mới một sản phẩm | `201 Created` (hoặc `400 Bad Request`) |
| 4 | `PUT` | `/products/{id}` | Cập nhật thông tin sản phẩm theo ID | `200 OK` (hoặc `404 Not Found`) |
| 5 | `DELETE` | `/products/{id}` | Xóa một sản phẩm theo ID | `200 OK` (hoặc `404 Not Found`) |

### Định dạng Body khi POST / PUT:
```json
{
    "name": "Bàn phím cơ Keychron K2 Pro",
    "description": "Bàn phím cơ không dây Bluetooth, Gateron Brown Switch",
    "price": 2150000.00,
    "quantity": 30
}
```

---

## 5. Hướng dẫn Khởi chạy và Kiểm thử

### Bước 1: Khởi động Auth Service (BTH2 - Port 8080)
Mở Terminal 1 tại thư mục `demo`:
```powershell
.\mvnw.cmd spring-boot:run
```
*(Auth Service sẽ khởi động tại `http://localhost:8080`)*

### Bước 2: Khởi động Product Service (BTH3 - Port 8081)
Mở Terminal 2 tại thư mục `demo/product-service`:
```powershell
cd product-service
.\mvnw.cmd spring-boot:run
```
*(Product Service sẽ khởi động tại `http://localhost:8081`, tự động nạp 3 sản phẩm mẫu)*

---

### Bước 3: Chạy Kiểm Thử Tự Động (Test Suite)
Tại thư mục `demo`, chạy file script PowerShell có sẵn:
```powershell
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
```
Script sẽ tự động chạy qua **9 test cases**:
1. `GET /products` không có token -> Trả về `401 Unauthorized`.
2. `GET /products` với token giả mạo -> Trả về `401 Unauthorized`.
3. Đăng nhập Auth Service (`admin` / `123456`) -> Nhận JWT Token hợp lệ.
4. `GET /products` với Token hợp lệ -> Trả về danh sách sản phẩm mẫu (`200 OK`).
5. `GET /products/1` -> Trả về chi tiết sản phẩm 1 (`200 OK`).
6. `POST /products` -> Thêm sản phẩm mới (`201 Created`).
7. `PUT /products/{id}` -> Cập nhật sản phẩm vừa thêm (`200 OK`).
8. `DELETE /products/{id}` -> Xóa sản phẩm (`200 OK`).
9. `GET /products/{id}` sau khi xóa -> Trả về `404 Not Found`.

---

### Bước 4: Kiểm thử bằng Postman
File `Postman_Collection_BTH3.json` đã được tạo sẵn trong thư mục dự án:
1. Mở Postman, chọn **Import** -> chọn file `Postman_Collection_BTH3.json`.
2. Chạy request **"1.1 Đăng nhập lấy Token JWT"** (sẽ tự động lưu token vào biến `{{jwt_token}}`).
3. Chạy lần lượt các request trong thư mục **"2. Product Service (Port 8081)"** và **"3. Kiểm thử Bảo mật"**.
