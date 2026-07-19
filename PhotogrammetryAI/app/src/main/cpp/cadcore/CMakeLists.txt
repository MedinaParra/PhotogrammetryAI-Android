cmake_minimum_required(VERSION 3.22.1)
project(photogrammetry_cadcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(PGAI_REQUIRE_OCCT "Fail configuration when OpenCASCADE is unavailable" OFF)
set(
    PGAI_OCCT_ROOT
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../..//third-party/opencascade/${ANDROID_ABI}"
    CACHE PATH
    "OpenCASCADE installation root for the current Android ABI"
)

add_library(
    photogrammetry_cadcore
    SHARED
    photogrammetry_cadcore_jni.cpp
    OcctStepFeatureExtractor.cpp
)

target_include_directories(
    photogrammetry_cadcore
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}"
)

target_compile_options(
    photogrammetry_cadcore
    PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fexceptions
    -frtti
)

find_library(ANDROID_LOG_LIBRARY log)
if(ANDROID_LOG_LIBRARY)
    target_link_libraries(photogrammetry_cadcore PRIVATE ${ANDROID_LOG_LIBRARY})
endif()

set(OCCT_INCLUDE_DIR "${PGAI_OCCT_ROOT}/include/opencascade")
if(EXISTS "${OCCT_INCLUDE_DIR}/Standard.hxx")
    message(STATUS "Photogrammetry cadcore: OpenCASCADE found at ${PGAI_OCCT_ROOT}")
    target_compile_definitions(photogrammetry_cadcore PRIVATE PGAI_USE_OCCT=1)
    target_include_directories(photogrammetry_cadcore PRIVATE "${OCCT_INCLUDE_DIR}")

    set(PGAI_OCCT_LIBRARIES
        TKernel
        TKMath
        TKG2d
        TKG3d
        TKGeomBase
        TKGeomAlgo
        TKBRep
        TKTopAlgo
        TKPrim
        TKBO
        TKMesh
        TKXSBase
        TKSTEPBase
        TKSTEPAttr
        TKSTEP209
        TKSTEP
    )

    foreach(OCCT_LIBRARY_NAME IN LISTS PGAI_OCCT_LIBRARIES)
        set(OCCT_LIBRARY_PATH "${PGAI_OCCT_ROOT}/lib/lib${OCCT_LIBRARY_NAME}.so")
        if(NOT EXISTS "${OCCT_LIBRARY_PATH}")
            message(FATAL_ERROR "Missing OpenCASCADE library: ${OCCT_LIBRARY_PATH}")
        endif()
        add_library(${OCCT_LIBRARY_NAME} SHARED IMPORTED)
        set_target_properties(
            ${OCCT_LIBRARY_NAME}
            PROPERTIES IMPORTED_LOCATION "${OCCT_LIBRARY_PATH}"
        )
        target_link_libraries(photogrammetry_cadcore PRIVATE ${OCCT_LIBRARY_NAME})
    endforeach()
else()
    if(PGAI_REQUIRE_OCCT)
        message(FATAL_ERROR
            "OpenCASCADE not found at ${PGAI_OCCT_ROOT}. Build/copy the ARM64 OCCT package first."
        )
    endif()
    message(WARNING
        "Photogrammetry cadcore will compile without OCCT and report OCCT_MISSING at runtime."
    )
endif()
