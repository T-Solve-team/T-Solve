---
module: tracker
date: 2026-10-27
branch: feat/KAN-22-tracker-issue-created
---

# Handoff: tracker — KAN-22 (người dùng tạo ticket trên Jira)

Ghi chú bàn giao để session sau làm tiếp. Đọc cùng: `03-modules/tracker/spec.md` (approved, version `b2abb6f0`),
`04-plans/tracker/design.md`, `04-plans/tracker/iteration-1/tasks.md` (mục **Update 2026-10-22** ở đầu file là thứ tự
làm của slice KAN-22).

## Đang ở đâu

Slice KAN-22 có 6 bước (tasks.md). **Bước 1 gần xong**, code đã viết, `mvn verify` xanh, **chưa chạy được app với DB**
và **chưa commit gì** (mọi thay đổi bên dưới đều chưa commit trên nhánh `feat/KAN-22-tracker-issue-created`).

| Bước | Nội dung | Trạng thái |
|---|---|---|
| 1 | Task 2 (phần nhận event) + multi-tenancy | ✅ Xong, commit `c890e22` (2026-10-27). App chạy với DB, `@TenantId` kiểm chứng trên DB thật |
| 2 | Task 3 — xác thực FIT | ✅ Xong, commit `0613c07`. ⚠️ Dependency mới **chờ nhóm duyệt**; `DefaultSecurityConfig` cần người phụ trách identity xem |
| 3 | Task 5 `TokenCipher` + Task 6 DTO/controller/service nhận event | ✅ Xong (`b739cad`, `84a1428`) — chỉ `issue-created`; `issue-updated` làm ở KAN-23 |
| 4 | Trigger `jira-app/manifest.yml` → backend, deploy, thử ticket thật + kiểm tra Task 1 | Đang làm. Người dùng phải `forge login` + `forge create` trước (xem "Chạy demo") |
| 5 | Task 7 `JiraClient` + Task 8 worker | Chưa làm |
| 6 | Task 9 triage + MSG80 | Chưa làm. Cần thống nhất interface với người phụ trách module `triage` |

## Chạy demo (2026-10-27)

1. Người dùng: `forge login`, rồi `forge create -d jira-app tsolve-jira-app` (UI Kit → Jira → `jira-issue-panel`).
2. Quick change `jira-app/manifest.yml` (module `jira-app` mới `decomposed`): thêm `trigger` `avi:jira:created:issue`
   → `endpoint` (remote `tsolve-backend`, route `/api/v1/jira-app/events/issue-created`, `auth.appSystemToken.enabled: true`),
   `remotes` với `baseUrl` = URL tunnel, scope `read:jira-work`.
3. `.env`: `TSOLVE_JIRA_APP_ID` = `app.id` trong manifest (đang là placeholder).
4. Chèn site bằng SQL: cloudId lấy từ `https://<site>.atlassian.net/_edge/tenant_info`;
   `INSERT INTO jira_site (workspace_id, site_url, cloud_id) VALUES (1, 'https://<site>.atlassian.net', '<cloudId>')`.
5. Chạy backend (`-Djdk.net.unixdomain.tmpdir=C:/tmp/uds` nếu lỗi loopback), mở tunnel `cloudflared tunnel --url http://localhost:8080`,
   `forge deploy` + `forge install`, tạo ticket → `issue_event` có dòng `RECEIVED`.
6. Ghi lại kết quả các mục "Kiểm tra ở bước 4" (header, claim FIT thật, `app.apiBaseUrl`, `timestamp`).
- Chưa có cột `api_base_url` trong `issue_event` (design nói lưu `apiBaseUrl` từ FIT): quyết định ở Task 7/8.

## Quyết định đã chốt (đã ghi vào artifact)

- **Đổi tên:** bảng `tracker_event` → `issue_event`; class `TrackerEvent*` → `IssueEvent*`; changeset
  `002-tracker-tables` → `002-jira-issue-events`. Module và package vẫn là `tracker` / `vn.tsolve.tracker`.
