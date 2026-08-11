cmake_minimum_required(VERSION 3.22.1)
project(stockfish_jni LANGUAGES CXX)

get_filename_component(STOCKFISH_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../iOS/ThirdParty/Stockfish/src"
    ABSOLUTE)

file(GLOB_RECURSE STOCKFISH_SOURCES CONFIGURE_DEPENDS "${STOCKFISH_ROOT}/*.cpp")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX "/main\\.cpp$")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX "/universal/")

add_library(stockfish_jni SHARED stockfish_jni.cpp ${STOCKFISH_SOURCES})
target_include_directories(stockfish_jni PRIVATE "${STOCKFISH_ROOT}")
target_compile_features(stockfish_jni PRIVATE cxx_std_20)
target_compile_definitions(stockfish_jni PRIVATE
    NDEBUG
    IS_64BIT
    USE_POPCNT
    USE_NEON=8)
target_compile_options(stockfish_jni PRIVATE
    -O3
    -Wno-deprecated-enum-enum-conversion
    -Wno-unused-parameter
    -Wno-unused-variable)

find_library(LOG_LIBRARY log)
find_library(ANDROID_LIBRARY android)
target_link_libraries(stockfish_jni PRIVATE ${LOG_LIBRARY} ${ANDROID_LIBRARY})
