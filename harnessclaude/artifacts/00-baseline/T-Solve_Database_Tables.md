# T-Solve Database Tables (Markdown companion)

**The source PDF is not in the repo (Hiếu keeps it). This file is the source of truth for the team.** It was extracted from the PDF `T-Solve_Database_Tables.pdf` (source title "T-Solve Database Tables", dated Oct 31, 2026, author Minh Dang, 18 pages) on 2026-11-04; the PDF content is unchanged except for the section "Additions not in the PDF" at the end, which was decided by Hiếu on 2026-11-04. Notes are copied in the PDF's language (Vietnamese). Null: N = NOT NULL, Y = nullable. Mention of "ERD" refers to `ERD_T-Solve_31-10.drawio` (not in this repo).

## Overview (Tổng quan)

25 tables: 2 in the catalog database (one for the whole system) and 23 in the tenant database (one database per Workspace). A catalog table is the "Catalog"; the tenant database holds all business data of one company; no tenant table has a Workspace ID column.

| Database | Tables | Contains |
|---|---|---|
| Catalog | 2 | Tìm đúng tenant database theo site name, Jira cloudId; email thuộc Workspace nào |
| Tenant | 23 | Toàn bộ dữ liệu nghiệp vụ của một công ty; không bảng nào có cột Workspace ID |

Conventions (Quy ước):

- PostgreSQL 16, table and column names in `snake_case`, few abbreviations; the User table is named `app_user` because `user` is a keyword.
- Primary key `id BIGINT GENERATED ALWAYS AS IDENTITY`, except join tables (composite key) and `workspace_email` (key is the email).
- Moments use `TIMESTAMPTZ`, date-only uses `DATE`. Status is stored as `VARCHAR` + `CHECK` against the values listed in the description column.
- Tokens and passwords are never stored in clear text: tokens encrypted (`BYTEA`), the DB password is stored only as a reference name to a secret.
- No hard delete: User, Department, Project only change status; logs (Review Decision, Feedback, Search Log, Audit Log) are append-only.

## Catalog database

A shared database for the whole system, used only to find the company's tenant database before handling a request. The operations team writes it by script when creating a Workspace (GBR-24); the application mostly reads.

### tenant_workspace

Mỗi công ty một dòng: cách tìm và cách kết nối tới database của công ty đó.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | Workspace ID, dùng chung với `workspace.id` trong tenant DB |
| site_name | VARCHAR(63) | N | UNIQUE | Phần đầu của `{site_name}.tsolve.io.vn`; chữ thường, số, gạch ngang |
| jira_cloud_id | VARCHAR(64) | Y | UNIQUE | cloudId của Jira site; trống tới khi Admin nhập địa chỉ lần đầu (UC-08). Một Jira site chỉ thuộc một Workspace |
| db_host | VARCHAR(255) | N | | Máy chạy PostgreSQL, ví dụ `postgres` |
| db_port | INTEGER | N | | Mặc định 5432 |
| db_name | VARCHAR(63) | N | UNIQUE | Database của công ty, ví dụ `tsolve_fpt` |
| db_username | VARCHAR(63) | N | | Tài khoản DB chỉ có quyền trên database này |
| db_password_ref | VARCHAR(100) | N | | Tên biến môi trường / secret chứa mật khẩu, không phải mật khẩu |
| status | VARCHAR(20) | N | | `ACTIVE`, `SUSPENDED`; Suspended thì chặn mọi request |
| created_at | TIMESTAMPTZ | N | | Ngày tạo Workspace |

### workspace_email

Danh sách email của mọi Workspace, để mỗi email chỉ thuộc một Workspace (MSG155).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| email | VARCHAR(254) | N | PK | Email công ty, lưu chữ thường |
| workspace_id | BIGINT | N | FK -> tenant_workspace.id | Workspace sở hữu email |
| created_at | TIMESTAMPTZ | N | | Ngày thêm |

Ghi khi Admin thêm user hoặc khi script tạo Admin đầu tiên: ghi dòng này trước, rồi tạo `app_user` ở tenant DB; tạo user lỗi thì xóa lại dòng này (hai database khác nhau, không chung một giao dịch).

