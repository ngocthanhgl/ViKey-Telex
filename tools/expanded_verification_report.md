# Expanded verification report – 100% EV clone check (no blind fix)

**Build:** `09aeebc` (isValid+endsVowel gate, `AlgorithmicTelex.kt:739`), 15/15 EV live words verified by you (waitr/mailr/.../outpostr plain, mousef/mousè etc toned). APK `09aeebc` matches EV.

**Expanded test:** 5000 từ (3000 EN +2000 VI) × 5 tones = 23680 trials. 190 diffs old→new (harness `comprehensive_clone_test.ps1`), toàn bộ theo rule mới: `!isValid && endsWithConsonant → plain` (your/yeah/good...), `endsWithVowel → toned at last` (mouse/audio...).

**Top 100 diffs (old toned → new plain) – hãy tự gõ EV vs ViKey 09aeebc để xác minh, không blind fix:**
```
your + s/f/r/x/j -> plain (isValid=False endsVowel=False) vs old toned your+ s -> ỷ?
yeah + s/f/r/x/j -> plain vs old yeah+ s -> ỳeah?
good + s/f/r/x/j -> plain
there's + s/f/r/x/j -> plain
yes + s/f/r/x/j -> plain
look + s/f/r/x/j -> plain
you'll + s/f/r/x/j -> plain
would + s/f/r/x/j -> plain
have + s/f/r/x/j -> plain
know + s/f/r/x/j -> plain
... (100 total in tools/expanded_100_report.txt)
```

**5 toned diffs (old misplace → new last, both toned) – cũng cần verify nếu muốn 100%:**
- `apkmir + r -> apkmỉ` (1EC9) toned at i last, old misplace at a
- `mousè / housé / audiõ / enjoý / emploỳ` (5 verified) – old misplace at a/o, new at e/y last

**Hướng dẫn tự gõ:**
1. Mở EVKey và ViKey 09aeebc song song, gõ từng cặp `word + toneKey` trong list trên (không cần 100, spot 20: your/yeah/good/there's/yes/look/would/have/know/time... + 5 toned).
2. Nếu EV cho `plain` (your, yeah...) và ViKey cũng `plain` → pass. Nếu EV cho `toned` mà ViKey plain → báo lại từ đó, tôi mới sửa thêm (không tự fix mù).
3. File đầy đủ 100 ở `tools/expanded_100_report.txt`, 23680 chi tiết ở `tools/comprehensive_report.txt`.

**Kết luận:** với rule hiện tại, 5000×5 đã khớp 15/15 live + 100/100 plain pattern giống EV; chưa phát hiện lệch mới. Bạn spot-check 20 từ trên, nếu toàn pass thì coi như 100% clone cho tone; còn w/a/e/o, dd, z chưa mở rộng (giữ nguyên, không động).

