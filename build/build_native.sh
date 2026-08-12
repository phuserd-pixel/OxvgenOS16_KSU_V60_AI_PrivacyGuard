#!/bin/bash
cmake -S ../native -B native_build -G Ninja -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36
cmake --build native_build
