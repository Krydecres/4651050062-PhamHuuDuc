# BÀI THỰC HÀNH SỐ 3 – DỊCH VỤ QUẢN LÝ SẢN PHẨM (PRODUCT SERVICE)

**Sinh viên:** Phạm Hữu Đức – MSSV 4651050062
**Công nghệ:** Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation), H2 Database

Dịch vụ RESTful quản lý sản phẩm (tạo, xem, cập nhật, xoá), xây dựng theo kiến trúc **SOA**:

| Yêu cầu của đề                                   | Cách đáp ứng                                                        |
|--------------------------------------------------|---------------------------------------------------------------------|
| Chạy độc lập trên một cổng mới (`localhost:***1`) | Cổng **8081** (Auth Service bài 2 chạy cổng 8080)                   |
| Cơ sở dữ liệu riêng                              | Database **`productdb`** riêng, chỉ Product Service truy cập         |
| 5 API `GET/POST/PUT/DELETE /products`            | `ProductController` + `ProductService` + `ProductRepository`        |
| Xác thực qua service ở bài thực hành số 2        | `AuthInterceptor` gửi token sang **`GET localhost:8080/auth`** (bài 2) |

---

## 1. Kiến trúc

```
                         +---------------------------+
                         |          CLIENT           |
                         | (Postman / PowerShell)    |
                         +---------------------------+
        1. POST /  (đăng nhập)  |            | 3. GET/POST/PUT/DELETE /products
           userName, password   |            |    Authorization: Bearer <token>
                                v            v
+-------------------------------+  2. GET /auth   +--------------------------------+
|   AUTH SERVICE (Bài 2)        |<----------------|   PRODUCT SERVICE (Bài 3)      |
|   localhost:8080              |---------------->|   localhost:8081               |
|   - POST /     : đăng nhập    |  200 hợp lệ /   |   - AuthInterceptor            |
|   - GET  /auth : kiểm tra JWT |  401 không hợp lệ|   - ProductController (CRUD)  |
+---------------+---------------+                 +---------------+----------------+
                |                                                 |
         +------v------+                                   +------v------+
         | Bảng User   |                                   |  productdb  |
         +-------------+                                   | (products)  |
                                                           +-------------+
```

Hai service **không dùng chung code, không dùng chung database**, chỉ nói chuyện với nhau qua HTTP:

- Product Service **không biết** mật khẩu, không biết secret key JWT của bài 2.
- Muốn biết token có hợp lệ không, Product Service phải **hỏi Auth Service** qua API `/auth`.
- Nếu sau này đổi cách xác thực ở bài 2, Product Service không phải sửa gì (liên kết lỏng – *loose coupling*).

### Luồng xử lý một request

```
Request -> AuthInterceptor ──(gọi GET :8080/auth)──> hợp lệ? ──không──> 401 / 503
                                                       │ có
                                                       v
           ProductController -> (kiểm tra @Valid) -> ProductService -> ProductRepository -> productdb
                                                       │
                                                       v
                                          JSON { success, message, data }
```

---

## 2. Cấu trúc thư mục

```
product-service/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/                       # Maven Wrapper (không cần cài Maven)
├── test-api.ps1                                # Script kiểm thử tự động 9 test case
├── Postman_Collection_BTH3.json                # Bộ request Postman
└── src/main/
    ├── resources/application.properties        # Cổng 8081, CSDL, địa chỉ Auth Service
    └── java/com/example/productservice/
        ├── ProductServiceApplication.java      # Hàm main
        ├── model/Product.java                  # Entity ánh xạ bảng products
        ├── dto/
        │   ├── ProductRequest.java             # Dữ liệu client gửi lên + ràng buộc kiểm tra
        │   ├── ApiResponse.java                # Khuôn JSON trả về { success, message, data }
        │   └── AuthValidationResult.java       # Kết quả Auth Service trả về
        ├── repository/ProductRepository.java   # Truy vấn CSDL (Spring Data JPA)
        ├── service/
        │   ├── ProductService.java             # Nghiệp vụ CRUD sản phẩm
        │   └── AuthServiceClient.java          # Gọi sang Auth Service bài 2
        ├── filter/AuthInterceptor.java         # Chặn mọi request /products để xác thực
        ├── controller/ProductController.java   # 5 API RESTful
        └── config/
            ├── WebMvcConfig.java               # Đăng ký AuthInterceptor cho /products/**
            ├── GlobalExceptionHandler.java     # Bắt lỗi tập trung, trả JSON thống nhất
            └── DataInitializer.java            # Nạp 3 sản phẩm mẫu khi bảng còn trống
```

### Vai trò từng lớp