## Tenant database — tổ chức và phân quyền

Công ty, Department, người dùng, vai trò và Project liên kết Jira.

### workspace

Thông tin công ty, đúng 1 dòng trong mỗi tenant DB; không bảng nào trỏ tới. Admin nhập lần đầu (UC-08) và sửa (UC-09).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | Bằng `tenant_workspace.id` trong catalog |
| company_name | VARCHAR(200) | Y | | Tên công ty; trống tới lần đăng nhập đầu của Admin |
| sign_in_provider | VARCHAR(20) | N | | `GOOGLE` trong MVP |
| jira_site_url | VARCHAR(255) | Y | | Ví dụ `https://fpt.atlassian.net` |
| service_account_email | VARCHAR(254) | Y | | Tài khoản Jira của công ty, dùng để đọc issue và tạo issue từ Chatbot. Bắt buộc trước khi thêm Project |
| service_account_token | BYTEA | Y | | API token đã mã hóa; không bao giờ hiển thị lại |
| token_expiry_date | DATE | Y | | Ngày token hết hạn, để nhắc Admin thay |
| updated_at | TIMESTAMPTZ | N | | Lần sửa cuối |

### department

Nhóm trong công ty, sở hữu Project và một Space Wiki.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| name | VARCHAR(100) | N | UNIQUE | Không trùng, kể cả với Department đã xóa (MSG43); so sánh không phân biệt hoa thường |
| default_project_id | BIGINT | Y | FK -> project.id | Project nhận issue tạo từ Chatbot (loại Task). Trống = Department không nhận yêu cầu hỗ trợ |
| status | VARCHAR(20) | N | | `ACTIVE`, `DELETED` (xóa mềm, khôi phục được) |
| created_at | TIMESTAMPTZ | N | | |

### app_user

Mọi người đăng nhập được T-Solve; vai trò Employee ngầm định, vai trò khác nằm ở `user_role`.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| department_id | BIGINT | Y | FK -> department.id | Bắt buộc với Project Manager (đúng một Department), trống với người khác |
| full_name | VARCHAR(100) | N | | |
| email | VARCHAR(254) | N | UNIQUE | Email công ty, đăng nhập Google (MSG11) |
| jira_account_id | VARCHAR(128) | Y | UNIQUE | Tài khoản Jira đã liên kết (GBR-19); dùng làm reporter khi Chatbot tạo issue |
| status | VARCHAR(20) | N | | `ACTIVE`, `DEACTIVATED`; không bao giờ xóa |
| created_at | TIMESTAMPTZ | N | | |

### user_role

Vai trò của một User; một người giữ được nhiều vai trò, quyền cộng dồn.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| user_id | BIGINT | N | FK -> app_user.id | |
| role | VARCHAR(30) | N | | `TICKET_RESOLVER`, `PROJECT_MANAGER`, `KNOWLEDGE_MANAGER`, `ADMIN`. UNIQUE (user_id, role) |
| assigned_at | TIMESTAMPTZ | N | | Người gán ghi ở audit log |

### project

Đơn vị duyệt: thuộc một Department, liên kết một Jira project.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | Project ID |
| department_id | BIGINT | N | FK -> department.id | |
| jira_project_key | VARCHAR(20) | N | UNIQUE | Ví dụ `DP`; một Jira project chỉ liên kết một Project |
| field_mapping | JSONB | N | | Trường Jira nào đổ vào Title, Description… của Ticket |
| done_status | VARCHAR(100) | N | | Trạng thái Jira coi là đã xong (mở Solution Form) |
| review_range_days | SMALLINT | N | | Số ngày duyệt, mặc định 3 |
| status | VARCHAR(20) | N | | `ACTIVE`, `REMOVED`; không bao giờ xóa hẳn |
| created_at | TIMESTAMPTZ | N | | |

### project_membership

