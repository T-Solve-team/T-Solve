# Knowledge Chatbot golden set (system test round 2)

Latest run: **23/11/2026 11:47–11:52** (UTC+7), staging `https://bluefin.tsolve.io.vn/`, build `main` 70c79d84
(deployed 22/11 23:57), through the **web Knowledge Chatbot page**. First run: 22/11/2026 ~13:45 on build bbb1c1a7,
through the same request the page sends (see "First run" below).

## Purpose and rules

- Measures the web Knowledge Chatbot (UC-51, AI-12, GBR-39) with real Published knowledge, as the AI-quality check of
  Report 5.0 §2.1 and the AI rows of Report 5.4 (BUG-246 evidence).
- Asker: `ticket-resolver@` (Department IT Support, the Employee IT of the round-2 role plan), signed in to the web app.
  Each question was typed into the chatbot page in a **new conversation** (New conversation, question box, Send) and the
  answer of the page's own `POST /api/v1/chatbot/answers` call was recorded (outcome, text, citations) together with the
  time from Send to the response.
- Sources on staging: about 320 Published IT Support tickets (CSV sample data of DP, the round-2 import R2-001 / R2-002,
  T2 = DP-109 after W-2.08) and the round-2 Wiki documents `ST2-VPN-guide-company` / `ST2R-VPN-guide-company`
  (Company-wide) and `ST2-server-room-IT-only` (Department IT Support).
- Per answer: **Has source** (at least one citation), **Correct citation** (a cited source is on the expected topic —
  for tickets judged from the cited Symptoms, since the chatbot hides ticket ids by design), **NO_SOURCE** (outcome
  `NO_SOURCE`, the fixed reply MSG109), and the answer time in seconds.
- **Original questions G-01 to G-30:** G-01 to G-20 and G-22 to G-26 are answerable (25). G-21 (a topic whose only
  ticket is not Published) and G-27 to G-30 must get NO_SOURCE (5).
- **Paraphrased questions P-01 to P-10** (added 23/11): all answerable. Each keeps the meaning of a covered topic but
  uses other words, with little word overlap with the ticket titles and Symptoms (e.g. "kết nối bảo mật từ xa" for VPN,
  "màn ngoài đen thui" for "No signal"). They check the semantic part of retrieval.

## Results (23/11/2026, build 70c79d84)

| Metric | Formula | Original (G) | Paraphrased (P) |
|---|---|---|---|
| Answers with a source | answerable questions answered with ≥ 1 citation | **23 / 25 = 92 %** | **3 / 10 = 30 %** |
| Correct citation | answers whose citations are on the expected topic / answerable | **23 / 25 = 92 %** (every cited answer on topic) | **3 / 10 = 30 %** (every cited answer on topic) |
| NO_SOURCE on answerable questions | NO_SOURCE / answerable (lower is better) | **2 / 25 = 8 %** (G-23, G-24) | **7 / 10 = 70 %** |
| Correct NO_SOURCE | NO_SOURCE / 5 no-source questions | **5 / 5 = 100 %** (no invented answer) | – |
| Answer time | Send → response | median **4.1 s**, mean 4.0 s, max 6.3 s | median 3.1 s, mean 3.4 s, max 4.7 s |

All 40 calls returned HTTP 200; the AI server did not fail during the run.

Findings:

- The original wording gives the same result as on 22/11 (23/25 with a correct source, 5/5 correct NO_SOURCE). G-23
  and G-24 still get NO_SOURCE although the sources hold the answer (BUG-246; G-23 may be BUG-370, bold text in the
  Wiki chunk).
- **Paraphrased questions mostly fail: 7 of 10 get NO_SOURCE** although the same topics are answered when asked with
  the ticket's words (BUG-387). The VPN paraphrase P-01 fails while G-01 / G-02 are answered from the VPN guide. So the
  92 % holds only for questions phrased close to the stored text. Semantic Search itself ranks T2 first for a
  paraphrase (W-2.10), so the gap is in the chatbot's source search or its threshold.
