# Knowledge Chatbot golden set (system test round 2)

Run on **22/11/2026 ~13:45** (UTC+7), staging `https://bluefin.tsolve.io.vn/`, build `main` bbb1c1a7 (CD staging of
22/11 06:15Z), after the staging AI server was back (it answered 503 MSG06 from 06:45 that morning, BUG-335).

## Purpose and rules

- Measures the web Knowledge Chatbot (UC-51, AI-12, GBR-39) with real Published knowledge, as the AI-quality check of
  Report 5.0 §2.1 and the AI rows of Report 5.4 (BUG-246 evidence).
- Asker: `ticket-resolver@` (Department IT Support, the Employee IT of the round-2 role plan), signed in to the web app.
  Each question was sent as a new conversation (`history` empty) with the same request the chatbot page sends
  (`POST /api/v1/chatbot/answers`), one after the other, and the JSON answer was recorded (outcome, text, citations).
- Sources on staging: about 320 Published IT Support tickets (CSV sample data of DP, the round-2 import R2-001 / R2-002,
  T2 = DP-109 after W-2.08) and the two round-2 Wiki documents `ST2-VPN-guide-company` (Company-wide) and
  `ST2-server-room-IT-only` (Department IT Support).
- Per answer: **Has source** (at least one citation), **Correct citation** (a cited source is on the expected topic —
  for tickets judged from the cited Symptoms, since the chatbot hides ticket ids by design), **NO_SOURCE** (outcome
  `NO_SOURCE`, the fixed reply MSG109), and the answer time in seconds (request to response).
- G-01 to G-20, G-22 to G-26 are answerable (25). G-21 (a topic whose only ticket is not Published) and G-27 to G-30 must
  get NO_SOURCE (5).

## Results

| Metric | Formula | Result |
|---|---|---|
| Answers with a source | answerable questions answered with ≥ 1 citation / 25 | **23 / 25 = 92 %** |
| Correct citation | answers whose citations are on the expected topic / 25 | **23 / 25 = 92 %** (every cited answer was on topic) |
| NO_SOURCE on answerable questions | NO_SOURCE / 25 (lower is better) | **2 / 25 = 8 %** (G-23, G-24) |
| Correct NO_SOURCE | NO_SOURCE / 5 no-source questions | **5 / 5 = 100 %** (no invented answer) |
| Answer time | all 30 | median **5.6 s**, mean 6.3 s, max 14.5 s |

Findings: G-23 and G-24 are answered by the sources (extension 7742 in `ST2-server-room-IT-only`; the VPN web portal as
the workaround of the VPN guide and of the VPN tickets) but got NO_SOURCE — retrieval or the answer step misses short
factual questions. G-02 (Vietnamese question) was answered in English, copied from the English Wiki text (BUG-247, the
answer language is not pinned). Many answers cite 4–5 near-identical CSV sample tickets (the sample data repeats
the same Solution), which fills the citation list with duplicates.

## Questions and answers

