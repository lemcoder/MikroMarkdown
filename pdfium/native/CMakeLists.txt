cmake_minimum_required(VERSION 3.20)
project(pdfium_jni C)

# Supplied by the Konan plugin: where it wrote the JNI stub, and what the bindings will load.
set(KONAN_JNI_STUB_DIR "" CACHE PATH "")
set(KONAN_JNI_LIB_NAME "" CACHE STRING "")
set(KONAN_JNI_INCLUDE_DIRS "" CACHE STRING "")

# pdfium ships PDFiumConfig.cmake; PDFium_DIR points at the unpacked release.
find_package(PDFium REQUIRED)

file(GLOB JNI_SOURCES "${KONAN_JNI_STUB_DIR}/*.c")
add_library(pdfium-jni SHARED ${JNI_SOURCES})
set_target_properties(pdfium-jni PROPERTIES OUTPUT_NAME "${KONAN_JNI_LIB_NAME}")
target_include_directories(pdfium-jni PRIVATE ${KONAN_JNI_INCLUDE_DIRS})

# Linking through CMake rather than konan's linker is the point: pdfium is C++, and its runtime
# comes along correctly this way.
target_link_libraries(pdfium-jni PRIVATE pdfium)
