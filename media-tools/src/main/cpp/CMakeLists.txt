cmake_minimum_required(VERSION 3.22.1)
project(mp3encoder)

# --- libmp3lame (LGPL) ------------------------------------------------------
# NOT VENDORED HERE. This blueprint scaffolds the build wiring only; the actual
# libmp3lame 3.100 sources need to be fetched and dropped into ./lame before this
# will configure successfully. Two ways to get them:
#   1. Download tarball from https://sourceforge.net/projects/lame/files/lame/3.100/
#      and extract libmp3lame/ from it into media-tools/src/main/cpp/lame/
#   2. Use a maintained Android-prebuilt package (e.g. via vcpkg / a known-good
#      prebuilt AAR) instead of building from source, if one exists.
# LGPL requires: dynamic linking (not static) OR shipping relink-capable object
# files, plus a copy of the LGPL license and attribution in-app. Using
# ANDROID_STL=c++_shared (set in build.gradle.kts) keeps our side dynamic;
# confirm libmp3lame itself is also built as a shared object, not statically
# linked into this .so, before shipping.
add_subdirectory(lame)

add_library(mp3encoder SHARED
        mp3_encoder_jni.cpp
)

find_library(log-lib log)

target_include_directories(mp3encoder PRIVATE lame/include)

target_link_libraries(mp3encoder
        mp3lame
        ${log-lib}
)