| ID | Question (as typed) | Expected source (title) | Outcome | Cited (first) | Correct | Time (s) |
|---|---|---|---|---|---|---|
| G-01 | Tôi không kết nối được VPN khi làm việc ở nhà, phải làm sao? | VPN tickets; Wiki ST2-VPN-guide-company | ANSWERED | W ST2-VPN-guide-company; T FortiClient "Connection lost" (×4) | yes | 9.4 |
| G-02 | VPN báo Authentication failed sau khi tôi đổi mật khẩu | Wiki ST2-VPN-guide-company | ANSWERED (in English) | W ST2-VPN-guide-company | yes | 5.0 |
| G-03 | Ổ đĩa mạng chia sẻ không hiện trong This PC | "Ổ đĩa mạng chia sẻ không hiện trên máy" | ANSWERED | T "File Explorer không có ổ S:…" (×4) | yes | 10.0 |
| G-04 | Xuất báo cáo trên hệ thống kho bị lỗi 500 | "Kho hàng WMS báo lỗi 500 khi xuất báo cáo" | ANSWERED | T "Bấm 'Xuất Excel' thì Kho hàng WMS trả về trang lỗi 500…" | yes | 14.5 |
| G-05 | Portal nội bộ báo lỗi 500 khi tôi export report | "Portal nội bộ báo lỗi 500 khi xuất báo cáo" | ANSWERED | T "…Portal nội bộ trả về trang lỗi 500…" | yes | 8.4 |
| G-06 | Tôi không nhận được mã OTP để đăng nhập chấm công | "Không nhận được mã OTP khi đăng nhập Hệ thống chấm công" | ANSWERED | T "Hệ thống chấm công báo đã gửi OTP…" | yes | 7.3 |
| G-07 | Outlook không nhận thư mới, hiện Disconnected | "Outlook không đồng bộ email mới" | ANSWERED | T "Thanh trạng thái Outlook hiện 'Disconnected'…" | yes | 5.7 |
| G-08 | Wi-Fi văn phòng cứ vài phút lại mất kết nối | Wi-Fi tickets (R2-001, "Wi-Fi văn phòng … chập chờn") | ANSWERED | T "Laptop kết nối được Wi-Fi nhưng mất mạng sau vài phút…" | yes | 6.3 |
| G-09 | Email tôi gửi cho khách hàng bị rơi vào thư rác | "Email gửi ra ngoài bị vào spam của khách hàng" | ANSWERED | T "Email gửi tới Gmail của khách bị gắn nhãn spam…" | yes | 6.6 |
| G-10 | Laptop chạy rất chậm sau khi cập nhật Windows | "Laptop chạy rất chậm sau bản cập nhật Windows" | ANSWERED | T "CPU luôn ở mức 90%, … Antimalware Service Executable…" | yes | 9.7 |
| G-11 | Máy in trên tầng không in được tài liệu | "Máy in tầng 5/6/8 không in được" | ANSWERED | T "Lệnh in nằm mãi trong hàng đợi … 'Error - Printing'" | yes | 7.1 |
| G-12 | Tài khoản của tôi bị khóa vì nhập sai mật khẩu nhiều lần | "Tài khoản bị khóa do đăng nhập sai nhiều lần" | ANSWERED | T "Tài khoản bị khóa sau nhiều lần nhập sai mật khẩu" | yes | 6.0 |
| G-13 | Dữ liệu kho không đồng bộ sang ERP | "Không đồng bộ được dữ liệu Kho hàng WMS với ERP" | ANSWERED | T "Log tích hợp báo 'HTTP 401 Unauthorized'…" | yes | 7.1 |
| G-14 | Màn hình thứ hai không nhận khi cắm qua dock USB-C | "Màn hình phụ không nhận qua dock USB-C" | ANSWERED | T "Màn hình báo 'No signal', laptop vẫn sạc qua dock." | yes | 7.1 |
| G-15 | Không chia sẻ màn hình được trong cuộc họp Teams | "Teams không chia sẻ được màn hình" | ANSWERED | T "Bấm 'Share' thì người khác chỉ thấy màn hình đen." | yes | 4.8 |
| G-16 | Cài phần mềm từ Company Portal bị lỗi | "Không cài được phần mềm từ Company Portal" | ANSWERED | T "Company Portal báo lỗi 0x87D1041C…" | yes | 4.7 |
| G-17 | Không đăng nhập được WMS sau khi đổi mật khẩu | "Không đăng nhập được Kho hàng WMS sau khi đổi mật khẩu" | ANSWERED | T "Màn hình đăng nhập Kho hàng WMS báo 'Sai tên đăng nhập…'" | yes | 5.5 |
| G-18 | HRM không đồng bộ dữ liệu với ERP | "Không đồng bộ được dữ liệu HRM với ERP" | ANSWERED | T "Log tích hợp báo 'HTTP 401 Unauthorized'…" | yes | 5.2 |
| G-19 | Không mở được file PDF đính kèm trên portal | "Portal nội bộ không mở được file đính kèm PDF" | ANSWERED | T "Bấm vào file PDF thì tab trình duyệt trống…" | yes | 4.0 |
| G-20 | File Excel trên ổ mạng báo đang bị khóa, không sửa được | R2-002 "Không mở được file Excel trên ổ chia sẻ" | ANSWERED | T "Mở file Excel trên ổ chia sẻ báo file is locked for editing" | yes | 5.0 |
| G-21 | Thanh toán Internet Banking bị trừ tiền nhưng báo thất bại | none Published (only ticket is Changes requested) | NO_SOURCE | – | yes (expected) | 3.3 |
| G-22 | Làm sao xin quyền vào phòng máy chủ SRV-ALPHA-7? | Wiki ST2-server-room-IT-only | ANSWERED | W ST2-server-room-IT-only | yes | 4.9 |
| G-23 | Số điện thoại khẩn cấp của kỹ sư trực phòng máy là gì? | Wiki ST2-server-room-IT-only (ext. 7742) | **NO_SOURCE** | – | **no** | 4.4 |
| G-24 | VPN web portal dùng khi nào? | Wiki ST2-VPN-guide-company; VPN tickets (Workaround) | **NO_SOURCE** | – | **no** | 3.8 |
| G-25 | Khách hàng báo lỗi ACC-LOCK-403 | T2 "Login báo lỗi, không vào được để xem đơn hàng" | ANSWERED | T "Khách hàng bấm đăng nhập … 'Tài khoản đang bị khóa'…" (T2) | yes | 5.2 |
| G-26 | My shared network drive does not show up in File Explorer | "Ổ đĩa mạng chia sẻ không hiện trên máy" | ANSWERED (in English) | T "File Explorer không có ổ S:…" | yes | 5.3 |
| G-27 | Chính sách nghỉ thai sản của công ty là gì? | none | NO_SOURCE | – | yes | 2.8 |
| G-28 | Làm sao đặt phòng họp tầng 12? | none | NO_SOURCE | – | yes | 9.6 |
| G-29 | Máy pha cà phê ở pantry bị hỏng thì báo ai? | none | NO_SOURCE | – | yes | 6.0 |
| G-30 | Cách cấu hình máy chủ Kubernetes production? | none | NO_SOURCE | – | yes | 3.0 |

Permission check (W-3.09, asked as `admin@`, Department HR): "Làm sao xin quyền vào phòng máy chủ SRV-ALPHA-7?" →
NO_SOURCE; the IT-only document is neither used nor cited. The same question as `ticket-resolver@` (IT Support) is
answered from it (G-22, W-3.10).
