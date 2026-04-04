#!/bin/bash
##########################################################################################
# Download Xray binary for VPN functionality
##########################################################################################

set -e

XRAY_VERSION="${1:-v2.3.2}"
OUTPUT_DIR="${2:-zapret2/bin}"

echo "Downloading Xray $XRAY_VERSION..."
echo "================================"

mkdir -p "$OUTPUT_DIR/arm64-v8a"
mkdir -p "$OUTPUT_DIR/armeabi-v7a"
mkdir -p "$OUTPUT_DIR/x86_64"
mkdir -p "$OUTPUT_DIR/x86"

download_xray() {
    local arch="$1"
    local url="https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/Xray-android-${arch}-v8a.zip"
    local tmpdir=$(mktemp -d)
    
    echo "Downloading for $arch..."
    
    if command -v curl >/dev/null 2>&1; then
        curl -sL "$url" -o "$tmpdir/xray.zip"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$url" -O "$tmpdir/xray.zip"
    else
        echo "ERROR: curl or wget required"
        rm -rf "$tmpdir"
        return 1
    fi
    
    if [ -f "$tmpdir/xray.zip" ]; then
        unzip -o "$tmpdir/xray.zip" -d "$tmpdir"
        mv "$tmpdir/xray" "$OUTPUT_DIR/$arch/xray"
        chmod +x "$OUTPUT_DIR/$arch/xray"
        rm -rf "$tmpdir"
        echo "  ✓ $arch done"
        return 0
    else
        echo "  ✗ Failed to download for $arch"
        rm -rf "$tmpdir"
        return 1
    fi
}

download_xray "arm64-v8a" || echo "arm64-v8a download failed"
download_xray "armeabi-v7a" || echo "armeabi-v7a download failed"
download_xray "x86_64" || echo "x86_64 download failed"
download_xray "x86" || echo "x86 download failed"

echo ""
echo "Download complete!"
echo ""

for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    if [ -f "$OUTPUT_DIR/$arch/xray" ]; then
        echo "  ✓ $arch: $(ls -lh "$OUTPUT_DIR/$arch/xray" | awk '{print $5}')"
    else
        echo "  ✗ $arch: missing"
    fi
done
