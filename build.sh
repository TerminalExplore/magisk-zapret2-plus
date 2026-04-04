#!/bin/bash
##########################################################################################
# Zapret2 Magisk Module - Local Build Script
##########################################################################################

set -e

VERSION="${1:-}"
OUTPUT_DIR="${2:-dist}"

echo "Checking for latest Xray version..."
XRAY_VERSION=$(curl -sL "https://api.github.com/repos/XTLS/Xray-core/releases/latest" 2>/dev/null | grep -o '"tag_name": "[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$XRAY_VERSION" ]; then
    echo "WARNING: Could not get latest Xray version, using default"
    XRAY_VERSION="v26.3.27"
else
    echo "Latest Xray version: $XRAY_VERSION"
fi

if [ ! -f "version.series" ]; then
    echo "ERROR: version.series file is missing"
    exit 1
fi

VERSION_SERIES=$(tr -d '[:space:]' < version.series)
if [[ ! "$VERSION_SERIES" =~ ^[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: invalid version.series value '$VERSION_SERIES' (expected MAJOR.MINOR)"
    exit 1
fi

if [ -n "$VERSION" ]; then
    VERSION="${VERSION#v}"
    VERSION_CODE=$(printf '%s' "$VERSION" | tr -cd '0-9' | head -c 9)
    if [ -z "$VERSION_CODE" ]; then
        VERSION_CODE=1
    fi
else
    COMMIT_COUNT=$(git rev-list --count HEAD 2>/dev/null || true)
    if [ -z "$COMMIT_COUNT" ]; then
        COMMIT_COUNT=1
    fi
    VERSION_CODE=$((100000 + COMMIT_COUNT))
    VERSION="${VERSION_SERIES}.${VERSION_CODE}"
fi

echo "Building Zapret2 Magisk Module v$VERSION"
echo "=========================================="

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Download Xray binaries automatically if missing
download_xray() {
    local arch="$1"
    echo "Downloading Xray for $arch..."
    
    # Map module arch to Xray arch
    local xray_arch=""
    case "$arch" in
        arm64-v8a) xray_arch="arm64-v8a" ;;
        armeabi-v7a) 
            # No armv7 build available, use arm64 if exists
            if [ -f "zapret2/bin/arm64-v8a/xray" ]; then
                cp "zapret2/bin/arm64-v8a/xray" "zapret2/bin/armeabi-v7a/xray" 2>/dev/null || true
            fi
            echo "  Note: armeabi-v7a not available, arm64 binary may work"
            return 0
            ;;
        x86_64) xray_arch="amd64" ;;
        x86) xray_arch="386" ;;
        *) echo "Unsupported arch: $arch"; return 1 ;;
    esac
    
    local url="https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/Xray-android-${xray_arch}.zip"
    echo "  URL: $url"
    local tmpdir=$(mktemp -d)
    
    mkdir -p "zapret2/bin/$arch"
    
    if command -v curl >/dev/null 2>&1; then
        curl -sL "$url" -o "$tmpdir/xray.zip" || return 1
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$url" -O "$tmpdir/xray.zip" || return 1
    else
        echo "ERROR: curl or wget required"
        return 1
    fi
    
    if [ -f "$tmpdir/xray.zip" ]; then
        # Try unzip, busybox, or python
        if command -v unzip >/dev/null 2>&1; then
            unzip -o "$tmpdir/xray.zip" -d "$tmpdir" >/dev/null 2>&1
        elif command -v busybox >/dev/null 2>&1; then
            busybox unzip -o "$tmpdir/xray.zip" -d "$tmpdir" >/dev/null 2>&1
        elif command -v python3 >/dev/null 2>&1; then
            python3 -c "import zipfile; zipfile.ZipFile('$tmpdir/xray.zip').extractall('$tmpdir')"
        elif command -v python >/dev/null 2>&1; then
            python -c "import zipfile; zipfile.ZipFile('$tmpdir/xray.zip').extractall('$tmpdir')"
        else
            echo "ERROR: unzip, busybox, or python required to extract archive"
            rm -rf "$tmpdir"
            return 1
        fi
        
        if [ -f "$tmpdir/xray" ]; then
            cp "$tmpdir/xray" "zapret2/bin/$arch/xray"
            chmod +x "zapret2/bin/$arch/xray"
            echo "  ✓ Xray downloaded for $arch"
            rm -rf "$tmpdir"
            return 0
        fi
    fi
    
    rm -rf "$tmpdir"
    return 1
}

# Check and download binaries
MISSING_XRAY=0
for arch in arm64-v8a armeabi-v7a; do
    if [ ! -f "zapret2/bin/$arch/xray" ]; then
        if download_xray "$arch"; then
            echo "  ✓ Downloaded Xray for $arch"
        else
            echo "  ✗ Failed to download Xray for $arch"
            MISSING_XRAY=1
        fi
    else
        echo "  ✓ Xray already exists for $arch"
    fi
done

# Check for nfqws2
MISSING_NFQWS=0
for arch in arm64-v8a armeabi-v7a; do
    if [ ! -f "zapret2/bin/$arch/nfqws2" ]; then
        echo "WARNING: Missing nfqws2 for $arch"
        MISSING_NFQWS=1
    fi
done

if [ $MISSING_NFQWS -eq 1 ]; then
    echo ""
    echo "nfqws2 binaries are missing."
    echo "Download from: https://github.com/bol-van/zapret/releases"
    echo "Or build from source (see docs/BUILD.md)"
    echo ""
    read -p "Continue without nfqws2? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Copy architecture-specific binaries to main bin directory
echo "Preparing binaries..."
for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    if [ -f "zapret2/bin/$arch/nfqws2" ]; then
        cp "zapret2/bin/$arch/nfqws2" "zapret2/nfqws2-$arch"
    fi
    if [ -f "zapret2/bin/$arch/xray" ]; then
        cp "zapret2/bin/$arch/xray" "zapret2/xray-$arch"
    fi
done

# Update version in module.prop
sed -i "s/^version=.*/version=v$VERSION/" module.prop
sed -i "s/^versionCode=.*/versionCode=$VERSION_CODE/" module.prop

# Make scripts executable
chmod +x customize.sh service.sh uninstall.sh action.sh 2>/dev/null || true
chmod +x zapret2/scripts/*.sh 2>/dev/null || true

# Create ZIP
ZIP_NAME="zapret2-magisk-v$VERSION.zip"

echo "Creating $ZIP_NAME..."

zip -r "$OUTPUT_DIR/$ZIP_NAME" \
    META-INF \
    module.prop \
    customize.sh \
    service.sh \
    uninstall.sh \
    action.sh \
    zapret2 \
    README.md \
    -x "*.git*" \
    -x "CLAUDE.md" \
    -x ".github/*" \
    -x "build.sh" \
    -x "docs/*"

echo ""
echo "Build complete: $OUTPUT_DIR/$ZIP_NAME"
echo ""

# Show file size
ls -lh "$OUTPUT_DIR/$ZIP_NAME"

# Verify ZIP structure
echo ""
echo "ZIP contents:"
unzip -l "$OUTPUT_DIR/$ZIP_NAME" | head -30
