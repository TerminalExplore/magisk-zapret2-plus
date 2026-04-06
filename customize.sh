#!/system/bin/sh

##########################################################################################
# Zapret2 Magisk Module - Installation Script
##########################################################################################

SKIPUNZIP=1

# Detect architecture
ARCH=$(getprop ro.product.cpu.abi)
ui_print "- Detected architecture: $ARCH"

case "$ARCH" in
    arm64-v8a|arm64*)
        ARCH_DIR="arm64-v8a"
        ;;
    armeabi-v7a|armeabi*)
        ARCH_DIR="armeabi-v7a"
        ;;
    x86_64)
        ARCH_DIR="x86_64"
        ;;
    x86)
        ARCH_DIR="x86"
        ;;
    *)
        abort "! Unsupported architecture: $ARCH"
        ;;
esac

ui_print "- Installing Zapret2 for $ARCH_DIR"

# Backup user settings before extraction (for updates)
USER_CATEGORIES_INI_BAK="/data/local/tmp/zapret2-categories.ini.bak"
USER_RUNTIME_INI_BAK="/data/local/tmp/zapret2-runtime.ini.bak"
USER_VPN_CONFIG_BAK="/data/local/tmp/zapret2-vpn-config.env.bak"
EXISTING_MODPATH="/data/adb/modules/zapret2"
if [ -f "$EXISTING_MODPATH/zapret2/categories.ini" ]; then
    ui_print "- Backing up user strategy settings..."
    cp "$EXISTING_MODPATH/zapret2/categories.ini" "$USER_CATEGORIES_INI_BAK"
fi
if [ -f "$EXISTING_MODPATH/zapret2/runtime.ini" ]; then
    ui_print "- Backing up user runtime settings..."
    cp "$EXISTING_MODPATH/zapret2/runtime.ini" "$USER_RUNTIME_INI_BAK"
fi
if [ -f "$EXISTING_MODPATH/zapret2/vpn-config.env" ]; then
    # Only backup if user has actually configured VPN (VPN_ENABLED=1 or has server set)
    if grep -qE '^VPN_ENABLED=1|^VPN_SERVER="[^"]+"|^VPN_SUBSCRIPTION_URL="[^"]+"' \
            "$EXISTING_MODPATH/zapret2/vpn-config.env" 2>/dev/null; then
        ui_print "- Backing up VPN config..."
        cp "$EXISTING_MODPATH/zapret2/vpn-config.env" "$USER_VPN_CONFIG_BAK"
    fi
fi

# Create directories
mkdir -p "$MODPATH/zapret2/bin"
mkdir -p "$MODPATH/zapret2/lua"
mkdir -p "$MODPATH/zapret2/lists"
mkdir -p "$MODPATH/zapret2/scripts"
mkdir -p "$MODPATH/system/bin"
mkdir -p "$MODPATH/webroot"

# Extract all files
ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d "$MODPATH" >&2

# Restore user strategy settings if backup exists
if [ -f "$USER_CATEGORIES_INI_BAK" ]; then
    ui_print "- Restoring user strategy settings..."
    cp "$USER_CATEGORIES_INI_BAK" "$MODPATH/zapret2/categories.ini"
    rm -f "$USER_CATEGORIES_INI_BAK"
    ui_print "  [OK] Strategy settings preserved"
fi

# Restore user runtime settings if backup exists
if [ -f "$USER_RUNTIME_INI_BAK" ]; then
    ui_print "- Restoring user runtime settings..."
    cp "$USER_RUNTIME_INI_BAK" "$MODPATH/zapret2/runtime.ini"
    rm -f "$USER_RUNTIME_INI_BAK"
    ui_print "  [OK] Runtime settings preserved"
fi

# Restore VPN config if backup exists
if [ -f "$USER_VPN_CONFIG_BAK" ]; then
    ui_print "- Restoring VPN config..."
    cp "$USER_VPN_CONFIG_BAK" "$MODPATH/zapret2/vpn-config.env"
    rm -f "$USER_VPN_CONFIG_BAK"
    ui_print "  [OK] VPN config preserved"
fi

# Copy architecture-specific binary
if [ -f "$MODPATH/zapret2/bin/$ARCH_DIR/nfqws2" ]; then
    cp "$MODPATH/zapret2/bin/$ARCH_DIR/nfqws2" "$MODPATH/zapret2/nfqws2"
    ui_print "- Copied nfqws2 binary for $ARCH_DIR"
elif [ "$ARCH_DIR" = "arm64-v8a" ] && [ -f "$MODPATH/zapret2/nfqws2" ]; then
    ui_print "- Using bundled fallback nfqws2 binary for $ARCH_DIR"
else
    ui_print "! Warning: nfqws2 binary not found for $ARCH_DIR"
    ui_print "! Please download from GitHub releases"
fi

# Copy Xray binary (VPN client)
if [ -f "$MODPATH/zapret2/bin/$ARCH_DIR/xray" ]; then
    cp "$MODPATH/zapret2/bin/$ARCH_DIR/xray" "$MODPATH/zapret2/xray"
    ui_print "- Copied xray binary for $ARCH_DIR"
