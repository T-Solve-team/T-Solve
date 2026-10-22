# Test manual review từ frontend tới backend

Môi trường local riêng, frontend đã tắt mock. Backend dùng PostgreSQL thật và adapter identity chỉ nằm trong test classpath. Không gửi email thật.

## Chạy thử ngay

1. Mở http://localhost:8080/login, đăng nhập `pm` / `Review-Local-2026`. Trình duyệt chuyển đến http://localhost:5173/review-queue. Dùng cùng hostname localhost; không đổi sang 127.0.0.1 giữa luồng.
2. Queue có dữ liệu PostgreSQL. Click Ticket ID để mở detail; thử filter Project, đổi trang/sort, Select all, Approve/Reject nhiều ticket. Approve từ hai ticket trở lên phải hỏi xác nhận; Cancel không thay đổi dữ liệu. Không có Request Changes hàng loạt.
3. Batch mới sau smoke: http://localhost:5173/tickets/73 để Approve; /tickets/74 để Reject; /tickets/75 để Request Changes. Các ID thay đổi sau khi tạo batch mới hoặc khởi động lại backend; luôn kiểm tra GET /manual-review/fixtures.
4. Reject: gửi khi Reason rỗng/whitespace, nhập trên 1.000 ký tự, rồi nhập lý do hợp lệ. Lỗi phải giữ popup; thành công đổi sang Rejected, bỏ nút duyệt và ra khỏi queue.
5. Request Changes: thử Comment bắt buộc/max 1.000; gửi comment hợp lệ. Detail đổi Changes requested, hiện review comment, giữ nguyên Review due date và không còn trong queue.
6. Theo D1/D2: tạo fixture mới, chọn changesId và secondChangesId trong queue rồi Request Changes; một comment áp dụng cho cả hai. Không còn trường hợp Solution rỗng hoặc MSG20.
7. /tickets/77 ở Project khác: PM hiện tại phải nhận MSG08. /tickets/78 là Manual import đã Published, không có nút duyệt và không vào queue.
8. Reload ticket đã xử lý: trạng thái vẫn được lưu; không thể quyết định lại.

## Kiểm tra lịch sử và tạo dữ liệu mới

Mở http://localhost:8080/swagger-ui/index.html trong cùng trình duyệt đã đăng nhập. GET /manual-review/csrf trước POST nếu cần cookie mới; POST /manual-review/fixtures tạo sáu ticket mới, không xóa dữ liệu cũ. GET /manual-review/fixtures trả các ID hiện tại.

GET /manual-review/state/{ticketId} hiển thị status, decision history, audit và pending notifications từ DB. Approve có một history/audit, không có email; Reject/Request Changes có một pending notification với delivered_at null. Đó là trạng thái chờ tích hợp email, không phải email đã gửi.

Hai tài khoản khác cùng mật khẩu local: `otherpm` quản lý Project 20; `employee` chỉ có quyền đọc Project 10. Đăng xuất trước khi đổi tài khoản. Backend xác định quyền từ principal, không nhận actor/Project quyền từ payload frontend.

## Khởi động lại

Docker Desktop cần Running. Container PostgreSQL riêng: `tsolve-review-manual-20261020`, localhost:55432, database review_manual. Start container hiện có bằng `docker start tsolve-review-manual-20261020`.

Trong backend/, dùng Java 21 và Maven 3.9.9; đặt DB_HOST=127.0.0.1, DB_PORT=55432, DB_NAME=review_manual, DB_USER=review_manual, DB_PASSWORD=review_manual_local, SERVER_PORT=8080, CORS_ALLOWED_ORIGINS=http://localhost:5173 và MANUAL_REVIEW_PASSWORD=Review-Local-2026 trong environment. Đây là credential của DB test local dùng riêng.

```powershell
mvn spring-boot:test-run -Dspring-boot.run.main-class=vn.tsolve.ticket.manual.ManualReviewApplication -Dspring-boot.run.profiles=manual-review -Dspring-boot.run.arguments=--springdoc.swagger-ui.csrf.enabled=true
```

Trong frontend/, dùng Node 24:

```powershell
$env:VITE_API_MOCKING='false'
$env:VITE_API_BASE_URL=''
npm run dev -- --host 127.0.0.1 --strictPort
```

Adapter login, /me và fixtures không nằm trong production jar. Khi tích hợp identity thật, cần /me và cookie/CSRF tương thích; production hiện vẫn fail closed khi thiếu adapter auth/Project. SMTP/Jira Evidence retrieval/search không được giả lập là đã tích hợp.
