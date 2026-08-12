#!/bin/bash
set -e

echo "[*] Preparing output directory..."

MODULE_DIR="../module"
BIN_DIR="$MODULE_DIR/bin"

mkdir -p "$BIN_DIR"

echo "[*] Searching native binary..."

if [ -f "../native_build/oxguard_daemon" ]; then
    echo "[+] Found oxguard_daemon"
    cp "../native_build/oxguard_daemon" "$BIN_DIR/oxguard"

elif [ -f "../native_build/oxguard" ]; then
    echo "[+] Found oxguard"
    cp "../native_build/oxguard" "$BIN_DIR/oxguard"

else
    echo "[!] ERROR: oxguard binary not found"
    echo "Available files:"
    find .. -type f -name "*oxguard*" || true
    exit 1
fi


echo "[*] Searching zygisk library..."

mkdir -p "$MODULE_DIR/zygisk"

if [ -f "../zygisk_build/liboxhook.so" ]; then
    cp "../zygisk_build/liboxhook.so" "$MODULE_DIR/zygisk/"
else
    echo "[!] Warning: liboxhook.so not found"
fi


chmod 755 "$BIN_DIR/oxguard" || true

echo "[+] Copy output finished"

ls -R "$MODULE_DIR"