else
    ui_print "! Warning: xray binary not found for $ARCH_DIR"
    ui_print "! VPN functionality will not be available"
fi

# Set permissions
ui_print "- Setting permissions..."

# Set directory permissions (0755 = rwxr-xr-x)
find "$MODPATH" -type d -exec chmod 0755 {} \;

# Set file permissions (0644 = rw-r--r--)
find "$MODPATH" -type f -exec chmod 0644 {} \;

# Set executable permissions for scripts and binary
set_perm "$MODPATH/zapret2/nfqws2" 0 0 0755
set_perm "$MODPATH/zapret2/xray" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/zapret-start.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/zapret-stop.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/zapret-restart.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/zapret-status.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/network-monitor.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/vpn-start.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/vpn-stop.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/vpn-tunnel.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/subscription-parser.sh" 0 0 0755
set_perm "$MODPATH/zapret2/scripts/app-filter.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/customize.sh" 0 0 0755

# Make sure bin and lua directories are accessible
chmod 0755 "$MODPATH/zapret2/bin"
chmod 0755 "$MODPATH/zapret2/lua"
chmod 0755 "$MODPATH/zapret2/lists"
chmod 0755 "$MODPATH/zapret2/scripts"
chmod 0755 "$MODPATH/zapret2"

# Set read permissions on all data files
chmod -R 0644 "$MODPATH/zapret2/bin/"*.bin 2>/dev/null || true
chmod -R 0644 "$MODPATH/zapret2/lua/"*.lua 2>/dev/null || true
chmod -R 0644 "$MODPATH/zapret2/lists/"*.txt 2>/dev/null || true

# Check kernel requirements
ui_print "- Checking kernel requirements..."

# Check for NFQUEUE support (legacy + modern indicators)
NFQUEUE_SUPPORTED=0

if [ -f /proc/net/netfilter/nf_queue ] || [ -f /proc/net/netfilter/nfnetlink_queue ]; then
    NFQUEUE_SUPPORTED=1
elif grep -qs NFQUEUE /proc/net/ip_tables_targets /proc/net/ip6_tables_targets; then
    NFQUEUE_SUPPORTED=1
fi

if [ "$NFQUEUE_SUPPORTED" -eq 1 ]; then
    ui_print "  [OK] NFQUEUE support found"
else
    ui_print "  [!] NFQUEUE support not detected"
    ui_print "      Checked: nf_queue, nfnetlink_queue, ip_tables_targets"
    ui_print "      Module may not work on this kernel"
fi

# Check iptables
if command -v iptables >/dev/null 2>&1; then
    ui_print "  [OK] iptables found"
else
    ui_print "  [!] iptables not found"
fi

# Create symlink for easy access
ln -sf "$MODPATH/zapret2/scripts/zapret-start.sh" "$MODPATH/system/bin/zapret2-start"
ln -sf "$MODPATH/zapret2/scripts/zapret-stop.sh" "$MODPATH/system/bin/zapret2-stop"
ln -sf "$MODPATH/zapret2/scripts/zapret-restart.sh" "$MODPATH/system/bin/zapret2-restart"
ln -sf "$MODPATH/zapret2/scripts/zapret-status.sh" "$MODPATH/system/bin/zapret2-status"
ln -sf "$MODPATH/zapret2/scripts/vpn-start.sh" "$MODPATH/system/bin/zapret2-vpn-start"
ln -sf "$MODPATH/zapret2/scripts/vpn-stop.sh" "$MODPATH/system/bin/zapret2-vpn-stop"
ln -sf "$MODPATH/zapret2/scripts/network-monitor.sh" "$MODPATH/system/bin/zapret2-network-monitor"
ln -sf "$MODPATH/zapret2/scripts/app-filter.sh" "$MODPATH/system/bin/zapret2-app-filter"

ui_print ""
ui_print "===================================="
ui_print " Zapret2 installed successfully!"
ui_print "===================================="
ui_print ""
ui_print " Control UI: Use the companion Android app"
ui_print "             or terminal commands below"
ui_print ""
ui_print " Terminal commands:"
ui_print "   zapret2-start          - Start Zapret2"
ui_print "   zapret2-stop           - Stop Zapret2"
ui_print "   zapret2-restart        - Restart (fast by default)"
ui_print "   zapret2-status        - Status"
ui_print "   zapret2-vpn-start     - Start VPN"
ui_print "   zapret2-vpn-stop      - Stop VPN"
ui_print "   zapret2-network-monitor - Start auto-switch"
ui_print "   zapret2-app-filter     - App filter control"
ui_print ""
ui_print " Config files:"
ui_print "   Runtime:    $MODPATH/zapret2/runtime.ini"
ui_print "   Categories: $MODPATH/zapret2/categories.ini"
ui_print "   VPN Config: $MODPATH/zapret2/vpn-config.env"
ui_print "   App Filter: $MODPATH/zapret2/app-filter.ini"
ui_print "   TCP:        $MODPATH/zapret2/strategies-tcp.ini"
ui_print "   UDP:        $MODPATH/zapret2/strategies-udp.ini"
ui_print "   STUN:       $MODPATH/zapret2/strategies-stun.ini"
ui_print ""
