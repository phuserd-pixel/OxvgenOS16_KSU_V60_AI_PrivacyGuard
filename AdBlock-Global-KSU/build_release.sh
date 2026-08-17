#!/bin/bash
set -e

NAME=AdBlock-Global-KSU-v1.0-release

rm -rf build release
mkdir build release

cmake -B build -S native \
-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
-DANDROID_ABI=arm64-v8a \
-DANDROID_PLATFORM=android-35

cmake --build build

mkdir -p release/bin
cp module.prop service.sh customize.sh uninstall.sh release/
cp build/dnsfilter/dnsfilter release/bin/
cp build/netwatch/netwatch release/bin/
cp build/ruleengine/ruleengine release/bin/
cp -r config webroot release/
mkdir -p release/logs
chmod 755 release/bin/*
cd release
zip -r ../${NAME}.zip .
