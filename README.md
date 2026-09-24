<table align="center" width="100%">
  <tr>
    <td width="140" valign="middle">
      <img src=".github/repo_icon.png" width="128" alt="ViKey">
    </td>
    <td valign="middle">
      <h1>ViKey</h1>
      <p><strong>Bàn phím Telex Tiếng Việt cho Android</strong></p>
      <p>
        Engine telex thuần thuật toán, <strong>độc lập thứ tự phím</strong> —<br>
        gõ sai thứ tự vẫn ra đúng một kết quả.
      </p>
      <p>
        <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin">
        <img src="https://img.shields.io/badge/API_26+-3DDC84?style=flat&logo=android&logoColor=white" alt="API 26+">
        <img src="https://img.shields.io/badge/License-Apache_2.0-blue" alt="License">
      </p>
    </td>
  </tr>
</table>

---

## Triết Lý

Vikey tập trung vào **bộ gõ thuần** như **Unikey** hay **EVKey** trên desktop — engine telex thuần thuật toán, **phản hồi tức thì, không delay**. Không như Gboard hay SwiftKey vốn có AI và ML chạy ngầm gây trễ và phụ thuộc mạng, Vikey gõ tới đâu ra tới đó.

Engine parse cấu trúc âm tiết thay vì tra bảng mutation. Độc lập thứ tự phím — gõ sai thứ tự vẫn ra đúng một kết quả:

```
tuaws = tuwas = tuaw s = tuw as  →  tựa
```

| Tổ hợp | Kết quả |
| --- | --- |
| `aw` / `aa` / `ee` / `oo` / `ow` / `uw` / `dd` | ă / â / ê / ô / ơ / ư / đ |
| `uow` | ươ |
| Gõ lại phím tắt lần hai | Undo (`ưw` → `uw`) |
| `z` cuối từ | Xoá toàn bộ dấu (`chàoz` → `chao`) |

Các tính năng như gợi ý từ, theme, glide typing chỉ là phụ — có sẵn nhưng không phải trọng tâm, mặc định nhẹ hoặc tắt. Vì tập trung vào gõ thuần nên Vikey khá kén người dùng.

## Quyền Riêng Tư

**Zero network access. Zero tracking. Zero analytics.** Mọi thao tác gõ phím ở lại trên máy bạn — không Internet, engine + từ điển local 100%. Nhưng với người thích Privacy thì Vikey hoàn toàn là lựa chọn phù hợp nhất ngoài kia.

```
╭──────────────────────────────────────────────╮
│  Mọi thao tác gõ phím → ở lại trên máy bạn   │
│  Không Internet → không gửi dữ liệu đi đâu   │
│  Engine + từ điển local 100%                 │
╰──────────────────────────────────────────────╯
```

## Download

Tải về từ [Releases](https://github.com/ngocthanhgl/ViKey-Telex/releases). Mỗi tag release đều có APK build tự động.

## Cảm Ơn

Dự án có sử dụng AI coding hỗ trợ xuyên suốt quá trình phát triển: **DeepSeek V4 Flash** giai đoạn đầu và **Ox Alpha** giai đoạn sau.

## Giấy Phép

Apache 2.0. Xem [LICENSE](LICENSE).

Bản quyền gốc © 2020-2026 The FlorisBoard Contributors.  
ViKey Telex engine © 2026 Nguyễn Ngọc Thành.
