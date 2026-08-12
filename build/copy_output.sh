#!/bin/bash
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)

echo "=============================="
echo " OxGuard Copy Output"
echo "=============================="

BUILD_DIR="$ROOT/build/native_build"
MODULE_DIR="$ROOT/module/bin"

mkdir -p "$MODULE_DIR"

echo "[*] Checking build output..."

if [ ! -d "$BUILD_DIR" ]; then
    echo "[ERROR] build directory not found:"
    echo "$BUILD_DIR"
    exit 1
fi


echo "[*] Available files:"
ls -lh "$BUILD_DIR"


echo "[*] Copy oxguard daemon..."

if [ -f "$BUILD_DIR/oxguard_daemon" ]; then

    cp "$BUILD_DIR/oxguard_daemon" \
    "$MODULE_DIR/oxguard_daemon"

    chmod 755 "$MODULE_DIR/oxguard_daemon"

    echo "[OK] oxguard_daemon copied"

else

    echo "[ERROR] oxguard_daemon missing"
    exit 1

fi



echo "[*] Copy zygisk library..."

if [ -f "$BUILD_DIR/liboxguard.so" ]; then

    cp "$BUILD_DIR/liboxguard.so" \
    "$MODULE_DIR/liboxguard.so"

    chmod 644 "$MODULE_DIR/liboxguard.so"

    echo "[OK] liboxguard.so copied"

else

    echo "[ERROR] liboxguard.so missing"
    exit 1

fi



echo "=============================="
echo " Final module/bin:"
echo "=============================="

ls -lh "$MODULE_DIR"

echo "[SUCCESS] Copy finished"
