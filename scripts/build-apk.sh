#!/bin/bash
##########################################################################################
# Build APK Script for Zapret2 Android App
##########################################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$SCRIPT_DIR/android-app"
OUTPUT_DIR="$SCRIPT_DIR/dist"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  debug       Build debug APK (default)"
    echo "  release     Build release APK (requires signing config)"
    echo "  clean       Clean build artifacts"
    echo "  all         Build both debug and release"
    echo "  install     Build and install to connected device"
    echo "  -h, --help  Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 debug        # Build debug APK"
    echo "  $0 release      # Build release APK"
    echo "  $0 install      # Build and install"
}

check_requirements() {
    info "Checking requirements..."
    
    # Check JDK
    if ! command -v java &> /dev/null; then
        error "Java not found. Please install JDK 17+"
        exit 1
    fi
    
    local java_version=$(java -version 2>&1 | head -1 | grep -oP '\d+')
    if [ "$java_version" -lt 17 ]; then
        warn "Java version is below 17, some features may not work"
    fi
    
    success "Java: $(java -version 2>&1 | head -1)"
    
    # Check Gradle
    if [ ! -f "$APP_DIR/gradlew" ]; then
        error "Gradle wrapper not found in $APP_DIR"
        exit 1
    fi
    
    chmod +x "$APP_DIR/gradlew"
    success "Gradle wrapper found"
    
    # Check Android SDK
    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        success "ANDROID_HOME: $ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
        success "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
    else
        warn "ANDROID_HOME not set. Attempting to detect..."
        
        local sdk_paths=(
            "$HOME/Android/Sdk"
            "$HOME/android-sdk"
            "/opt/android-sdk"
            "/usr/local/android-sdk"
        )
        
        for path in "${sdk_paths[@]}"; do
            if [ -d "$path" ]; then
                export ANDROID_HOME="$path"
                success "Found Android SDK at: $ANDROID_HOME"
                break
            fi
        done
        
        if [ -z "$ANDROID_HOME" ]; then
            warn "Android SDK not found. Build may fail."
            warn "Set ANDROID_HOME or install Android SDK."
        fi
    fi
    
    echo ""
}

setup_output_dir() {
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$APP_DIR/app/build/outputs/apk/debug"
    mkdir -p "$APP_DIR/app/build/outputs/apk/release"
}

build_debug() {
    info "Building debug APK..."
    
    cd "$APP_DIR"
    
    ./gradlew assembleDebug --no-daemon --stacktrace
    
    local apk="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
    
    if [ -f "$apk" ]; then
        cp "$apk" "$OUTPUT_DIR/zapret2-control-debug.apk"
        success "Debug APK built successfully!"
        echo ""
        echo "Output: $OUTPUT_DIR/zapret2-control-debug.apk"
        echo "Size: $(ls -lh "$apk" | awk '{print $5}')"
    else
        error "Debug APK not found after build"
        exit 1
    fi
}

build_release() {
    info "Building release APK..."
    
    # Check for keystore
    local keystore="$SCRIPT_DIR/keystore/zapret2.keystore"
    
    if [ ! -f "$keystore" ]; then
        warn "Release keystore not found at $keystore"
        warn "Creating debug keystore for testing..."
        
        mkdir -p "$SCRIPT_DIR/keystore"
        
        if command -v keytool &> /dev/null; then
            keytool -genkeypair \
                -keystore "$keystore" \
                -alias zapret2 \
                -keyalg RSA \
                -keysize 2048 \
                -validity 10000 \
                -storepass android \
                -keypass android \
                -dname "CN=Zapret2, OU=Dev, O=Zapret2, L=City, ST=State, C=US" \
                2>/dev/null || true
        fi
    fi
    
    cd "$APP_DIR"
    
    if [ -f "$keystore" ]; then
        export KEYSTORE_PATH="$keystore"
        export KEYSTORE_PASSWORD="android"
        export KEY_ALIAS="zapret2"
        export KEY_PASSWORD="android"
    fi
    
    ./gradlew assembleRelease --no-daemon --stacktrace || {
        error "Release build failed"
        warn "Falling back to debug build..."
        build_debug
        return
    }
    
    local apk="$APP_DIR/app/build/outputs/apk/release/app-release.apk"
    
    if [ -f "$apk" ]; then
        cp "$apk" "$OUTPUT_DIR/zapret2-control-release.apk"
        success "Release APK built successfully!"
        echo ""
        echo "Output: $OUTPUT_DIR/zapret2-control-release.apk"
        echo "Size: $(ls -lh "$apk" | awk '{print $5}')"
    else
        error "Release APK not found after build"
        exit 1
    fi
}

clean_build() {
    info "Cleaning build artifacts..."
    
    cd "$APP_DIR"
    ./gradlew clean --no-daemon
    
    rm -rf "$APP_DIR/app/build"
    rm -f "$OUTPUT_DIR"/*.apk
    
    success "Clean completed"
}

install_apk() {
    local apk="$OUTPUT_DIR/zapret2-control-debug.apk"
    
    if [ ! -f "$apk" ]; then
        warn "APK not found, building debug version..."
        build_debug
    fi
    
    if command -v adb &> /dev/null; then
        info "Installing APK to device..."
        adb install -r "$apk"
        success "APK installed!"
        
        # Launch app
        if adb shell pm list packages | grep -q "com.zapret2.app"; then
            info "Launching app..."
            adb shell am start -n "com.zapret2.app/.MainActivity"
        fi
    else
        error "ADB not found. Please install Android SDK platform-tools."
        error "APK is ready at: $apk"
    fi
}

build_all() {
    info "Building all variants..."
    build_debug
    echo ""
    build_release
    echo ""
    
    success "All builds completed!"
    echo ""
    echo "Output files:"
    ls -lh "$OUTPUT_DIR"/*.apk 2>/dev/null || true
}

# Main
case "${1:-debug}" in
    debug)
        check_requirements
        setup_output_dir
        build_debug
        ;;
    release)
        check_requirements
        setup_output_dir
        build_release
        ;;
    clean)
        clean_build
        ;;
    all)
        check_requirements
        setup_output_dir
        build_all
        ;;
    install)
        install_apk
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
