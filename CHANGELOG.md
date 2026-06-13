# Changelog

## v2.1.3 (2026-06-13)

### Added
- JoyPixels emoji font (replaces Apple 42MB font with 29MB JoyPixels 10.0, Unicode 16.0)
- CHANGELOG.md

### Changed
- NLP provider dropdown: removed placeholder "-select-" option, shows only "None" and "Vietnamese (TFLite)"
- Emoji font label renamed from "Apple emoji" to "JoyPixels" in settings

### Removed
- Support for all non-English/Vietnamese languages (70+ subtype presets removed)
- 80+ foreign keyboard layouts (kept QWERTY + Western symbols/numeric)
- Hangul (Korean) and Kana (Japanese) composers (kept Appender + Telex only)
- LatinLanguageProvider (general Latin suggestion engine)
- Language packs: German/Japanese (`org.florisboard.languagepack`) and Chinese shape-based (`org.florisboard.hanshapebasedbasicpack`)
