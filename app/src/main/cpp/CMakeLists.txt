cmake_minimum_required(VERSION 3.22.1)

project("myapplication")

add_library( # Sets the name of the library.
        myapplication
        SHARED
        opencv-utils.cpp
        native-lib.cpp)

#opencv
set(OPENCV_LIBS_DIR ${CMAKE_SOURCE_DIR}/../opencv_include)
set(OPENCV_LIBS ${CMAKE_SOURCE_DIR}/../opencv_lib)


add_library(libopencv_java4
        SHARED
        IMPORTED )
set_target_properties(libopencv_java4
        PROPERTIES IMPORTED_LOCATION
        ${OPENCV_LIBS}/${ANDROID_ABI}/libopencv_java4.so )

#指定引入头文件目录
include_directories(${OPENCV_LIBS_DIR})

find_library( # Sets the name of the path variable.
        log-lib
        jnigraphics-lib jnigraphics
        log)


target_link_libraries( # Specifies the target library.
        myapplication
        libopencv_java4
        ${jnigraphics-lib}
        jnigraphics #JNI层,添加bitmap支持
        ${log-lib})