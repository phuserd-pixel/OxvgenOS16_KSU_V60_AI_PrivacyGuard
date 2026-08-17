cmake_minimum_required(VERSION 3.22)
project(AdBlockGlobal)

set(CMAKE_C_STANDARD 11)

add_subdirectory(dnsfilter)
add_subdirectory(netwatch)
add_subdirectory(ruleengine)
