#!/bin/bash
set -e
./build_native.sh
./build_zygisk.sh
./copy_output.sh
./release.sh
