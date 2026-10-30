cmake_minimum_required(VERSION 3.20)
project(pdfium_jni C)

# Supplied by the Konan plugin: where it wrote the JNI stub, and what the bindings will load.
set(KONAN_JNI_STUB_DIR "" CACHE PATH "")
set(KONAN_LIB_NAME "" CACHE STRING "")
set(KONAN_JNI_INCLUDE_DIR "" CACHE PATH "")

# pdfium ships PDFiumConfig.cmake; PDFium_DIR points at the unpacked release.
#
# Cross-compiling for Android, the NDK toolchain confines find_library and find_path to the sysroot,
# so a prebuilt library unpacked elsewhere is invisible and the error names the package rather than
# the cause. pdfium is exactly that, so the search is widened for it.
if(ANDROID)
    list(APPEND CMAKE_FIND_ROOT_PATH "${PDFium_DIR}")
    set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
    set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
endif()

find_package(PDFium REQUIRED)

file(GLOB JNI_SOURCES "${KONAN_JNI_STUB_DIR}/*.c")

# Hand-written alongside the generated bridges, for the calls the generator cannot express: anything
# taking or returning a raw pixel buffer. See masks.c.
list(APPEND JNI_SOURCES "${CMAKE_CURRENT_LIST_DIR}/masks.c")

add_library(pdfium-jni SHARED ${JNI_SOURCES})
set_target_properties(pdfium-jni PROPERTIES OUTPUT_NAME "${KONAN_LIB_NAME}")
target_include_directories(pdfium-jni PRIVATE "${KONAN_JNI_INCLUDE_DIR}" "${PDFium_DIR}/include")

# Linking through CMake rather than konan's linker is the point: pdfium is C++, and its runtime
# comes along correctly this way.
target_link_libraries(pdfium-jni PRIVATE pdfium)
