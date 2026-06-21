<p align="center">
  <img src=".github/repo_icon.png" width="96" height="96" alt="ViKey">
</p>

<h1 align="center">ViKey</h1>

<p align="center">
  <strong>Bàn phím Tiếng Việt Telex cho Android</strong><br>
  IME Telex mã nguồn mở với engine xử lý ngữ âm dựa trên kiến trúc FST, gợi ý theo thời gian thực.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/API_26+-3DDC84?style=flat&logo=android&logoColor=white" alt="API 26+">
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue" alt="License">
</p>

## Sự Ra Đời Của ViKey

ViKey ban đầu chỉ là một app dùng cho mục đích cá nhân. Tôi là người đam mê bảo mật nhưng tìm mãi không có app bàn phím Android nào đáp ứng nhu cầu. Đã thử Heliboard, FUTO, Openboard, Fcitx5 và cuối cùng là FlorisBoard — tất cả đều chỉ dừng lại ở mức gõ **được** chứ chưa gõ **đúng**. Vì vậy tôi quyết định build lại từ nền tảng FlorisBoard, bắt đầu bằng việc viết lại engine Telex mới. Sau đó tôi quyết định trau chuốt để public cho mọi người cùng sử dụng.

```kotlin
// FlorisBoard (mutation-based): "chaof" → "chào"
//   cần entry trong bảng cho "ào" hoặc thay thế theo chuỗi
// ViKey (FST-based): parse("chao") + applyTone('f') → "chào"
//   xử lý theo trạng thái — đọc đúng dấu thanh ngay khi đang gõ, chưa cần commit
```

### Tái Tổ Hợp Âm Tiết Theo Thời Gian Thực

Nhờ kiến trúc FST, mỗi lần gõ phím — kể cả khi đổi dấu thanh giữa chừng — đều được xử lý và phản hồi ngay lập tức:

```
Gõ phím → Phân rã → Phân tích âm tiết → Áp dụng quy tắc → Tái tạo → Xuất ra
```

Ví dụ: gõ **"bá"** rồi đổi dấu thành **"bà"** → gợi ý lập tức chuyển sang **"bàn — bàng — bài"** mà không cần commit từ. Đây là điểm khác biệt lớn so với Telex kiểu cũ, vốn không đọc được dấu thanh đang gõ nên gợi ý không cập nhật real-time.

### Bộ Phân Tích Âm Tiết

Phân rã chuỗi Tiếng Việt thành các thành phần ngữ âm chuẩn:

```
  n g u y ễ n
  ↑↑   ↑↑  ↑
âm đầu  âm chính  âm cuối + thanh điệu
```

Xử lý đầy đủ các tổ hợp phụ âm đầu (`ngh`, `ng`, `ch`, `gh`, `gi`, `kh`, `nh`, `ph`, `th`, `tr`, `qu`) với cơ chế phòng tránh nhận nhầm.

### Đặt Dấu Thanh Theo Chính Tả

Dấu thanh được đặt theo quy tắc Quốc Ngữ 1984 qua 4 cấp ưu tiên:

1. **Quy tắc đôi/ba nguyên âm** — 30+ cụm nguyên âm được ánh xạ đến đúng vị trí đặt dấu
2. **Ưu tiên nguyên âm có móc** — `ê`, `ơ` được ưu tiên
3. **Ưu tiên nguyên âm có mũ/breve** — `â`, `ă`, `ô`
4. **Nguyên âm cuối** — quy tắc mặc định cho đôi nguyên âm đơn giản

---

## Tính Năng

### Hoàn Tác Bằng `z`

Gõ `z` ở cuối từ để xóa toàn bộ dấu thanh:

| Gõ | Kết quả |
|------|-----|
| `ƯỚz` | `ươ` |
| `chàoz` | `chao` |

Nếu không có dấu, `z` là ký tự thường.

### Hoàn Tác Phím Tắt

