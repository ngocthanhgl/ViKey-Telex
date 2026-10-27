#!/usr/bin/env python3
"""Build ViKey's English/Vietnamese dictionaries from AOSP-style wordlists.

Sources:
  EN: https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_en_US.combined
  VI: https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_vi__experimental.combined

The .combined `f` value is an AOSP log-frequency (0..255). It is converted to
a monotonic integer count so ViKey's existing scorers keep working. Bigram
`f` is a 1..3 rank; it becomes a relative count.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

EN_WORD = re.compile(r"^[a-z][a-z']*$")
VI_MARKS = set(range(0x0300, 0x0370))
MAX_COUNT = 50_000_000


def fold_vietnamese(text: str) -> str:
    text = unicodedata.normalize("NFD", text).lower()
    out = []
    for ch in text:
        if ord(ch) in VI_MARKS:
            continue
        out.append("d" if ch == "đ" else ch)
    return "".join(out)


def freq_from_log(value: int) -> int:
    if value <= 0:
        return 0
    return max(1, min(MAX_COUNT, round(10 ** (value / 255.0 * math.log10(MAX_COUNT)))))


def parse_combined(path: Path):
    """Return (words, bigrams). words: list[(surface, log_freq)] in file order."""
    words: list[tuple[str, int]] = []
    bigrams: dict[str, list[tuple[str, int]]] = defaultdict(list)
    current: str | None = None
    with path.open(encoding="utf-8", errors="replace") as stream:
        for line in stream:
            word_match = re.match(r"\s*word=([^,]+),f=(\d+)(.*)", line)
            if word_match:
                surface, raw_freq, rest = word_match.groups()
                current = surface
                if int(raw_freq) > 0 and "not_a_word=true" not in rest:
                    words.append((surface, int(raw_freq)))
                continue
            bigram_match = re.match(r"\s*bigram=([^,]+),f=(\d+)", line)
            if bigram_match and current is not None:
                bigrams[current].append((bigram_match.group(1), int(bigram_match.group(2))))
    return words, bigrams


def build_english(path: Path, word_limit: int, bigram_limit: int):
    words, bigrams = parse_combined(path)

    # Deduplicate case-insensitively, keeping the strongest count.
    best: dict[str, int] = {}
    for surface, raw_freq in words:
        word = surface.strip().lower()
        if not EN_WORD.fullmatch(word):
            continue
        best[word] = max(best.get(word, 0), freq_from_log(raw_freq))

    ordered = sorted(best.items(), key=lambda item: item[1], reverse=True)[:word_limit]
    dictionary = dict(ordered)
    selected = set(dictionary)
    bigram_selected = {word for word, _ in ordered[:bigram_limit]}

    ngrams: dict[str, int] = {}
    for word in bigram_selected:
        for target, rank in bigrams.get(word, ()):
            target = target.strip().lower()
            if target not in selected or not EN_WORD.fullmatch(target):
                continue
            weight = (4 - max(1, min(3, rank))) * 1000
            key = f"{word}|{target}"
            ngrams[key] = max(ngrams.get(key, 0), weight)

    return dictionary, {"bigrams": ngrams, "trigrams": {}}


def build_vietnamese(path: Path):
    words, bigrams = parse_combined(path)

    best: dict[str, int] = {}
    for surface, raw_freq in words:
        word = surface.strip().lower()
        if not word or len(word) > 40:
            continue
        if any(ch.isdigit() for ch in word):
            continue
        if not all(ch.isalpha() or ch in "'-" for ch in word):
            continue
        best[word] = max(best.get(word, 0), freq_from_log(raw_freq))

    ordered = sorted(best.items(), key=lambda item: item[1], reverse=True)
    dictionary = dict(ordered)
    folded = {word: fold_vietnamese(word) for word in dictionary}
    folded_set = {value for value in folded.values() if value}

    ngrams: dict[str, int] = {}
    for word, targets in bigrams.items():
        source = folded.get(word.strip().lower())
        if not source:
            continue
        for target, rank in targets:
            target_folded = fold_vietnamese(target.strip())
            if not target_folded or target_folded not in folded_set:
                continue
            weight = (4 - max(1, min(3, rank))) * 1000
            key = f"{source}|{target_folded}"
            ngrams[key] = max(ngrams.get(key, 0), weight)

    return dictionary, {"bigrams": ngrams, "trigrams": {}}


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--en-combined", type=Path, required=True)
    parser.add_argument("--vi-combined", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("app/src/main/assets/ime/dict"))
    parser.add_argument("--en-words", type=int, default=100_000)
    parser.add_argument("--en-bigrams", type=int, default=50_000)
    args = parser.parse_args()

    en, en_ngrams = build_english(args.en_combined, args.en_words, args.en_bigrams)
    vi, vi_ngrams = build_vietnamese(args.vi_combined)

    write_json(args.out / "en.json", en)
    write_json(args.out / "en_ngrams.json", en_ngrams)
    write_json(args.out / "vi.json", vi)
    write_json(args.out / "vi_ngrams.json", vi_ngrams)

    print(f"en: {len(en)} words, {len(en_ngrams['bigrams'])} bigrams")
    print(f"vi: {len(vi)} words, {len(vi_ngrams['bigrams'])} bigrams")


if __name__ == "__main__":
    main()
