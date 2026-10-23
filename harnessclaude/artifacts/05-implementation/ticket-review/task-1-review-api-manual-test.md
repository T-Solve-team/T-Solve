# Manual test: ticket review

**Module:** ticket-review
**Assignee:** OpenAI Codex
**Status:** local servers running for user testing; no commit performed
**Iteration:** 1
**Date:** 2026-10-20

## Hai địa chỉ đang chạy

- Frontend: http://127.0.0.1:5173/review-queue — mock hiện có, mặc định Project Manager, khoảng 58 ticket mẫu.
- Backend Swagger: http://127.0.0.1:8080/swagger-ui/index.html — API review thật, PostgreSQL riêng và adapter đăng nhập chỉ trong test classpath.

Frontend chưa được cập nhật để gọi backend mới. Khi thao tác FE, bạn đang thử hành vi mock cũ, gồm các bug đã ghi BUG-007–BUG-009. Để kiểm tra phần backend vừa triển khai, dùng Swagger theo hướng dẫn dưới đây. Không coi việc bấm FE mock là bằng chứng backend đã lưu dữ liệu.

Auth manual và công cụ tạo dữ liệu nằm trong `backend/src/test/java/vn/tsolve/review/manual/`, chỉ nạp qua main test với profile `manual-review`. Chúng không có trong jar production. Backend chỉ lắng nghe loopback. Email dừng ở bản ghi pending; OIDC, SMTP và Jira chưa được tích hợp trong scope đã chốt.

## Đăng nhập Swagger

1. Mở Swagger, nhấn **Authorize**.
2. Username **pm**, password **Review-Local-2026**, rồi **Apply credentials → Close**. Đây là tài khoản giả cho database manual.
3. Mở nhóm **manual-review-tools** → `GET /manual-review/csrf` → **Try it out → Execute**. Kết quả 200; trình duyệt nhận cookie CSRF và Swagger tự gắn header cho POST.
4. Gọi `GET /manual-review/fixtures` để lấy ID batch hiện tại. Các ID đổi khi tạo batch mới; luôn dùng kết quả GET này.

Nếu bạn reload tab và mất đăng nhập, làm lại bước Authorize và GET CSRF. Nếu POST báo 403, kiểm tra tài khoản đã Authorize và chạy lại GET CSRF trước.

## Dữ liệu đã để sẵn

Batch tại thời điểm bàn giao: `467d29c5`. GET fixtures là nguồn đúng nếu bạn đã tạo batch khác.

| Trường fixture | ID hiện tại | Dùng để thử |
|---------------|-------------|------------|
| approveId | 43 | Approve, gồm trường hợp người duyệt cũng là writer |
| rejectId | 44 | Reject với reason |
| changesId | 45 | Request changes với comment |
| secondChangesId | (lấy từ fixtures mới) | Ticket có Solution đầy đủ để test Request changes hàng loạt |
| foreignTicketId | 47 | Ticket của Project 20; pm của Project 10 không được duyệt |
| importedTicketId | 48 | Source MANUAL, đã Published, không đi qua Review Queue |

Các ticket của pm thuộc Project **10**; ticket ngoại phạm vi thuộc Project **20**. Dữ liệu được lưu trong database `review_manual`, container `tsolve-review-manual-20261020`, cổng loopback 55432. Nó không dùng database dự án và không gắn volume dự án. Container được để chạy để bạn tiếp tục test.

## Ba luồng chính

Trong nhóm **review**, mở endpoint → **Try it out**, thay body, rồi **Execute**.

### Approve

`POST /api/v1/reviews/approve`

```json
{"ticketIds":[43]}
```

Mong đợi: HTTP **200**, `count: 1`. Gọi `GET /api/v1/tickets/43`: status **PUBLISHED**. Ticket không còn trong queue. Duyệt lại cùng ID phải báo **409**.

### Reject

`POST /api/v1/reviews/reject`

```json
{"ticketIds":[44],"reason":"Solution chưa xử lý đúng nguyên nhân"}
```

Mong đợi: HTTP **200**, status **REJECTED**, lưu reason; không được Approve lại. Có một thông báo chờ gửi cho writer.

### Request changes

Trước khi gửi, gọi `GET /api/v1/tickets/45` và ghi lại `reviewDueDate`.

`POST /api/v1/reviews/request-changes`

```json
{"ticketId":45,"comment":"Bổ sung bước tái hiện và cách xác nhận kết quả"}
```

Mong đợi: HTTP **200**, status **CHANGES_REQUESTED**, comment được lưu; `reviewDueDate` giữ nguyên. Request changes dùng **ticketId đơn**, không dùng mảng ticketIds.

## Kiểm tra dữ liệu đã lưu

Trong nhóm **manual-review-tools**, gọi `GET /manual-review/state/{ticketId}` với ID vừa thao tác. Endpoint chỉ dành cho tài khoản operator của harness, không có trong production.

- `ticket`: trạng thái, Project, deadline và comment thực trong PostgreSQL.
- `decisions`: một record cho quyết định thành công, có actor, writer và comment/reason.
- `audit`: một event tương ứng.
- `notifications`: rỗng khi Approve; có một record khi Reject/Request changes. `delivered_at: null` nghĩa là đang pending, chưa gửi email thật.

Chỉ xác nhận một quyết định lần đầu sau khi tạo fixture. Những request bị từ chối không được tạo thêm decision/audit/notification.

## Các trường hợp lỗi nên thử

