add_library(lz4_static STATIC
    "${CMAKE_CURRENT_LIST_DIR}/../../lib/lz4.c"
)

target_include_directories(lz4_static
    PUBLIC
        "${CMAKE_CURRENT_LIST_DIR}/../../lib"
)

add_library(LZ4::LZ4 ALIAS lz4_static)

set_target_properties(lz4_static PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)