- **Multi-tenancy (2026-10-27):** Tenant = **Workspace**, lọc bằng **Hibernate `@TenantId`**, **không** dùng Postgres
  RLS, **không** có base entity (mỗi entity tự khai báo `@TenantId workspaceId`). Quyết định chung ghi ở
  `03-modules/architecture.md` (Cross-Cutting Concerns → Multi-tenancy); hôm nay chỉ áp dụng cho `jira_site`,
  `issue_event`. Module khác thêm `@TenantId` khi họ làm module của mình.
  - Spec thêm **AC-003.7**, **AC-004.9**, Non-Functional 8 → đã duyệt lại (`sdd.ts approve tracker`).
  - Design: mục "Workspace per transaction" (nhận event, worker, cleanup đặt Workspace ở đâu).
  - **Đặt Workspace trước khi transaction bắt đầu** (`WorkspaceContext.runAs(id, () -> service.x())`), không gọi bên
    trong method `@Transactional`.
  - `@TenantId` **không** lọc native SQL và `findById` → query claim của worker phải tự xử lý Workspace.
- **`timestamp` của Forge:** OpenAPI khai báo `type: string`, `pattern: '^\d{1,19}$'` (tài liệu Forge ghi là string).
  DTO (Task 6) nhận `String` + `@Pattern`, service `Long.parseLong`; entity/DB vẫn `Long`/`BIGINT`.
- **Khoá chống trùng** giữ `(cloud_id, issue_id, event_type, event_timestamp)`. Chưa kiểm chứng Forge gửi lại có giữ
  nguyên `timestamp` không — kiểm tra ở bước 4 (xem "Kiểm tra ở bước 4").

## File đã thay đổi (chưa commit)

Backend:
- `backend/src/main/resources/db/changelog/changes/002-jira-issue-events.sql` (mới) + dòng include trong
  `db.changelog-master.xml`. Có `issue_event.workspace_id` (FK `workspace`) và index `idx_issue_event_workspace`.
- `backend/src/main/java/vn/tsolve/tracker/` — `package-info.java`; `entity/` `IssueEvent` (field, constructor,
  `claim`, `scheduleRetry`, `markProcessed`, `markIgnored`, `markFailed`, `markCommentPosted`, `@TenantId`),
  `IssueEventStatus`, `IssueEventType`, `JiraSite` (`@TenantId`, constructor không nhận `workspaceId`);
  `repository/` `IssueEventRepository`, `JiraSiteRepository.findByCloudId`.
- `backend/src/main/java/vn/tsolve/common/security/WorkspaceContext.java` (`NONE = -1`, `ROOT = 0`, `callAs`, `runAs`).
- `backend/src/main/java/vn/tsolve/common/config/WorkspaceTenantResolver.java`
  (`CurrentTenantIdentifierResolver<Long>` + `HibernatePropertiesCustomizer`, `isRoot`).
- Test (18, xanh): `tracker/entity/IssueEventTest` (10), `common/security/WorkspaceContextTest` (4),
  `common/config/WorkspaceTenantResolverTest` (4).

Frontend: `frontend/openapi/tsolve-api.yaml` (path `issue-created`, schema `IssueEventPayload`, `timestamp` string) và
các file sinh ra trong `frontend/src/shared/api/generated/` (đã chạy `npm run generate:api`). Các lệnh kiểm tra
frontend (`format:check`, `lint`, `typecheck`, `test`, `build`) **chưa chạy xong lần nào** sau khi sửa.

Harness: `architecture.md`, `tracker/spec.md`, `tracker/design.md`, `tracker/iteration-1/tasks.md`,
`07-iterations/iteration-1.md`, `tracker/module.md`, `approvals.json`, `progress.json`, `events/events.jsonl`.

## ⚠️ Việc dở dang: app chưa khởi động được với DB

- **DB local đã được reset một phần:** đã `DROP TABLE issue_event, jira_site` và xoá dòng `002-jira-issue-events` trong
  `databasechangelog` (hai bảng lúc đó rỗng). **Hai bảng này hiện không tồn tại** cho tới khi app khởi động thành công
  và Liquibase chạy lại changeset 002.
- **Lỗi khi chạy app:** `password authentication failed for user "tsolve"`.
  - Đã biết: service Windows `postgresql-x64-17` đang **Running, StartType Automatic**, giữ `0.0.0.0:5432` (IPv4); Postgres
    của Docker (`tsolve-db`) nghe `:::5432` (IPv6). `localhost` trong `.env` rơi vào Postgres của Windows.
  - Đã kiểm tra: `.env` (thư mục gốc) có `DB_USER=tsolve`, `DB_PASSWORD` đúng như docker-compose, không có CRLF; đăng
    nhập TCP bằng mật khẩu **bên trong** container thành công.
  - Đã thử chạy với `DB_HOST='[::1]'` (biến môi trường, không sửa `.env`) → **vẫn lỗi**. Chưa rõ vì sao: có thể biến môi
    trường không được dùng, hoặc Postgres Windows cũng nhận kết nối IPv6. Chưa điều tra tiếp.