Ai là Project Manager, ai là thành viên (Ticket Resolver) của Project.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| user_id | BIGINT | N | PK, FK -> app_user.id | |
| project_id | BIGINT | N | PK, FK -> project.id | |
| role_in_project | VARCHAR(20) | N | | `PROJECT_MANAGER`, `MEMBER`. Index unique riêng: tối đa một `PROJECT_MANAGER` mỗi Project |
| added_at | TIMESTAMPTZ | N | | |

## Tenant database — ticket

Ticket và Solution, file đính kèm, duyệt, đánh giá, import và bản nháp.

### ticket

Bản chụp một issue đã giải quyết kèm đúng một Solution (không version).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| ticket_key | VARCHAR(50) | N | UNIQUE | Ticket ID nghiệp vụ: Jira issue key hoặc ID trong CSV / nhập tay (MSG31 khi trùng) |
| project_id | BIGINT | N | FK -> project.id | |
| writer_id | BIGINT | Y | FK -> app_user.id | Người viết Solution |
| import_history_id | BIGINT | Y | FK -> import_history.id | Có khi Ticket tạo từ import |
| source | VARCHAR(20) | N | | `SOLUTION_FORM`, `JIRA_IMPORT`, `CSV`, `MANUAL` |
| title | VARCHAR(255) | N | | |
| description | TEXT | Y | | |
| symptoms | TEXT | N | | Solution — Symptoms (GBR-33) |
| root_cause | TEXT | N | | Solution — Root cause |
| resolution_steps | TEXT | N | | Solution — Resolution steps |
| workaround | TEXT | Y | | Solution — Workaround, mục duy nhất không bắt buộc |
| scope | TEXT | N | | Solution — Scope of applicability. CHECK tổng độ dài 5 mục <= 10.000 ký tự (MSG02) |
| status | VARCHAR(20) | N | | `PENDING_REVIEW`, `CHANGES_REQUESTED`, `PUBLISHED`, `REJECTED`, `UNPUBLISHED`, `EXPIRED`; import thì `PUBLISHED` ngay |
| review_due_date | DATE | Y | | Hạn duyệt; JOB-02 chuyển `EXPIRED` lúc 00:00 khi quá hạn |
| rejection_reason | TEXT | Y | | |
| change_request_comment | TEXT | Y | | |
| source_link | VARCHAR(500) | Y | | Link tới issue Jira |
| created_at | TIMESTAMPTZ | N | | |
| updated_at | TIMESTAMPTZ | N | | |

### evidence

File chứng minh cho Solution; tối đa 5 file mỗi Ticket, chỉ thêm, không xóa, không thay.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| ticket_id | BIGINT | N | FK -> ticket.id | |
| file_name | VARCHAR(255) | N | | Tên gốc |
| file_type | VARCHAR(100) | N | | MIME type |
| file_size | BIGINT | N | | Byte |
| storage_key | VARCHAR(500) | N | | Đường dẫn trong kho file (không có trên ERD, cần để đọc file) |
| uploaded_at | TIMESTAMPTZ | N | | |

### review_decision

Mỗi quyết định của Project Manager trên một Ticket; lưu vĩnh viễn.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | Decision ID |
| ticket_id | BIGINT | N | FK -> ticket.id | |
| project_manager_id | BIGINT | N | FK -> app_user.id | |
| decision | VARCHAR(20) | N | | `APPROVE`, `REJECT`, `REQUEST_CHANGES`, `UNPUBLISH`, `REPUBLISH`, `SEND_BACK` |
| reason | TEXT | Y | | Lý do hoặc nhận xét |
| decided_at | TIMESTAMPTZ | N | | |

### feedback

Đánh giá của người dùng cho một Published ticket; lưu vĩnh viễn.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| ticket_id | BIGINT | N | FK -> ticket.id | |
| user_id | BIGINT | N | FK -> app_user.id | |
| rating | VARCHAR(20) | N | | `HELPFUL`, `NOT_HELPFUL` |
| comment | TEXT | Y | | |
| channel | VARCHAR(20) | N | | `WEB`, `ISSUE_PANEL` |
| created_at | TIMESTAMPTZ | N | | |

### import_history

