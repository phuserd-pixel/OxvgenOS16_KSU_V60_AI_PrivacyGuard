cmake_minimum_required(VERSION 3.22)
project(OxGuard)
add_library(oxguard SHARED src/main.cpp)
add_executable(oxguard_daemon src/daemon.cpp)
