cmake_minimum_required(VERSION 3.22.1)

project("cafetone")

# Set C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- FIX FOR 16KB ALIGNMENT ---
# Add the required linker flag for 16KB page size compatibility.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
# -----------------------------

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

# Compiler flags for optimization (simplified for debugging)
target_compile_options(cafetone-dsp PRIVATE
        -O2
        -Wall
        -Wextra
)