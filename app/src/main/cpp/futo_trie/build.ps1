# Builds libfuto_trie.so for every Android ABI and copies it into jniLibs.
# Requires: Android SDK CMake 3.22.1+ and NDK r27+.
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Resolve-Path (Join-Path $root "..\..\..\..\..")
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$ndk = (Get-ChildItem (Join-Path $sdk "ndk") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$cmake = Join-Path $sdk "cmake\3.22.1\bin\cmake.exe"
if (-not (Test-Path $cmake)) { $cmake = "cmake" }
$env:PATH = (Join-Path $sdk "cmake\3.22.1\bin") + ";" + $env:PATH

$abis = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
foreach ($abi in $abis) {
    $build = Join-Path $repo "build\futo_trie\$abi"
    & $cmake -S $root -B $build -G Ninja `
        "-DCMAKE_TOOLCHAIN_FILE=$(Join-Path $ndk 'build\cmake\android.toolchain.cmake')" `
        "-DANDROID_ABI=$abi" `
        "-DANDROID_PLATFORM=android-26" `
        "-DANDROID_STL=c++_shared" `
        "-DCMAKE_BUILD_TYPE=Release"
    & $cmake --build $build
    $dst = Join-Path $repo "app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force $dst | Out-Null
    $so = Join-Path $build "libfuto_trie.so"
    $strip = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe"
    if (Test-Path $strip) { & $strip --strip-all $so }
    Copy-Item $so $dst -Force
    Write-Host "built $abi"
}
