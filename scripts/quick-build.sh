#!/bin/bash
###############################################################################
# Quick Build Script for Zapret2 Plus
# Builds only the Magisk module ZIP (no APK, faster)
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
VERSION="2.0.0-test"

echo "Zapret2 Plus - Quick Module Build"
echo "=================================="
echo ""

# Clean and create build dir
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Download Xray if needed
XRAY_DIR="$PROJECT_DIR/binaries"
mkdir -p "$XRAY_DIR/arm64-v8a"
mkdir -p "$XRAY_DIR/armeabi-v7a"

if [ ! -f "$XRAY_DIR/arm64-v8a/xray" ]; then
    echo "Downloading Xray..."
    wget -q "https://github.com/XTLS/Xray-core/releases/download/v26.3.23/Xray-android-arm64-v8a.zip" \
        -O /tmp/xray.zip
    unzip -o /tmp/xray.zip -d /tmp/xray
    cp /tmp/xray/xray "$XRAY_DIR/arm64-v8a/"
    chmod +x "$XRAY_DIR/arm64-v8a/xray"
    cp "$XRAY_DIR/arm64-v8a/xray" "$XRAY_DIR/armeabi-v7a/"
fi

# Copy to module
mkdir -p "$PROJECT_DIR/zapret2/bin/arm64-v8a"
mkdir -p "$PROJECT_DIR/zapret2/bin/armeabi-v7a"
cp "$XRAY_DIR/arm64-v8a/xray" "$PROJECT_DIR/zapret2/bin/arm64-v8a/"
cp "$XRAY_DIR/arm64-v8a/xray" "$PROJECT_DIR/zapret2/bin/armeabi-v7a/"

# Build ZIP
echo "Building module ZIP..."
cd "$PROJECT_DIR"
ZIP_NAME="zapret2-module-v${VERSION}.zip"
ZIP_PATH="$BUILD_DIR/$ZIP_NAME"

if command -v zip &> /dev/null; then
    # Use find to create file list, then zip
    find . -type f \
        ! -path "./.git/*" \
        ! -path "./build/*" \
        ! -path "./binaries/*" \
        ! -path "./tests/*" \
        ! -path "./android-app/build/*" \
        ! -path "./android-app/.gradle/*" \
        ! -path "./.gradle/*" \
        ! -path "./scripts/*" \
        ! -path "./.github/*" \
        ! -name "*.apk" \
        ! -name "CLAUDE.md" \
        ! -name "AGENTS.md" \
        ! -name ".env" \
        ! -name "*.zip" \
        ! -name ".DS_Store" \
        ! -name "Thumbs.db" \
        | zip -@ "$ZIP_PATH"
    echo "OK: $ZIP_PATH"
else
    python3 << PYEOF
import zipfile
import os

exclude_dirs = ['.git', '.gradle', 'build', 'binaries', 'tests', '.github', 'scripts', 'android-app']
exclude_files = ['CLAUDE.md', 'AGENTS.md', '.env']
zip_path = '$ZIP_PATH'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk('.'):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for f in files:
            if f in exclude_files or f.endswith('.apk') or f.endswith('.zip'):
                continue
            path = os.path.join(root, f)
            zf.write(path)
print(f"OK: {zip_path}")
PYEOF
fi

echo ""
echo "Done: $ZIP_PATH"
echo ""
echo "Install: Flash ZIP in Magisk app"
