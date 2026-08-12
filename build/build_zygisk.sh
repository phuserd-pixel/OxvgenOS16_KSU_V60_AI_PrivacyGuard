#!/bin/bash
cmake -S ../zygisk -B zygisk_build -G Ninja -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36
cmake --build zygisk_build
