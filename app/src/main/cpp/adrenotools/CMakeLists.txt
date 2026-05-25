cmake_minimum_required(VERSION 3.22.1)
project(adrenotools)

# Stub library for adrenotools - provides empty implementation
# The real library should be cloned from https://github.com/Pipetto-crypto/libadrenotools.git

add_library(adrenotools STATIC
        adrenotools_stub.c
)

target_include_directories(adrenotools PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)
