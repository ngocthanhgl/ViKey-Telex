# Contribution guidelines

Thanks for considering contributing to ViKey!

There are several ways to contribute. This document provides some general guidelines for each type of contribution.

All contributions, including issues, pull requests must be in English and follow the [code of conduct](CODE_OF_CONDUCT.md).

## Bug reporting

Use the pre-made [bug report template](https://github.com/ngocthanhdev81/ViKey-Telex/issues/new?template=bug_report.yml).

## Feature proposals

Use the feature proposal [issue template](https://github.com/ngocthanhdev81/ViKey-Telex/issues/new?template=feature_request.yml).

## Code contributions

You are always welcome to contribute. It is best to quickly ask if someone is already working on an issue to avoid duplicates.

### System requirements for development

- Desktop PC with Linux or WSL2 (Windows)
- At least 16GB of RAM (because of Android Studio / IntelliJ)
- The following tools must be installed:
  - Android Studio (bundles SDK and NDK) or IntelliJ with Android and Compose plugin
  - Java 17
  - CMake 3.22+
  - Clang 15+
  - Git
  - [Rust](https://www.rust-lang.org/tools/install)
- Utilities (optional)
  - Python 3.10+
  - Bash, realpath, grep, ...

### Manual build without Android Studio

Ensure Android SDK and NDK are properly installed. Then run:

```./gradlew clean && ./gradlew assembleDebug```
