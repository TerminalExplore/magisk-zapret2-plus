#!/bin/bash
##########################################################################################
# Full Build Script - Builds both Magisk Module ZIP and Android APK
##########################################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$MODULE_DIR/dist"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step() { echo -e "${CYAN}==>${NC} $1"; }

usage() {
    echo "Zapret2 Full Build Script"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  all          Build everything (module + APK) [default]"
    echo "  module       Build only Magisk module ZIP"
    echo "  apk          Build only Android APK"
    echo "  xray         Download Xray binaries"
    echo "  clean        Clean all build artifacts"
    echo "  -h, --help   Show this help"
    echo ""
}

# Check for binaries
check_binaries() {
    info "Checking for binaries..."
    
    local missing=0
    
    for arch in arm64-v8a armeabi-v7a; do
        if [ ! -f "$MODULE_DIR/zapret2/bin/$arch/nfqws2" ]; then
            warn "Missing nfqws2 for $arch"
            missing=1
        fi
    done
    
    if [ $missing -eq 1 ]; then
        warn ""
        warn "Some binaries are missing."
        warn "Options:"
        warn "  1. Download nfqws2 from: https://github.com/bol-van/zapret/releases"
        warn "  2. Run: $0 xray (to download Xray)"
        warn ""
    fi
}

# Download Xray binaries
download_xray() {
    step "Downloading Xray binaries..."
    
    if [ -f "$MODULE_DIR/scripts/download-xray.sh" ]; then
        bash "$MODULE_DIR/scripts/download-xray.sh"
    else
        error "download-xray.sh not found"
        exit 1
    fi
}

# Build Magisk module
build_module() {
    step "Building Magisk module..."
    
    cd "$MODULE_DIR"
    
    if [ -f "$MODULE_DIR/build.sh" ]; then
        bash "$MODULE_DIR/build.sh"
    else
        error "build.sh not found"
        exit 1
    fi
    
    local zip=$(ls -t "$OUTPUT_DIR"/zapret2-magisk-*.zip 2>/dev/null | head -1)
    
    if [ -n "$zip" ] && [ -f "$zip" ]; then
        success "Module built: $zip"
        echo "Size: $(ls -lh "$zip" | awk '{print $5}')"
    else
        error "Module build failed"
        exit 1
    fi
}

# Build Android APK
build_apk() {
    step "Building Android APK..."
    
    if [ -f "$MODULE_DIR/scripts/build-apk.sh" ]; then
        bash "$MODULE_DIR/scripts/build-apk.sh debug
    else
        error "build-apk.sh not found"
        exit 1
    fi
}

# Clean all
clean_all() {
    step "Cleaning build artifacts..."
    
    rm -rf "$MODULE_DIR/android-app/app/build"
    rm -rf "$MODULE_DIR/dist"
    rm -f "$MODULE_DIR/zapret2"/*.apk
    rm -f "$MODULE_DIR/zapret2/nfqws2-"*
    rm -f "$MODULE_DIR/zapret2/xray"
    
    success "Clean completed"
}

# Main
case "${1:-all}" in
    all)
        info "Zapret2 Full Build"
        echo "========================================"
        echo ""
        
        check_binaries
        echo ""
        
        build_module
        echo ""
        
        build_apk
        echo ""
        
        success "Build complete!"
        echo ""
        echo "========================================"
        echo "Output files:"
        echo ""
        ls -lh "$OUTPUT_DIR"/*.zip "$OUTPUT_DIR"/*.apk 2>/dev/null || true
        echo ""
        ;;
    module)
        check_binaries
        build_module
        ;;
    apk)
        build_apk
        ;;
    xray)
        download_xray
        ;;
    clean)
        clean_all
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        error "Unknown option: $1"
        usage
        exit 1
        ;;
esac