Nhấn phím tắt lần hai để hoàn tác:

| Gõ | Kết quả |
|------|-----|
| `aa` | `â` |
| `âa` | `aa` |
| `uw` | `ư` |
| `ưw` | `w` |
| `uow` | `ươ` |
| `ươw` | `uow` |

Hoạt động với cả 7 phím tắt: `aw(ă)` `aa(â)` `ee(ê)` `oo(ô)` `ow(ơ)` `uw(ư)` `dd(đ)`.

### Vòng Đời Phím `w`

| Gõ | Kết quả | Giải thích |
|------|-----|------|
| `w` | `w` | Ký tự đầu tiên → `w` |
| `kw` | `kư` | Phụ âm + `w` → `kư` |
| `kưw` | `kw` | Hoàn tác |
| `aw` | `ă` | Phím tắt |
| `uw` | `ư` | Phím tắt |

### Phím Tắt 3 Ký Tự

`uow` → `ươ` trong một thao tác — tính năng hiệu quả không có trong Telex chuẩn.

### Tự Nhận Diện Tiếng Anh

Ba heuristic ngăn biến đổi Telex khi gõ tiếng Anh:

1. **Pattern tiếng Anh** — `tion`, `ness`, `ship`, `str`, `ight`...
2. **Kiểm tra âm cuối** — chỉ `c`, `m`, `n`, `p`, `t` hoặc `ch`, `ng`, `nh`, `ngh` là âm cuối hợp lệ trong Tiếng Việt
3. **Mật độ nguyên âm** — chuỗi phụ âm quá 3 ký tự liên tiếp → nhận diện là tiếng Anh

Không cần chuyển chế độ thủ công.

---

## Giao Diện

| Sakura Dark | Valentine Light |
|:---:|:---:|
| <img src=".github/theme-dark.jpg" width="300" alt="Dark theme"> | <img src=".github/theme-light.jpg" width="300" alt="Light theme"> |

Và hơn 12 theme tùy chỉnh có sẵn, bao gồm theme **Liquid Glass** với hiệu ứng kính mờ, glow bloom.

---

## Điểm Nổi Bật

- **Hỗ trợ import GGUF model** — Bản Full cho phép import LLM (như Qwen) để gợi ý từ và gợi ý từ tiếp theo thông minh hơn. Bản Lite gọn nhẹ, không cần model.
- **Kiến trúc FST** — gợi ý, auto-correct và đặt dấu thanh phản hồi theo thời gian thực, ngay cả khi đổi dấu giữa chừng.
- **30+ quy tắc đặt dấu thanh** — bao phủ toàn bộ đôi và ba nguyên âm Tiếng Việt.
- **3 lớp nhận diện tiếng Anh** — pattern, kiểm tra âm cuối, mật độ nguyên âm.
- **Fork của FlorisBoard** — kế thừa toàn bộ tính năng (theme, layout, glide typing, clipboard, emoji, spell check) trong khi thay thế hoàn toàn engine Telex.
- **Tự động học cách gõ** — từ thường xuyên gõ sẽ xuất hiện trên thanh gợi ý, từ lâu không dùng sẽ tự động tụt hạng.
- **Tối ưu cho người Việt** — điểm khác biệt lớn nhất của ViKey là hỗ trợ gõ Tiếng Việt cực mạnh, không giống các IME khác luôn đặt Tiếng Việt ở mức ưu tiên thấp.

---

## Quyền Riêng Tư

ViKey theo triết lý privacy-first của FlorisBoard: **không truy cập mạng, không theo dõi, không analytics**. Mọi thao tác gõ phím đều ở lại trên điện thoại của bạn, không ai có thể theo dõi bạn.

---

## Giấy Phép

Apache 2.0. Xem [LICENSE](LICENSE).

Bản quyền gốc © 2020-2026 The FlorisBoard Contributors.  
ViKey Telex engine © 2026 Nguyễn Ngọc Thành.