Một lần import: Jira import (<= 100 issue theo khoảng ngày), CSV hoặc một lần nhập tay.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| performed_by | BIGINT | N | FK -> app_user.id | Project Manager thực hiện |
| source | VARCHAR(20) | N | | `JIRA_IMPORT`, `CSV`, `MANUAL` |
| date_from | DATE | Y | | Chỉ với Jira import |
| date_to | DATE | Y | | Chỉ với Jira import |
| file_name | VARCHAR(255) | Y | | Chỉ với CSV |
| tickets_published | INTEGER | N | | |
| removed_count | INTEGER | N | | Số issue bị bỏ (Jira) hoặc số dòng lỗi (CSV) |
| errors | JSONB | Y | | Danh sách lỗi theo dòng |
| status | VARCHAR(20) | N | | `COMPLETED`, `FAILED` |
| created_at | TIMESTAMPTZ | N | | |

### draft

Bản nháp do người dùng bấm "Save as draft"; xóa khi submit / import thành công hoặc khi người tạo xóa.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| creator_id | BIGINT | N | FK -> app_user.id | |
| type | VARCHAR(20) | N | | `SOLUTION_FORM`, `JIRA_IMPORT`, `CSV`, `MANUAL` |
| jira_issue_keys | TEXT[] | Y | | Issue đang soạn; mỗi user tối đa một draft Solution Form cho mỗi issue |
| content | JSONB | N | | Nội dung đã lưu |
| saved_at | TIMESTAMPTZ | N | | |

## Tenant database — AI và nhật ký

Kết quả AI Solution Suggestion, lịch sử tìm kiếm và audit log.

### suggestion_result

Kết quả AI Solution Suggestion cho một issue mới; một dòng mỗi issue (GBR-36). Dùng để ghi Yes/No của reporter, làm số liệu Dashboard và chống đăng trùng khi Forge gửi lại event.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| project_id | BIGINT | N | FK -> project.id | |
| jira_issue_key | VARCHAR(50) | N | UNIQUE | Issue mới được xử lý |
| sources | JSONB | Y | | Ticket và Wiki document đã dùng, kèm điểm similarity; trống khi không nguồn nào qua ngưỡng |
| suggested_solution | TEXT | Y | | Nội dung comment đã đăng (MSG80); trống = không đăng gì |
| reporter_answer | VARCHAR(10) | Y | | `YES`, `NO`; trống tới khi reporter trả lời |
| created_at | TIMESTAMPTZ | N | | Lúc xử lý issue |
| answered_at | TIMESTAMPTZ | Y | | |

### search_log

Mỗi lần tìm trên web, trong Issue Panel, và mỗi câu hỏi Chatbot không có nguồn; kết quả 0 = knowledge gap trên Dashboard. Câu chào hỏi và câu ngoài phạm vi không ghi.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| user_id | BIGINT | N | FK -> app_user.id | |
| department_id | BIGINT | Y | FK -> department.id | Phạm vi tìm, để chia số liệu theo Department |
| query | TEXT | N | | Câu tìm hoặc câu hỏi |
| channel | VARCHAR(20) | N | | `WEB`, `ISSUE_PANEL`, `CHATBOT` |
| result_count | INTEGER | N | | |
| created_at | TIMESTAMPTZ | N | | |

### audit_log_entry

Mọi thao tác quan trọng (gán vai trò, sửa service account, tạo issue từ Chatbot, sửa / xóa Wiki…); chỉ đọc, lưu vĩnh viễn.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| actor_id | BIGINT | Y | FK -> app_user.id | Trống khi do job hệ thống (ví dụ JOB-02) |
| action | VARCHAR(50) | N | | Ví dụ `ROLE_ASSIGNED`, `TICKET_APPROVED`, `CHATBOT_ISSUE_CREATED` |
| target_type | VARCHAR(50) | N | | Ví dụ `USER`, `TICKET`, `WIKI_DOCUMENT` |
| target_id | VARCHAR(100) | Y | | ID của đối tượng (hoặc Jira issue key) |
| details | JSONB | Y | | Giá trị cũ / mới, lý do |
| created_at | TIMESTAMPTZ | N | | |

## Tenant database — Wiki

