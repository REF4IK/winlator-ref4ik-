cmake_minimum_required(VERSION 3.10)
project(fpslimiter)

# Устанавливаем C++14 (для unordered_map и mutex)
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Флаги компиляции
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC")

# Создаём библиотеку libfpslimiter.so
add_library(fpslimiter SHARED
    fpslimiter.cpp
    fps_hook.cpp
)

# Линкуем системные библиотеки
target_link_libraries(fpslimiter
    log          # Android logging
    dl           # Dynamic linking (dlsym)
)

# Экспортируем все символы
set_target_properties(fpslimiter PROPERTIES
    LINK_FLAGS "-Wl,--export-dynamic"
)

# Копируем JSON manifest
add_custom_command(TARGET fpslimiter POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_SOURCE_DIR}/VkLayer_fps_limiter.json
        $<TARGET_FILE_DIR:fpslimiter>/VkLayer_fps_limiter.json
    COMMENT "Copying Vulkan layer manifest"
)
