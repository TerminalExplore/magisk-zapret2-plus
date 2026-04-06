#!/bin/bash
###############################################################################
# Local Build Script for Zapret2 Plus
# Builds Magisk module ZIP and Android APK locally
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
VERSION="2.0.0-local"

echo "=========================================="
echo "Zapret2 Plus - Local Build"
echo "=========================================="
echo ""

# Clean previous builds
echo "[1/6] Cleaning previous builds..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Download Xray binaries if not present
echo "[2/6] Downloading Xray binaries..."
mkdir -p "$PROJECT_DIR/binaries/arm64-v8a"
mkdir -p "$PROJECT_DIR/binaries/armeabi-v7a"

XRAY_VERSION="v26.3.23"
if [ ! -f "$PROJECT_DIR/binaries/arm64-v8a/xray" ]; then
    echo "  Downloading Xray for arm64-v8a..."
    wget -q "https://github.com/XTLS/Xray-core/releases/download/$XRAY_VERSION/Xray-android-arm64-v8a.zip" \
        -O /tmp/xray-arm64.zip
    unzip -o /tmp/xray-arm64.zip -d /tmp/xray-arm64
    cp /tmp/xray-arm64/xray "$PROJECT_DIR/binaries/arm64-v8a/"
    chmod +x "$PROJECT_DIR/binaries/arm64-v8a/xray"
    echo "  ✓ arm64-v8a Xray downloaded"
else
    echo "  ✓ arm64-v8a Xray already present"
fi

# Copy arm64 to armv7 (no separate build available)
echo "  Copying arm64 to armeabi-v7a..."
cp "$PROJECT_DIR/binaries/arm64-v8a/xray" "$PROJECT_DIR/binaries/armeabi-v7a/"

# Copy binaries to module
echo "[3/6] Copying binaries to module..."
mkdir -p "$PROJECT_DIR/zapret2/bin/arm64-v8a"
mkdir -p "$PROJECT_DIR/zapret2/bin/armeabi-v7a"
cp "$PROJECT_DIR/binaries/arm64-v8a/xray" "$PROJECT_DIR/zapret2/bin/arm64-v8a/"
cp "$PROJECT_DIR/binaries/armeabi-v7a/xray" "$PROJECT_DIR/zapret2/bin/armeabi-v7a/"

# Build Magisk module ZIP
echo "[4/6] Building Magisk module ZIP..."
cd "$PROJECT_DIR"

ZIP_NAME="zapret2-magisk-v${VERSION}-${TIMESTAMP}.zip"

if command -v zip &> /dev/null; then
    cd "$PROJECT_DIR"
    zip -r "$BUILD_DIR/$ZIP_NAME" \
        META-INF \
        module.prop \
        customize.sh \
        service.sh \
        uninstall.sh \
        action.sh \
        zapret2 \
        README.md \
        common \
        docs \
        -x "*.git*" \
        -x "*.gradle*" \
        -x "android-app/*" \
        -x "build/*" \
        -x "binaries/*" \
        -x "tests/*" \
        -x "scripts/*" \
        -x ".github/*" \
        -x "*.apk" \
        -x "CLAUDE.md" \
        -x "AGENTS.md" \
        -x ".env" \
        -x "zapret2/vpn-config.json" \
        -x "zapret2/vpn-subs-raw.txt" \
        -x "zapret2/vpn-subs-haproxy.txt" \
        -x "zapret2/*.pid" \
        -x "zapret2/iptables-status"
    echo "  ✓ Module ZIP: $BUILD_DIR/$ZIP_NAME (using zip)"
else
    python3 << PYEOF
import zipfile
import os

exclude_dirs = ['.git', '.gradle', 'build', 'binaries', 'tests', '.github', 'scripts', 'android-app']
exclude_files = ['CLAUDE.md', 'AGENTS.md', '.env']
zip_path = '$BUILD_DIR/$ZIP_NAME'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk('.'):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for f in files:
            if f in exclude_files or f.endswith('.apk') or f.endswith('.zip'):
                continue
            path = os.path.join(root, f)
            zf.write(path)
print(f"  ✓ Module ZIP: {zip_path}")
PYEOF
fi

# Build Android APK
echo "[5/6] Building Android APK..."
cd "$PROJECT_DIR/android-app"

if ! command -v java &> /dev/null; then
    echo "  ⚠ Java not found. Install with: sudo apt install openjdk-17-jdk"
    echo "  ⚠ Skipping APK build..."
else
    if [ -z "$JAVA_HOME" ]; then
        JAVA_BIN=$(which java)
        export JAVA_HOME=$(dirname $(dirname $(readlink -f $JAVA_BIN)))
    fi
    
    if ./gradlew assembleRelease --no-daemon -q 2>&1; then
        APK_PATH=$(find "$PROJECT_DIR/android-app" -name "*-release-*.apk" -type f 2>/dev/null | head -1)
        if [ -n "$APK_PATH" ]; then
            APK_NAME="zapret2-control-v${VERSION}-${TIMESTAMP}.apk"
            cp "$APK_PATH" "$BUILD_DIR/$APK_NAME"
            echo "  ✓ APK: $BUILD_DIR/$APK_NAME"
        fi
    elif ./gradlew assembleDebug --no-daemon -q 2>&1; then
        APK_PATH=$(find "$PROJECT_DIR/android-app" -name "*-debug-*.apk" -type f 2>/dev/null | head -1)
        if [ -n "$APK_PATH" ]; then
            APK_NAME="zapret2-control-v${VERSION}-${TIMESTAMP}.apk"
            cp "$APK_PATH" "$BUILD_DIR/$APK_NAME"
            echo "  ✓ APK: $BUILD_DIR/$APK_NAME"
        fi
    else
        echo "  ⚠ Gradle build failed. Check Java/Android SDK installation."
    fi
fi

# Create update JSON
echo "[6/6] Creating update manifest..."
cat > "$BUILD_DIR/update.json" << EOF
{
  "version": "${VERSION}",
  "versionCode": 200000,
  "zipUrl": "file://${BUILD_DIR}/${ZIP_NAME}",
  "changelog": "Local test build - $TIMESTAMP"
}
EOF
echo "  ✓ Update manifest: $BUILD_DIR/update.json"

echo ""
echo "=========================================="
echo "Build complete!"
echo "=========================================="
echo ""
echo "Output files:"
echo "  📦 Module: $BUILD_DIR/$ZIP_NAME"
[ -f "$BUILD_DIR/$APK_NAME" ] && echo "  📱 APK: $BUILD_DIR/$APK_NAME"
echo "  📋 Update: $BUILD_DIR/update.json"
echo ""
echo "To install:"
echo "  Module: Flash ZIP in Magisk app"
echo "  APK: adb install $BUILD_DIR/*.apk"