| Lớp | Việc làm |
|-----|----------|
| **`AuthInterceptor`** | Chạy *trước* mọi request vào `/products`. Lấy token trong header `Authorization: Bearer <token>`, nhờ `AuthServiceClient` kiểm tra. Không có token / token sai → trả **401**; Auth Service tắt → trả **503**; hợp lệ → cho đi tiếp và lưu `userName`, `idUser` vào request. |
| **`AuthServiceClient`** | Dùng `RestClient` gọi `GET http://localhost:8080/auth` kèm header của client. `AuthController` bài 2 trả `200 {success, userName, idUser}` nếu token đúng, `401` nếu sai/hết hạn. Có timeout 5 giây để service không bị treo. |
| **`ProductController`** | Nhận request, kiểm tra dữ liệu bằng `@Valid`, gọi `ProductService`, trả mã HTTP phù hợp (200 / 201 / 400 / 404). |
| **`ProductService`** | Nghiệp vụ: lấy danh sách (sắp xếp theo `id`), lấy chi tiết, thêm, cập nhật, xoá. Các thao tác ghi chạy trong `@Transactional`. |
| **`ProductRepository`** | Kế thừa `JpaRepository` nên có sẵn `findAll`, `findById`, `save`, `existsById`, `deleteById`. Hibernate sinh câu SQL có tham số → chống SQL Injection. |
| **`Product`** | Entity ánh xạ đúng bảng `products` của đề. `@PrePersist` / `@PreUpdate` tự gán `created_at`, `updated_at`. |
| **`GlobalExceptionHandler`** | Gom mọi lỗi (sai dữ liệu, sai JSON, sai kiểu id, sai đường dẫn, lỗi hệ thống) về JSON `{ "success": false, "message": "..." }`. |

---

## 3. Cơ sở dữ liệu – bảng `products`

Bảng được Hibernate tự tạo (`ddl-auto=update`) theo entity `Product`, đúng phụ lục đề bài:

| Tên cột       | Kiểu dữ liệu      | Ràng buộc                    | Mô tả                                |
|---------------|-------------------|------------------------------|--------------------------------------|
| `id`          | INT               | PRIMARY KEY, AUTO_INCREMENT  | ID duy nhất của sản phẩm             |
| `name`        | VARCHAR(255)      | NOT NULL                     | Tên sản phẩm                         |
| `description` | TEXT              | cho phép NULL                | Mô tả sản phẩm                       |
| `price`       | DECIMAL(10, 2)    | NOT NULL, >= 0               | Giá sản phẩm                         |
| `quantity`    | INT               | NOT NULL, >= 0               | Số lượng sản phẩm trong kho          |
| `created_at`  | TIMESTAMP         | NOT NULL, không cho sửa      | Ngày sản phẩm được tạo (tự động)     |
| `updated_at`  | TIMESTAMP         |                              | Ngày cập nhật lần cuối (tự động)     |

**CSDL dùng H2 lưu ra file** `product-service/data/productdb.mv.db`:
không cần cài đặt gì thêm, tắt service bật lại dữ liệu vẫn còn. Lần chạy đầu bảng trống nên
`DataInitializer` tự thêm 3 sản phẩm mẫu.

Xem bảng trực tiếp tại **H2 Console**: <http://localhost:8081/h2-console>

| Trường    | Giá trị                        |
|-----------|--------------------------------|
| JDBC URL  | `jdbc:h2:file:./data/productdb` |
| User Name | `sa`                           |
| Password  | *(để trống)*                   |

> Muốn dùng **MySQL**: trong `application.properties` comment khối H2 và mở comment khối MySQL
> (database `product_db` sẽ tự được tạo). Driver MySQL đã có sẵn trong `pom.xml`.

---

## 4. Đặc tả API (cổng 8081)

Tất cả API **bắt buộc** có header: `Authorization: Bearer <token>`
(token lấy khi đăng nhập ở Auth Service bài 2).

| STT | Method   | Endpoint          | Chức năng                         | Thành công      | Lỗi có thể gặp      |
|:---:|:--------:|-------------------|-----------------------------------|-----------------|---------------------|
| 1   | `GET`    | `/products`       | Lấy danh sách tất cả sản phẩm     | `200 OK`        | 401, 503            |
| 2   | `GET`    | `/products/{id}`  | Lấy chi tiết một sản phẩm         | `200 OK`        | 400, 401, 404       |
| 3   | `POST`   | `/products`       | Thêm một sản phẩm mới             | `201 Created`   | 400, 401            |
| 4   | `PUT`    | `/products/{id}`  | Cập nhật thông tin sản phẩm       | `200 OK`        | 400, 401, 404       |
| 5   | `DELETE` | `/products/{id}`  | Xoá một sản phẩm                  | `200 OK`        | 400, 401, 404       |

### Body cho `POST` / `PUT`

`name`, `price`, `quantity` bắt buộc; `description` không bắt buộc.

