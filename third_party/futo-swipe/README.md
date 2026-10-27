# FUTO Swipe integration

ViKey uses FUTO Swipe for English glide typing. Everything runs offline; the
models and dictionary are bundled in the APK.

## Pinned upstream

- Inference library (GPL-3.0):
  https://gitlab.futo.org/keyboard/swipe-library
  commit `1b13f2c85d6b347f6ea3fbc4b3aaf01fce42429a`
- Prebuilt Android AAR (GPL-3.0):
  https://gitlab.futo.org/keyboard/android-libs
  `futo-swipe-release.aar`
  SHA-256 `e46ecbc490c96a014e286fd9793f60b60d956dfc298ac2e7412a0f8569ed5d5f`
- Models (FUTO Model Weights License 1.0):
  https://huggingface.co/futo-org/futo-swipe
  revision `315d42583233b71d75665d076cfa2f2c7c6f43ad`

## Bundled models

| File | SHA-256 |
| --- | --- |
| `honorable_sturgeon/model_fp32.pte` | `725242bab5d14345e96ff214e8de2bfbc1f962c232d320df9c24cb82ffd1fbaf` |
| `magic_macaw/model_fp32.pte` | `01eaf16ac4bc0f1ed0698c240807f0e95e6d427bcf6de04983ffc50736744d85` |
| `hungry_jellyfish/context_lm.pte` | `74d29f56a513c0c60abcd43df3b16a6b68925cdf4e97e51b094a5275ec2810d7` |
| `hungry_jellyfish/vocab.txt` | `a7db66376783b5a23ee3d4a2aaa8f2499fd9b35f975e92bcb248664c2cf6ebd1` |

The English dictionary is a 50k-word subset of FUTO Keyboard's
`dictionaries/en_wordlist.combined.gz`, filtered for `f=0` (profanity) entries
and re-sorted by frequency. It is extracted from
https://github.com/futo-org/android-keyboard.

## Native trie loader

The published AAR has no API to create a dictionary trie from Kotlin. ViKey
builds a small `libfuto_trie.so` from the upstream `trie.cpp` /
`unicode_case.cpp` sources so `SwipeDecoder.setMode()` can receive an `ITrie*`
for the bundled `.combined` dictionary.

- Source: `app/src/main/cpp/futo_trie/`
- Build: `powershell -ExecutionPolicy Bypass -File app/src/main/cpp/futo_trie/build.ps1`
- Output: `app/src/main/jniLibs/<abi>/libfuto_trie.so`
- Requires Android NDK r27+; links against the `libc++_shared.so` already
  shipped by the FUTO AAR.

Source is available in this repository and at the upstream URLs above.
