cmake_minimum_required(VERSION 3.10.0)
project(mmkv)

add_subdirectory(Core Core)

add_library(mmkv SHARED native-bridge.cpp)

set_target_properties(mmkv PROPERTIES
        CXX_STANDARD 20
        CXX_EXTENSIONS OFF
        POSITION_INDEPENDENT_CODE ON
)

find_library(log-lib log)

target_link_libraries(mmkv ${log-lib} core android c++_shared)