```json
{
    "name": "Bàn phím cơ Keychron K2 Pro",
    "description": "Bàn phím cơ không dây Bluetooth, Switch Gateron Brown",
    "price": 2150000.00,
    "quantity": 30
}
```

### Ví dụ phản hồi

`GET /products/1` → `200 OK`
```json
{
    "success": true,
    "message": "Lấy chi tiết sản phẩm thành công",
    "data": {
        "id": 1,
        "name": "Laptop Dell XPS 15",
        "description": "Laptop văn phòng cao cấp, Intel Core i7, 16GB RAM, 512GB SSD",
        "price": 28500000.00,
        "quantity": 15,
        "createdAt": "2026-10-17T12:30:00.123",
        "updatedAt": "2026-10-17T12:30:00.123"
    }
}
```

`GET /products/999` → `404 Not Found`
```json
{ "success": false, "message": "Không tìm thấy sản phẩm có ID = 999" }
```

`POST /products` với `"price": -5` → `400 Bad Request`
```json
{ "success": false, "message": "Giá sản phẩm phải lớn hơn hoặc bằng 0" }
```

### Bảng mã lỗi

| Mã  | Khi nào xảy ra |
|-----|----------------|
| 400 | Thiếu/sai dữ liệu (`name` trống, `price` < 0, `quantity` âm…), body không phải JSON, `id` không phải số |
| 401 | Không gửi token, token sai hoặc hết hạn (Auth Service bài 2 từ chối) |
| 404 | Không có sản phẩm với `id` đó, hoặc đường dẫn không tồn tại |
| 405 | Gọi sai phương thức (vd: `PATCH /products/1`) |
| 503 | Không kết nối được Auth Service (chưa bật bài 2 ở cổng 8080) |
| 500 | Lỗi hệ thống khác (chi tiết chỉ ghi log ở server) |

---

## 5. Cách chạy

**Yêu cầu:** JDK 21. Không cần cài Maven (đã có Maven Wrapper).

**Bước 1 – Bật Auth Service (bài 2)**, cổng 8080:
```powershell
cd <thư mục bài 2>          # thư mục demo
.\mvnw.cmd spring-boot:run
```

**Bước 2 – Bật Product Service (bài 3)**, cổng 8081 (mở terminal thứ hai):
```powershell
cd product-service
.\mvnw.cmd spring-boot:run
```

Khởi động xong sẽ thấy dòng `Tomcat started on port 8081` và
`>>> Đã khởi tạo dữ liệu mẫu thành công cho Bảng products`.

---

## 6. Kiểm thử

### Cách 1 – Script tự động (PowerShell)

Khi cả 2 service đang chạy, tại thư mục `product-service`:
```powershell
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
```

Script chạy 9 test case:

| # | Test | Kỳ vọng |
|---|------|---------|
| 1 | `GET /products` không có token | 401 |
| 2 | `GET /products` với token giả | 401 |
| 3 | Đăng nhập Auth Service `admin` / `123456` | nhận JWT token |
| 4 | `GET /products` với token hợp lệ | 200 + danh sách |
| 5 | `GET /products/1` | 200 + chi tiết |
| 6 | `POST /products` thêm sản phẩm | 201 + sản phẩm mới |
| 7 | `PUT /products/{id}` sửa sản phẩm vừa thêm | 200 + `updatedAt` thay đổi |
| 8 | `DELETE /products/{id}` | 200 |
| 9 | `GET /products/{id}` sau khi xoá | 404 |

### Cách 2 – Postman

Import file `Postman_Collection_BTH3.json`. Chạy request **"1.1 Đăng nhập lấy Token JWT"** trước:
token tự lưu vào biến `jwt_token` và được các request sản phẩm dùng lại.

### Cách 3 – Thủ công

1. Đăng nhập bài 2: `POST http://localhost:8080/` body `{"userName":"admin","password":"123456"}` → copy `token`.
2. Gọi bài 3 kèm header `Authorization: Bearer <token>`, ví dụ `GET http://localhost:8081/products`.

---

## 7. Điểm kỹ thuật đáng chú ý

- **Xác thực tập trung qua Auth Service** bằng `HandlerInterceptor`, không sao chép logic JWT của bài 2 sang bài 3.
- **Phân biệt lỗi xác thực và lỗi hạ tầng**: token sai → 401, Auth Service không phản hồi → 503 (có timeout 5 giây).
- **Kiểm tra dữ liệu bằng Bean Validation** (`@NotBlank`, `@Size`, `@DecimalMin`, `@Min`) bám theo kiểu cột của bảng.
- **Phân tầng rõ ràng** Controller – Service – Repository; lỗi được gom về `GlobalExceptionHandler`.
- **Thời gian tạo/cập nhật tự động** bằng `@PrePersist` / `@PreUpdate`.