- **Cách gỡ nên thử trước:** mở PowerShell **Administrator**: `Stop-Service postgresql-x64-17` (và
  `Set-Service postgresql-x64-17 -StartupType Manual` để không tự bật lại), rồi chạy lại `TSolveApplication`.
- **Khi app lên được, kiểm tra:**
  1. Log có `ChangeSet ...002-jira-issue-events.sql::002-jira-issue-events::tracker ran successfully`, không có lỗi
     `Schema-validation`.
  2. `docker exec tsolve-db psql -U tsolve -d tsolve -c "\d issue_event"` có cột `workspace_id`.
  3. Chứng minh `@TenantId` chạy thật (test hiện tại chỉ kiểm tra resolver/context, không có DB — AGENTS cấm
    `@DataJpaTest`): insert một `JiraSite` trong `WorkspaceContext.runAs(<id workspace có thật>, …)` → dòng có đúng
    `workspace_id`; đọc trong Workspace khác → không thấy; đọc bằng `ROOT` → thấy. Có thể làm bằng một
    `CommandLineRunner` tạm hoặc endpoint tạm, **xoá trước khi commit**. Nếu Hibernate báo lỗi khi cấu hình resolver
    mà không có `@TenantId`/`MultiTenantConnectionProvider` thì xem lại `WorkspaceTenantResolver`.

## Việc tiếp theo (theo thứ tự)

1. Gỡ lỗi DB ở trên, chạy app, kiểm tra 3 điểm.
2. Bước 1 còn lại: `common/config/TrackerProperties` (record `@ConfigurationProperties`: app id, JWKS URI, token key,
   thời gian của worker) + thêm `TSOLVE_JIRA_APP_ID`, `TSOLVE_TOKEN_KEY`, `FORGE_JWKS_URI` vào `.env.example`
   **và** `.env` của máy (thiếu là app không khởi động).
3. Chạy kiểm tra frontend trong `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build`;
   `npm run generate:api` không được để lại diff.
4. Backend: `mvn spotless:apply` rồi `mvn verify`.
5. **Báo cáo và commit bước 1 với người dùng** (quy tắc dự án: báo feature, file, kết quả test, commit message đề xuất;
   commit khi người dùng đồng ý). Header dạng `feat(tracker): record Jira issue events per Workspace (KAN-22)`, body
   `STORY-003`. Thay đổi trong `harnessclaude/` vào **commit harness riêng**. **Không** thêm trailer
   `Co-Authored-By: Claude` (memory của người dùng). Không push/PR nếu người dùng không yêu cầu.
6. Bước 2: FIT (Task 3) — hỏi nhóm duyệt thư viện mới trước. Nhớ đặt Workspace từ `cloudId` (tra `jira_site` bằng `ROOT`).
7. Bước 3: `TokenCipher` (Task 5), rồi Task 6 (DTO `IssueEventPayload` với `timestamp` String, controller, service;
   bắt lỗi trùng bằng `saveAndFlush` + chỉ `uq_issue_event`, edge case 12).

## Kiểm tra ở bước 4 (Forge thật)

- Payload thật có những trường nào; `timestamp` là string hay số (đã sửa OpenAPI thành string theo tài liệu).
- Cho endpoint trả 500 một lần → Forge gửi lại sau ~60 s: `timestamp` có giữ nguyên không, có `retryContext` không.
- `changelog` của `issue-updated` có `id` không.
- Nếu `timestamp` đổi khi gửi lại: sửa design/spec trước — `ISSUE_CREATED` dùng khoá `(cloud_id, issue_id, event_type)`,
  `ISSUE_UPDATED` dùng `changelog.id` nếu có.
- `selfGenerated` / `ignoreSelf` trong manifest nếu sau này app sửa issue.
- Tạo app Forge: làm trên **nhánh riêng** `chore/KAN-xx-jira-app-setup` từ `main` mới nhất (cất code KAN-22 bằng
  `git stash push -u` hoặc commit trước). `forge tunnel` chỉ cho thấy payload, **không** cho thấy cơ chế gửi lại của
  Forge Remote.

## Lưu ý về cách làm việc với người dùng

- Người dùng đang học Spring/JPA/JUnit; giải thích bằng tiếng Việt, kèm ví dụ cụ thể. Thường tự viết code và nhờ review;
  khi họ yêu cầu rõ thì mới viết hộ.
- Hỏi từng câu một (2–3 lựa chọn, có gợi ý) trước khi viết spec/design/code.
