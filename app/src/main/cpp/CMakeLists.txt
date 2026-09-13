cmake_minimum_required(VERSION 3.22)

project(nativehook)

set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -ffunction-sections -fdata-sections")

add_library(
        nativehook
        SHARED
        native_hook.cpp
)

target_include_directories(
        nativehook
        PRIVATE
        ${CMAKE_SOURCE_DIR}/../libs/include
)

add_library(frida-gumjs STATIC IMPORTED)

set_target_properties(
        frida-gumjs
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../libs/${ANDROID_ABI}/libfrida-gumjs.a
)

target_link_libraries(
        nativehook
        frida-gumjs
        log
        dl
        m
)

target_link_options(
        nativehook
        PRIVATE
        -Wl,--gc-sections
        -Wl,--as-needed
        -Wl,--strip-all
)