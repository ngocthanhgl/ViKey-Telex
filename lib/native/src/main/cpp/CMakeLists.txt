cmake_minimum_required(VERSION 3.22)
project(qwen_jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b4795
    GIT_SHALLOW TRUE
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_STATIC ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

add_library(qwen_jni SHARED qwen_jni.cpp)
target_include_directories(qwen_jni PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/ggml/include
)
target_link_libraries(qwen_jni PRIVATE
    llama
    android
    log
)
set_target_properties(qwen_jni PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
)