Mỗi Department một Space. Tài liệu không có version, không duyệt: upload hoặc ghi đè là Published ngay và được index lại.

### space

Vùng Wiki của một Department.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| department_id | BIGINT | N | FK -> department.id, UNIQUE | Đúng một Space mỗi Department, tạo cùng Department |
| name | VARCHAR(100) | N | | Đổi theo tên Department |
| status | VARCHAR(20) | N | | `ACTIVE`, `HIDDEN` (ẩn khi Department bị xóa, hiện lại khi khôi phục) |

### space_manager

Knowledge Manager quản lý Space nào (folder, quyền, thùng rác).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| space_id | BIGINT | N | PK, FK -> space.id | |
| user_id | BIGINT | N | PK, FK -> app_user.id | User phải có vai trò `KNOWLEDGE_MANAGER` |
| assigned_at | TIMESTAMPTZ | N | | |

### space_permission

Quyền của một Department hoặc một User trên Space.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| space_id | BIGINT | N | FK -> space.id | |
| grantee_department_id | BIGINT | Y | FK -> department.id | Đúng một trong hai cột grantee có giá trị (CHECK) |
| grantee_user_id | BIGINT | Y | FK -> app_user.id | |
| level | VARCHAR(10) | N | | `VIEW`, `CREATE`. Quyền Create của chính Department sở hữu không xóa được (GBR-30) |
| granted_at | TIMESTAMPTZ | N | | Mỗi (space, grantee) một dòng |

### folder

Thư mục trong Space, tối đa 3 cấp.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| space_id | BIGINT | N | FK -> space.id | |
| parent_id | BIGINT | Y | FK -> folder.id | Trống = folder cấp 1 |
| name | VARCHAR(100) | N | | Không trùng trong cùng folder cha; chỉ xóa được folder rỗng |

### wiki_document

Tài liệu Wiki; cập nhật = upload lại, ghi đè bản cũ.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| space_id | BIGINT | N | FK -> space.id | |
| folder_id | BIGINT | Y | FK -> folder.id | Trống = nằm ở gốc Space |
| owner_id | BIGINT | N | FK -> app_user.id | Người tạo; người sửa sau ghi ở audit log |
| title | VARCHAR(255) | N | | Không trùng trong cùng folder |
| visibility | VARCHAR(20) | N | | `DEPARTMENT`, `COMPANY_WIDE` |
| status | VARCHAR(20) | N | | `PUBLISHED`, `DELETED` (trong thùng rác, khôi phục hoặc xóa hẳn) |
| source_type | VARCHAR(10) | N | | `EDITOR`, `UPLOAD` |
| original_file_name | VARCHAR(255) | Y | | Khi upload PDF / DOCX |
| storage_key | VARCHAR(500) | Y | | File gốc trong kho file |
| content | TEXT | N | | Nội dung văn bản (soạn hoặc trích từ file), dùng để index |
| updated_at | TIMESTAMPTZ | N | | Lần ghi đè cuối |

## Tenant database — Knowledge Chatbot

Lịch sử chat chỉ sống trong phiên đăng nhập (UC-48); đánh giá câu trả lời lưu vĩnh viễn (UC-49).

### chat_session

Một cuộc hội thoại của User; xóa khi phiên đăng nhập kết thúc.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| user_id | BIGINT | N | FK -> app_user.id | Chỉ chủ phiên xem được |
| started_at | TIMESTAMPTZ | N | | |

### chat_message

Một lượt hỏi đáp; xóa cùng `chat_session` (ON DELETE CASCADE).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| chat_session_id | BIGINT | N | FK -> chat_session.id | |
| question | TEXT | N | | |
| answer | TEXT | Y | | Trống khi đang sinh câu trả lời |
| citations | JSONB | Y | | Nguồn đánh số: Ticket / Wiki document, tiêu đề lúc trích |
| created_at | TIMESTAMPTZ | N | | |

### chat_feedback