- Language: G-02 (Vietnamese) was again answered in English, copied from the English Wiki text (BUG-247). G-26
  (English question) was answered in English.
- Answers often cite 4–5 near-identical CSV sample tickets (the sample data repeats the same Solution); P-04 is answered
  with one sentence that restates the cause and gives no steps, although the cited Solution has them.
- Answers are faster than on 22/11 (median 4.1 s against 5.6 s).

Screenshots (evidence doc "5.4 NFR evidence"): G-04 (answered, ticket citations), G-22 (answered, Wiki citation),
G-29 (correct NO_SOURCE), G-23 (wrong NO_SOURCE), P-04 (paraphrase answered), P-01 (paraphrase, wrong NO_SOURCE).

## Questions and answers

Outcome on 23/11 (build 70c79d84, web page); the 22/11 column is the first run (build bbb1c1a7).

| ID | Question (as typed) | Expected source (title) | Outcome 23/11 | Cited (first) | Correct | Time (s) | 22/11 |
|---|---|---|---|---|---|---|---|
| G-01 | Tôi không kết nối được VPN khi làm việc ở nhà, phải làm sao? | VPN tickets; Wiki ST2-VPN-guide-company | ANSWERED | W ST2R-VPN-guide-company, ST2-VPN-guide-company; T VPN (×3) | yes | 5.5 | ANSWERED |
| G-02 | VPN báo Authentication failed sau khi tôi đổi mật khẩu | Wiki ST2-VPN-guide-company | ANSWERED (in English) | W ST2R-VPN-guide-company, ST2-VPN-guide-company | yes | 3.5 | ANSWERED (in English) |
| G-03 | Ổ đĩa mạng chia sẻ không hiện trong This PC | "Ổ đĩa mạng chia sẻ không hiện trên máy" | ANSWERED | T network drive / security group (×5) | yes | 5.0 | ANSWERED |
| G-04 | Xuất báo cáo trên hệ thống kho bị lỗi 500 | "Kho hàng WMS báo lỗi 500 khi xuất báo cáo" | ANSWERED | T "Bấm 'Xuất Excel' thì Kho hàng WMS trả về trang lỗi 500…" (×5) | yes | 6.3 | ANSWERED |
| G-05 | Portal nội bộ báo lỗi 500 khi tôi export report | "Portal nội bộ báo lỗi 500 khi xuất báo cáo" | ANSWERED | T "Bấm 'Xuất Excel' thì Portal nội bộ trả về trang lỗi 500…" (×5) | yes | 4.5 | ANSWERED |
| G-06 | Tôi không nhận được mã OTP để đăng nhập chấm công | "Không nhận được mã OTP khi đăng nhập Hệ thống chấm công" | ANSWERED | T "Hệ thống chấm công báo đã gửi OTP…" (×2) | yes | 3.7 | ANSWERED |
| G-07 | Outlook không nhận thư mới, hiện Disconnected | "Outlook không đồng bộ email mới" | ANSWERED | T "Thanh trạng thái Outlook hiện 'Disconnected'…" (×4) | yes | 3.8 | ANSWERED |
| G-08 | Wi-Fi văn phòng cứ vài phút lại mất kết nối | Wi-Fi tickets (R2-001, "Wi-Fi văn phòng … chập chờn") | ANSWERED | T "Laptop tự ngắt Wi-Fi văn phòng mỗi 5-10 phút…" (R2-001) | yes | 3.8 | ANSWERED |
| G-09 | Email tôi gửi cho khách hàng bị rơi vào thư rác | "Email gửi ra ngoài bị vào spam của khách hàng" | ANSWERED | T "Email gửi tới Gmail của khách bị gắn nhãn spam…" (×5) | yes | 4.8 | ANSWERED |
| G-10 | Laptop chạy rất chậm sau khi cập nhật Windows | "Laptop chạy rất chậm sau bản cập nhật Windows" | ANSWERED | T "CPU luôn ở mức 60% … Antimalware Service Executable…" (×5) | yes | 4.1 | ANSWERED |
| G-11 | Máy in trên tầng không in được tài liệu | "Máy in tầng 5/6/8 không in được" | ANSWERED | T "Lệnh in nằm mãi trong hàng đợi … 'Error - Printing'" (×5) | yes | 4.0 | ANSWERED |
| G-12 | Tài khoản của tôi bị khóa vì nhập sai mật khẩu nhiều lần | "Tài khoản bị khóa do đăng nhập sai nhiều lần" | ANSWERED | T "Tài khoản bị khóa sau nhiều lần nhập sai mật khẩu" | yes | 4.4 | ANSWERED |
| G-13 | Dữ liệu kho không đồng bộ sang ERP | "Không đồng bộ được dữ liệu Kho hàng WMS với ERP" | ANSWERED | T "Log tích hợp báo 'HTTP 401 Unauthorized'…" (×5) | yes | 4.0 | ANSWERED |
| G-14 | Màn hình thứ hai không nhận khi cắm qua dock USB-C | "Màn hình phụ không nhận qua dock USB-C" | ANSWERED | T "Màn hình báo 'No signal', laptop vẫn sạc qua dock." (×5) | yes | 3.9 | ANSWERED |
| G-15 | Không chia sẻ màn hình được trong cuộc họp Teams | "Teams không chia sẻ được màn hình" | ANSWERED | T "Bấm 'Share' thì người khác chỉ thấy màn hình đen." (×5) | yes | 4.1 | ANSWERED |
| G-16 | Cài phần mềm từ Company Portal bị lỗi | "Không cài được phần mềm từ Company Portal" | ANSWERED | T "Company Portal báo lỗi 0x87D1041C…" (×5) | yes | 4.7 | ANSWERED |
| G-17 | Không đăng nhập được WMS sau khi đổi mật khẩu | "Không đăng nhập được Kho hàng WMS sau khi đổi mật khẩu" | ANSWERED | T "Màn hình đăng nhập Kho hàng WMS báo 'Sai tên đăng nhập…'" | yes | 4.2 | ANSWERED |
| G-18 | HRM không đồng bộ dữ liệu với ERP | "Không đồng bộ được dữ liệu HRM với ERP" | ANSWERED | T "Log tích hợp báo 'HTTP 401 Unauthorized'…" (×5) | yes | 4.2 | ANSWERED |
| G-19 | Không mở được file PDF đính kèm trên portal | "Portal nội bộ không mở được file đính kèm PDF" | ANSWERED | T "Bấm vào file PDF thì tab trình duyệt trống…" (×2) | yes | 3.8 | ANSWERED |
| G-20 | File Excel trên ổ mạng báo đang bị khóa, không sửa được | R2-002 "Không mở được file Excel trên ổ chia sẻ" | ANSWERED | T "Mở file Excel trên ổ chia sẻ báo file is locked for editing" | yes | 3.7 | ANSWERED |
| G-21 | Thanh toán Internet Banking bị trừ tiền nhưng báo thất bại | none Published (only ticket is Changes requested) | NO_SOURCE | – | yes (expected) | 2.6 | NO_SOURCE |
| G-22 | Làm sao xin quyền vào phòng máy chủ SRV-ALPHA-7? | Wiki ST2-server-room-IT-only | ANSWERED | W ST2-server-room-IT-only | yes | 4.2 | ANSWERED |
| G-23 | Số điện thoại khẩn cấp của kỹ sư trực phòng máy là gì? | Wiki ST2-server-room-IT-only (ext. 7742) | **NO_SOURCE** | – | **no** | 3.4 | NO_SOURCE |
| G-24 | VPN web portal dùng khi nào? | Wiki ST2-VPN-guide-company; VPN tickets (Workaround) | **NO_SOURCE** | – | **no** | 2.8 | NO_SOURCE |
| G-25 | Khách hàng báo lỗi ACC-LOCK-403 | T2 "Login báo lỗi, không vào được để xem đơn hàng" | ANSWERED | T "Khách hàng bấm đăng nhập … 'Tài khoản đang bị khóa'…" (T2) | yes | 4.7 | ANSWERED |
| G-26 | My shared network drive does not show up in File Explorer | "Ổ đĩa mạng chia sẻ không hiện trên máy" | ANSWERED (in English) | T "File Explorer không có ổ S:…" (×5) | yes | 4.2 | ANSWERED (in English) |
| G-27 | Chính sách nghỉ thai sản của công ty là gì? | none | NO_SOURCE | – | yes | 2.5 | NO_SOURCE |
| G-28 | Làm sao đặt phòng họp tầng 12? | none | NO_SOURCE | – | yes | 3.0 | NO_SOURCE |
| G-29 | Máy pha cà phê ở pantry bị hỏng thì báo ai? | none | NO_SOURCE | – | yes | 4.1 | NO_SOURCE |
| G-30 | Cách cấu hình máy chủ Kubernetes production? | none | NO_SOURCE | – | yes | 3.4 | NO_SOURCE |
| P-01 | Ở nhà tôi không vào được mạng nội bộ công ty qua kết nối bảo mật từ xa | VPN tickets; Wiki ST2-VPN-guide-company (as G-01) | **NO_SOURCE** | – | **no** | 2.6 | – |
| P-02 | Thư mục dùng chung của phòng tự nhiên biến mất khỏi máy tính | network drive tickets (as G-03) | **NO_SOURCE** | – | **no** | 2.6 | – |
| P-03 | Bấm in rồi mà giấy không ra, lệnh cứ treo mãi | printer tickets (as G-11) | ANSWERED | T "Lệnh in nằm mãi trong hàng đợi … 'Error - Printing'" (×5) | yes | 4.2 | – |
| P-04 | Gõ sai mấy lần giờ hệ thống không cho tôi vào nữa | account lock tickets (as G-12) | ANSWERED (one sentence, no steps) | T "Tài khoản bị khóa sau nhiều lần nhập sai mật khẩu" | yes | 4.7 | – |
| P-05 | Mã xác thực gửi qua điện thoại không tới nên tôi không chấm công được | OTP tickets (as G-06) | ANSWERED | T "Hệ thống chấm công báo đã gửi OTP…" (×2) | yes | 4.3 | – |
| P-06 | Hộp thư không thấy thư đến từ sáng tới giờ | Outlook tickets (as G-07) | **NO_SOURCE** | – | **no** | 2.2 | – |
| P-07 | Đối tác nói thư của tôi bị lọc vào mục quảng cáo bên họ | spam tickets (as G-09) | **NO_SOURCE** | – | **no** | 3.0 | – |
| P-08 | Máy tính xách tay ì ạch, quạt kêu to từ hôm nâng cấp hệ điều hành | slow laptop tickets (as G-10) | **NO_SOURCE** | – | **no** | 3.0 | – |
| P-09 | Cắm bộ chuyển Type-C mà màn ngoài đen thui | dock / monitor tickets (as G-14) | **NO_SOURCE** | – | **no** | 3.2 | – |
| P-10 | Bảng tính trên ổ chung không cho chỉnh vì có người khác đang giữ | R2-002 Excel lock (as G-20) | **NO_SOURCE** | – | **no** | 3.9 | – |

Permission check (W-3.09, 23/11 11:46, asked as `admin@`, Department HR): "Làm sao xin quyền vào phòng máy chủ
SRV-ALPHA-7?" → NO_SOURCE; the IT-only document is neither used nor cited. The same question as `ticket-resolver@`
(IT Support) is answered from it (G-22, W-3.10).

## First run (22/11/2026, build bbb1c1a7)

Run on 22/11/2026 ~13:45 after the staging AI server was back (it answered 503 MSG06 from 06:45 that morning,
BUG-335). Each original question was sent as a new conversation with the same request the chatbot page sends
(`POST /api/v1/chatbot/answers`, `history` empty). Result: 23/25 answerable questions with a correct source, NO_SOURCE
on G-23 and G-24 (8 %), 5/5 correct NO_SOURCE, answer time median 5.6 s, mean 6.3 s, max 14.5 s. The per-question
outcomes are in the "22/11" column above.
