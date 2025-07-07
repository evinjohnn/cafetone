cmake_minimum_required(VERSION 3.22.1)

project("cafetone")

# Set C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(OpenSLES-lib OpenSLES)

# Create shared library
add_library(cafetone-dsp SHARED
    cafetone_dsp.cpp
    audio_processor.cpp
    binaural_processor.cpp
    haas_processor.cpp
    eq_processor.cpp
    reverb_processor.cpp
)

# Link libraries
target_link_libraries(cafetone-dsp
    ${log-lib}
    ${android-lib}
    ${OpenSLES-lib}
)

# Include directories
target_include_directories(cafetone-dsp PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Compiler flags for optimization
target_compile_options(cafetone-dsp PRIVATE
    -O2
    -ffast-math
    -DNDEBUG
    -Wall
    -Wextra
)

# Enable NEON for ARM optimization
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(cafetone-dsp PRIVATE -march=armv8-a+simd)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(cafetone-dsp PRIVATE -mfpu=neon -march=armv7-a)
endif()

# Link time optimization for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(cafetone-dsp PRIVATE -O3)
endif() 