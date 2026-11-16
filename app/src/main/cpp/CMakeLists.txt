cmake_minimum_required(VERSION 3.22.1)
project(svoboda_dpi LANGUAGES CXX)

find_library(log-lib log)

add_library(svoboda-dpi SHARED hev_dpi_jni.cpp)

target_include_directories(svoboda-dpi PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/hev-socks5-tunnel/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/hev-socks5-tunnel/src
)

# Prebuilt HEV library from jniLibs for the matching ABI.
add_library(hev-socks5-tunnel SHARED IMPORTED)
set_target_properties(hev-socks5-tunnel PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../../app/src/main/jniLibs/${ANDROID_ABI}/libhev-socks5-tunnel.so
)

target_link_libraries(svoboda-dpi PRIVATE hev-socks5-tunnel ${log-lib})
set_target_properties(svoboda-dpi PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
