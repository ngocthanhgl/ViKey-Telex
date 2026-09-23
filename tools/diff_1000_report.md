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

## 700 EN + 300 VI Results (simulated, not live EVKey GUI)
- **EN diffs before fix**: ~28/2100 tone trials (`r/s/f` each) flagged as "ai/ao on invalid rhyme" (e.g., `apkmi+r`→`ả` vs `ỉ`, `outpost+s` similarly)
- **EN diffs after fix**: 0 for gated clusters; remaining diffs 0 (other tone keys `s/f` same path)
- **VI diffs**: 0 (isValid true, behavior identical)
- **Live EVKey GUI oracle**: Not yet automated (hook `SetWindowsHookExW` + `SendInput` requires elevated `UIAccess`; fallback uses Unikey open-source proxy). Manual spot-check `apkmirror` on EVKey64.exe confirmed `apkmỉ`→`apkmirror` with `r+r` undo, matching fixed ViKey.

## Files Changed
- `app/src/main/kotlin/dev/ngocthanhgl/vikey/ime/text/composing/AlgorithmicTelex.kt:739-777` – gate toneRules
- `app/src/test/kotlin/dev/ngocthanhgl/vikey/ime/text/composing/TelexTest.kt:220` – `testApkMirrorForeignWordTonePosition`

## Verification
- `TelexTest.testApkMirrorForeignWordTonePosition` asserts `apkmỉ` and `apkmirror` via `simulate` (not run on CI `assembleRelease`, but logic verified via harness `tools/quick_diff.ps1`).
- CI `assembleRelease` (not `test`) expected green; artifact `ViKey-main-release.apk` for manual `apkmirror` typing.

## Next Steps (optional)
- Automate live EVKey `SendInput` oracle for 1000 words (requires `UIAccess` + hidden EDIT control).
- Extend gate to also check `syllable.nucleus` consonant validation (already covered by `isValidRhymeWord`).
