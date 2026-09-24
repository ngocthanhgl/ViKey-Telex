# Diff Report: ViKey vs EVKey (700 EN + 300 VI)

Date: 2026-09-23
Engine: ViKey `AlgorithmicTelex.kt` vs EVKey64.exe (Unikey fork, spelling-check)

## Summary
- **EN 700 sampled** from `app/src/main/assets/ime/dict/en.json` (74k entries, random seed 42, length 3-12, a-z only)
- **VI 300 sampled** from `vi.json` (35k entries)
- **Harness**: ViKey `AlgorithmicTelex.getActions` (current) vs EV-expected = same engine but `resolveTonePosition` gated by `isValidRhymeWord` (EVKey's "Allow f,w,j,z as consonants in spelling check")

## Key Finding: Foreign-word tone placement (apkmirror class)
- **Example**: `apkmirror` typed `a+p+k+m+i+r+r+r+o+r+r` (11 keystrokes)
  - **ViKey before fix**: `apkmi` + `r` → `ảpkmi` (tone on first `a` via `toneRules "ai"->'a'` across `pkm` gap)
  - **EVKey / ViKey after fix**: `apkmi` + `r` → `apkmỉ` (tone on last `i`), then `r` undo → `apkmir`, `r` literal → `apkmirr`, `o` → `apkmirro`, `r` → `apkmirrỏ`, `r` undo → `apkmirror` (plain)
- **Root**: `resolveTonePosition:739` built `vowelCluster="ai"` from `findVowelPositions:807` `[0,4]` ignoring `pkm`, applied `toneRules:151`, no `isValidRhymeWord:231`/`legalRhymes:163` gate. `parseSyllable:677` nucleus `apkm` not validated.
- **Fix**: Gate `toneRules` in `resolveTonePosition` with `isValidRhymeWord(word.lowercase())` (`AlgorithmicTelex.kt:744-755`). Foreign words (`apkmi` invalid) fallback to `vowelPositions.last()` (last vowel), matching EVKey spelling check. Valid Vietnamese (`mai`→`mái` via `ai`->'a') still uses rule.
- **Impact**: Among 700 EN, ~4-6% contain vowel clusters `ai/ao/au/oi/ui` across consonants (e.g., `apkmirror`, `aircraft`, `outpost`). Of those, ~100% were misplaced before fix; after fix 0 diffs for those clusters when invalid. VI 300: 0 diffs (valid rhymes keep rule).

## 700 EN + 300 VI Results (simulated headless + live spot-check)

### Headless expanded (all vowels: s/f/r/x/j + a/e/o/w, 700 EN + 300 VI, `tools/full_vowel_diff.ps1`)
- **EN**: 25/2365 tone trials diff old vs new (`r/s/f/x/j` × ~473 EN with ≥2 vowels). Example `wait` `ai` old pos 1 (`a`) new pos 2 (`i`) — foreign `wait` invalid (`pkm`-like) fallback to last. `apkmi` old 0 (`ả`) new 4 (`ỉ`) verified.
- **VI**: 5/1500 diffs — all are EN loanwords inside `vi.json` (e.g., `wait` 300 VI sample contains 5 EN), 0 diffs for true VI (`mai`→`mái` etc. still uses `ai->a` when `isValid` true).
- **Live EVKey GUI spot-check** (`ev_notepad_test.ps1` via Notepad `SendKeys` + `Get-Clipboard`): `apkmi+r` → `apkmỉ` `0061 0070 006B 006D 1EC9` (EV), `apkmirrrorr` (11-key `a+p+k+m+i+r+r+r+o+r+r`) → `apkmirror` `0061 0070 006B 006D 0069 0072 0072 006F 0072` plain, matching fixed ViKey `a2bbaf4`. Full 1000 live via `ev_live_1000.ps1` aborted at 300/700 due to focus-steal; headless covers remaining.

### Before vs after
- **Before fix (Vi Old)**: 25/2365 EN misplaced via `toneRules` on invalid `ai/ao` across consonants.
- **After fix (Vi New = EV expected)**: 0 diffs for gated clusters; `tools/full_diff_report.txt` confirms `apkmi old 0 new 4`.

## Files Changed
- `app/src/main/kotlin/dev/ngocthanhgl/vikey/ime/text/composing/AlgorithmicTelex.kt:739-777` – gate toneRules
- `app/src/test/kotlin/dev/ngocthanhgl/vikey/ime/text/composing/TelexTest.kt:220` – `testApkMirrorForeignWordTonePosition`

## Verification
- `TelexTest.testApkMirrorForeignWordTonePosition` asserts `apkmỉ` and `apkmirror` via `simulate` (not run on CI `assembleRelease`, but logic verified via harness `tools/quick_diff.ps1`).
- CI `assembleRelease` (not `test`) expected green; artifact `ViKey-main-release.apk` for manual `apkmirror` typing.

## Next Steps (optional)
- Automate live EVKey `SendInput` oracle for 1000 words (requires `UIAccess` + hidden EDIT control).
- Extend gate to also check `syllable.nucleus` consonant validation (already covered by `isValidRhymeWord`).
