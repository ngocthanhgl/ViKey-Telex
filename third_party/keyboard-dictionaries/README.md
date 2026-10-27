# Keyboard dictionaries

ViKey's English and Vietnamese suggestion dictionaries come from the same
AOSP-compatible dictionary set used by HeliBoard / OpenBoard, not from a raw
web corpus.

## Source files

- English:
  `https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_en_US.combined`
  - 160,715 entries and top-3 bigrams
  - Origin: OpenBoard v1.4.5 AOSP `en_wordlist.combined`
  - License: Apache-2.0
- Vietnamese:
  `https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_vi__experimental.combined`
  - 11,755 entries and bigrams
  - Origin: Leipzig Corpora Collection
  - License: CC BY 4.0
- Repository (commit `ff0cd52be8d9530200fefe8b296c53d4f662091e`):
  https://codeberg.org/Helium314/aosp-dictionaries

## Generated files

- `en.json`: top 100,000 English words
- `en_ngrams.json`: bigrams for the top 50,000 English words
- `vi.json`: all 11,754 Vietnamese entries
- `vi_ngrams.json`: all Vietnamese bigrams (diacritics folded)

Regenerate with:

```bash
python scripts/generate_keyboard_dicts.py \
  --en-combined /path/to/main_en_US.combined \
  --vi-combined /path/to/main_vi__experimental.combined
```

The FUTO Swipe dictionary (`app/src/main/assets/futo-swipe/en_wordlist.combined`)
is separate and intentionally unchanged: it is tuned for the FUTO decoder.
