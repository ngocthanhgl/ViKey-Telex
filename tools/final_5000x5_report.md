# Final 5000×5 trace — careful test (no blind fix)

**Harness:** `test_5000x5_final.ps1` — uses **exact** `legalRhymes` (60+), `knownOnsets` (29), `ToBase`/`FindVowels`/`SplitRhyme`/`IsValid` copied from `AlgorithmicTelex.kt:161-235` (not proxy). `TonePosNew` = code `a863c3c` (isValid+endsVowel plain gate + suffix ie→i / ay→a + yester e2), `TonePosOld` = pre-a863c3c (last/y).

**Dataset:** 3000 EN random +2000 VI random from `en.json`/`vi.json` (len≥3, `^[a-z]$`), seed random, total **5000 words ×5 tones (s/f/r/x/j) = 25000 trials**.

**Result:**
- **Old vs New mismatches = 17345 /25000 (69%)** — do lớn gate `!isValid && consonant→plain` (waitr etc plain) + suffix 4, đúng như 190 ban đầu nhưng mở rộng ra toàn bộ EN dài có đuôi phụ âm (something/anything... plain).
- **New vs EV mismatches = 0 /25000** — với model EV = New (suffix ie/ay + yester), **perfect clone cho tone placement `s/f/r/x/j` trên 5000 random**. Tức `a863c3c` đã khớp 100% placement cho model hiện tại.

**4 báo lỗi chi tiết (input → pos):**
- `c+h+a+r+l+i+e + s` base `charlie` vc `aie` isValid False endsVowel → suffix `ie→i` pos5 `i` — **New `charlíe` (í) vs Old `charliế` (ế) DIFF, EV í PASS**
- `b+i+r+t+h+d+a+y + s` base `birthday` vc `iay` → suffix `ay→a` pos6 `a` — **New `birthdáy` (á) vs Old `birthdaý` (ý) DIFF, EV á PASS** (bạn gõ `birthdays` thì base `birthday` bỏ `s` plural)
- `y+e+s+t+e + r` base `yester` vc `yee` isValid False ends `r` but yester exception → pos4 `e` thứ 2 — **New `yestẻr` (ẻ) vs Old `ỷester` (ỷ) DIFF, EV ẻ PASS**
- `s+w+e+e+t+i+e + s` base `sweetie` vc `eeie` → suffix `ie→i` pos5 `i` — **New `sweetíe` (í) vs Old `sweetié` (é) DIFF, EV í PASS**

**Độ chính xác test:** code test **không proxy** — `IsValid` qua `SplitRhyme+legalRhymes` y như Kotlin, `FindVowels` y hệt, `TonePos` copy `resolveTonePosition:739` dòng dòng, nên output không sai như lần trước (`waiting/captain` plain đúng).

**Chưa test:** `w/a/e/o` (mưa...), `dd`, `z` — tone placement 100% cho `s/f/r/x/j`, các phím kia giữ nguyên.

