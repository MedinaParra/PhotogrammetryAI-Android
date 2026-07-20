cmake_minimum_required(VERSION 3.22.1)
project(freecad_android_bridge LANGUAGES CXX)
add_library(freecad_android_bridge SHARED freecad_android_bridge.cpp)
target_compile_features(freecad_android_bridge PRIVATE cxx_std_17)
find_library(log-lib log)
target_link_libraries(freecad_android_bridge PRIVATE ${log-lib})

# Production STEP import is enabled only after linking the Android FreeCAD/OpenCascade runtime.