Đánh giá một câu trả lời của Chatbot; chép lại câu hỏi và câu trả lời vì `chat_message` bị xóa khi hết phiên.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | BIGINT | N | PK | |
| user_id | BIGINT | N | FK -> app_user.id | |
| question | TEXT | N | | Lưu nguyên văn (câu 10 ngày 31/10) |
| answer | TEXT | N | | Lưu nguyên văn |
| rating | VARCHAR(20) | N | | `HELPFUL`, `NOT_HELPFUL` |
| comment | TEXT | Y | | |
| created_at | TIMESTAMPTZ | N | | |

## Bảng đã bỏ và điểm còn mở (dropped tables and open points)

| Dropped table | Date | Reason |
|---|---|---|
| credential | 31/10 | Jira API token gắn theo tài khoản, không theo Project; dùng service account trong `workspace` |
| document_version | 31/10 | Wiki không version, cập nhật là ghi đè |
| wiki_review_decision | 31/10 | Wiki không còn bước duyệt |
| triage_result | 29/10 | Thay bằng `suggestion_result`, không phân loại Simple / Complex |

Điểm còn mở (open, unchecked in the PDF):

- Bỏ `chat_session` và `chat_message`, giữ lịch sử chat ở web app? Lịch sử chỉ sống trong phiên nên không bắt buộc lưu DB.
- ERD còn đường "saved for" từ Draft tới Ticket nhưng `draft` không có cột `ticket_id` (draft có trước khi Ticket tồn tại) -> nên xóa đường đó.
- Bảng vector cho tìm kiếm (embedding Ticket, đoạn Wiki ~500 token) chưa mô tả; chốt ở Report 4 cùng pgvector.
- Cột `storage_key` của `evidence` và `wiki_document` chưa có trên ERD conceptual.

## Additions not in the PDF (decided by Hiếu, 2026-11-04)

These are not in the source PDF. They are decided for the `identity` module (KAN-235, Task 9b) and win over the PDF's tables above where they touch the same table.

### app_user: two extra columns

Added to the table `app_user` above (AC-001.16: the sign-in times).

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| first_sign_in_at | TIMESTAMPTZ | Y | | Lúc đăng nhập đầu tiên; trống tới lần đăng nhập đầu (Task 9b) |
| last_sign_in_at | TIMESTAMPTZ | Y | | Lúc đăng nhập gần nhất (Task 9b) |

Other decided differences from the `app_user` table above:

- `app_user.id` is `UUID` (`DEFAULT gen_random_uuid()`), not the PDF's `BIGINT` identity. Decided by Hiếu; the PDF's `BIGINT` is not followed.
- `app_user.full_name` is `VARCHAR(100)`, nullable (empty until the first sign-in). A `name` claim longer than 100 code points leaves the old name unchanged (AC-001.16 design note).
- `app_user.status` has the CHECK constraint `ck_app_user_status` (`ACTIVE`, `DEACTIVATED`).
- `app_user.email` is `VARCHAR(254)`, stored lower-cased (CHECK `ck_app_user_email_lower`), unique.

### hand_off_code

The one-time code that carries a finished sign-in from `login.` to the Workspace (or Console) site. It is not in the PDF. Interim: it lives in today's only Liquibase changelog; Task 14 moves it to the Catalog (registry) side with `sign_in_states`.

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| id | UUID | N | PK | The code the browser gets is `<id>.<HMAC>` |
| target | VARCHAR | N | | `WORKSPACE`, `PLATFORM_CONSOLE` |
| workspace_id | UUID | Y | | Null for the Console |
| account_id | UUID | Y | | Null for the Console |
| operator_email | VARCHAR(254) | Y | | Console only |
| site_host | VARCHAR | N | | The site the code may be redeemed on |
| return_to | VARCHAR(2048) | N | | |
| created_at | TIMESTAMPTZ | N | | |
| expires_at | TIMESTAMPTZ | N | | Created + 60 seconds |
| used_at | TIMESTAMPTZ | Y | | Set once, atomically, when redeemed (Task 10) |

The key type is `UUID` as designed by identity (platform AC-001.8); the PDF's tenant tables use `BIGINT`. Decided by Hiếu (2026-11-04): `UUID` is used and the PDF's `BIGINT` is not followed.
