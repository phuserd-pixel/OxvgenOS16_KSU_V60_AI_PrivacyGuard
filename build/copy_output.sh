#!/bin/bash
mkdir -p ../module/bin ../module/lib64 ../module/zygisk
cp native_build/oxguard ../module/bin/
cp native_build/liboxguard.so ../module/lib64/
cp zygisk_build/liboxhook.so ../module/zygisk/
chmod 755 ../module/bin/oxguard
