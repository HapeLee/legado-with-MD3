cmake_minimum_required(VERSION 3.22.1)
project(legado_small100 LANGUAGES C CXX)
include(FetchContent)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_Declare(sentencepiece
    URL https://codeload.github.com/google/sentencepiece/tar.gz/e0cce7d37b065b5140349dbe12c6bcf6192fdd78
    URL_HASH SHA256=0c369622d810d7d8fd98396bede6b7e92104ea3edb75680bda4b667c2f0b9565)
FetchContent_GetProperties(sentencepiece)
if(NOT sentencepiece_POPULATED)
    FetchContent_Populate(sentencepiece)
    # Upstream creates a symlink here. Windows builds must also work without Developer Mode.
    file(MAKE_DIRECTORY ${sentencepiece_SOURCE_DIR}/third_party/absl)
    add_subdirectory(${sentencepiece_SOURCE_DIR} ${sentencepiece_BINARY_DIR} EXCLUDE_FROM_ALL)
    file(COPY ${sentencepiece_SOURCE_DIR}/third_party/abseil-cpp/absl/
        DESTINATION ${sentencepiece_SOURCE_DIR}/third_party/absl)
endif()
add_library(legado_sentencepiece SHARED small100_sentencepiece.cpp)
target_include_directories(legado_sentencepiece PRIVATE ${sentencepiece_SOURCE_DIR}/src ${sentencepiece_SOURCE_DIR})
target_link_libraries(legado_sentencepiece PRIVATE sentencepiece-static)
target_link_options(legado_sentencepiece PRIVATE "-Wl,-z,max-page-size=16384")
