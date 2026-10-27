cmake_minimum_required(VERSION 3.22)
project(futo_trie CXX)

add_library(futo_trie SHARED
    src/trie.cpp
    src/unicode_case.cpp
    jni/futo_trie_jni.cpp
)

target_include_directories(futo_trie PRIVATE include)
target_compile_features(futo_trie PRIVATE cxx_std_17)
target_compile_options(futo_trie PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_libraries(futo_trie PRIVATE log)