| Ca manual | Thao tác | Mong đợi |
|-----------|----------|----------|
| Sai Project | pm Approve `[47]` hoặc GET queue với projectId=20 | 403, không thay đổi ticket |
| Reason/comment trống | Gửi `"   "` hoặc bỏ trường bắt buộc | 400, MSG01 ở fieldErrors |
| Quá 1.000 ký tự | Reason/comment dài 1.001 ký tự | 400, MSG02 ở fieldErrors |
| ID thập phân | Approve `{"ticketIds":[1.5]}` | 400; không ép 1.5 thành 1 |
| ID trùng | Approve cùng một ID hai lần trong mảng | 400 |
| Request changes nhiều ticket | Gửi body cũ `{"ticketIds":[43,44],"comment":"Thêm bước"}` | 400 |
| Quyết định đã final | Approve lại ticket vừa Rejected | 409; không thêm record |
| Thiếu quyền review | Authorize bằng employee, GET review-queue | 403 |
| PM khác | Authorize bằng otherpm, GET queue projectId=20 | 200; projectId=10 bị từ chối |

Ba tài khoản manual **pm**, **otherpm**, **employee** cùng dùng password manual ở trên. Chuyển tài khoản bằng **Authorize → Remove authorization**, sau đó nhập tài khoản mới. Khôi phục pm để dùng nhóm manual-review-tools.

## Tạo dữ liệu mới và thử bulk

1. Authorize pm và GET CSRF nếu cần.
2. Gọi `POST /manual-review/fixtures` để tạo batch mới. Lịch sử cũ được giữ, không xóa hoặc sửa audit.
3. Dùng các ID mới trong response.
4. Bulk approve thành công: lấy `approveId` và `rejectId` của batch mới, gửi cả hai trong ticketIds; mong đợi count=2 và mỗi ticket có decision/audit riêng.
5. Bulk lỗi: ở một batch mới khác, Approve `[approveId, foreignTicketId]`; mong đợi 403 và cả hai vẫn Pending review, không tạo record. Đừng trộn ID đã xử lý của batch trước khi muốn kiểm tra bulk thành công.

## Bằng chứng kiểm tra trước bàn giao

- `mvn verify`: **34 tests, 0 failures/errors/skipped, BUILD SUCCESS**; Spotless nằm trong verify.
- HTTP smoke trên server đang chạy: **18 checks passed**, gồm ba quyết định, record DB, CSRF, quyền Project, input lỗi và bulk atomicity.
- Đã thao tác trực tiếp **Authorize → GET CSRF → POST fixtures** trên Swagger và nhận **200**, header X-XSRF-TOKEN được gửi tự động.
- Frontend đã kiểm tra bằng trình duyệt: Review Queue hiển thị 58 ticket mock. Chưa kiểm tra FE nối BE vì feature đó còn pending.
- Bug mới trong lần chuẩn bị manual: BUG-015–BUG-018 được giữ trong `docs/bug-log.md`, kể cả đã sửa. BUG-014 của trace vẫn mở.
- Script kiểm tra lại HTTP, dùng PowerShell 7 khi cả hai server còn chạy:

```powershell
& D:/T-Solve/backend/src/test/java/vn/tsolve/review/manual/manual-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 -Password Review-Local-2026
```

Script tạo batch riêng để kiểm tra rồi để lại một batch mới cho bạn. Nó không gửi mail hoặc gọi Jira.

## Chạy lại sau khi tắt máy

Database manual đã được tạo. Bật Docker Desktop, rồi chạy:

```powershell
$env:PATH = 'C:/Program Files/Docker/Docker/resources/bin;' + $env:PATH
& C:/Users/anime/Documents/Codex/tools/rtk/rtk.exe docker start tsolve-review-manual-20261020
```

Backend, trong một terminal tại `D:/T-Solve/backend`:

```powershell
$env:JAVA_HOME = 'D:/T-Solve/backend/target/tooling/jdk-21.0.12.1+1'
$env:PATH = 'D:/apache-maven-3.9.9/bin;' + $env:JAVA_HOME + '/bin;' + $env:PATH
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '55432'
$env:DB_NAME = 'review_manual'
$env:DB_USER = 'review_manual'
$env:DB_PASSWORD = 'review_manual_local'
$env:SERVER_PORT = '8080'
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5173'
$env:MANUAL_REVIEW_PASSWORD = 'Review-Local-2026'
& C:/Users/anime/Documents/Codex/tools/rtk/rtk.exe mvn spring-boot:test-run `
  '-Dspring-boot.run.main-class=vn.tsolve.review.manual.ManualReviewApplication' `
  '-Dspring-boot.run.profiles=manual-review' `
  '-Dspring-boot.run.arguments=--springdoc.swagger-ui.csrf.enabled=true'
```

JDK trong target là công cụ local tạm; Maven clean sẽ xóa nó. Khi đó dùng JDK 21 đã cài của bạn và đổi JAVA_HOME. Không dùng biến môi trường SPRINGDOC_SWAGGER_UI_CSRF_ENABLED; BUG-015 đã ghi lý do truyền canonical property qua argument.

Frontend, trong terminal khác tại `D:/T-Solve/frontend`:

```powershell
$env:VITE_API_MOCKING = 'true'
$env:VITE_API_BASE_URL = ''
& C:/Users/anime/Documents/Codex/tools/rtk/rtk.exe run `
  C:/Users/anime/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe `
  node_modules/vite/bin/vite.js --host 127.0.0.1 --strictPort
```

Dependencies đã cài từ package-lock. Server FE dùng Node 24.19.0, đúng engine yêu cầu. Với terminal do bạn mở, Ctrl+C dừng server tương ứng; dữ liệu manual nằm trong container vẫn được giữ khi chỉ dừng ứng dụng.
D1/D2: tạo batch fixture mới, lấy changesId và secondChangesId, gửi Request changes với ticketIds chứa cả hai và một comment. Cả hai thành Changes requested, giữ deadline, có một history/audit/pending notification mỗi ticket. Không còn fixture hoặc luồng Solution rỗng.
