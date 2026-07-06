cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

# Locate glslangValidator for SPIR-V compilation
find_program(GLSLANG_VALIDATOR glslangValidator
    HINTS ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/winlator
    NO_DEFAULT_PATH)
if(NOT GLSLANG_VALIDATOR)
    find_program(GLSLANG_VALIDATOR glslangValidator)
endif()
if(NOT GLSLANG_VALIDATOR)
    message(FATAL_ERROR "glslangValidator not found. Install it or place glslangValidator.exe in app/src/main/cpp/")
endif()
message(STATUS "Using glslangValidator: ${GLSLANG_VALIDATOR}")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(fpslimiter)
add_subdirectory(adrenotools)
add_subdirectory(asurfacerenderer)

# lsfg-vk-android: build LSFG Vulkan layer from source
set(LSFGVK_ANDROID_WINE ON CACHE BOOL "Build for Wine-on-Android" FORCE)
add_subdirectory(lsfg-vk-android)

# Add Winlator shared library
add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/patchelf_wrapper.cpp
        winlator/vulkan.cpp
)

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        c++_shared
        adrenotools
        dl
)

# Vulkan renderer shader compilation
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
    COMMAND ${GLSLANG_VALIDATOR} -V --vn window_vert_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.vert
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.vert
    COMMENT "Compiling window.vert -> window_vert.h"
)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
    COMMAND ${GLSLANG_VALIDATOR} -V --vn window_frag_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.frag
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/window.frag
    COMMENT "Compiling window.frag -> window_frag.h"
)

include_directories(${CMAKE_CURRENT_BINARY_DIR})

# Effect shader compilation
set(EFFECT_SHADERS
    effect_color effect_fxaa effect_crt effect_toon
    effect_vignette effect_sepia effect_blur effect_pixelate
    effect_grayscale effect_sharpen effect_smooth effect_hdr
    effect_ntsc effect_fsr1_easu effect_fsr1_rcas
)

# Effect vertex shader
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/effect_vert.h
    COMMAND ${GLSLANG_VALIDATOR} -V --vn effect_vert_code
            -x -o ${CMAKE_CURRENT_BINARY_DIR}/effect_vert.h
            ${CMAKE_CURRENT_SOURCE_DIR}/winlator/effect.vert
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/effect.vert
    COMMENT "Compiling effect.vert -> effect_vert.h"
)

foreach(EFFECT ${EFFECT_SHADERS})
    add_custom_command(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${EFFECT}_frag.h
        COMMAND ${GLSLANG_VALIDATOR} -V --vn ${EFFECT}_frag_code
                -x -o ${CMAKE_CURRENT_BINARY_DIR}/${EFFECT}_frag.h
                ${CMAKE_CURRENT_SOURCE_DIR}/winlator/${EFFECT}.frag
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/winlator/${EFFECT}.frag
        COMMENT "Compiling ${EFFECT}.frag -> ${EFFECT}_frag.h"
    )
    list(APPEND EFFECT_HEADERS ${CMAKE_CURRENT_BINARY_DIR}/${EFFECT}_frag.h)
endforeach()

# Vulkan renderer shared library
add_library(vulkan_renderer SHARED
        winlator/vulkan_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp

        ${CMAKE_CURRENT_BINARY_DIR}/window_vert.h
        ${CMAKE_CURRENT_BINARY_DIR}/window_frag.h
        ${CMAKE_CURRENT_BINARY_DIR}/effect_vert.h
        ${EFFECT_HEADERS}
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        vulkan
        adrenotools
        dl
        atomic
)