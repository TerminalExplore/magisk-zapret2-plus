#!/system/bin/sh
##########################################################################################
# Zapret2 Magisk Module - Service Script (runs at boot)
##########################################################################################

MODDIR="$(cd "${0%/*}" && pwd)"
LOGFILE="/data/local/tmp/zapret2.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2" "$1" 2>/dev/null
}

wifi_is_up() {
    if [ -f /sys/class/net/wlan0/operstate ]; then
        [ "$(cat /sys/class/net/wlan0/operstate 2>/dev/null)" = "up" ] && return 0
    fi

    if command -v ip >/dev/null 2>&1; then
        ip link show wlan0 2>/dev/null | grep -q "state UP" && return 0
        ip link show wlan1 2>/dev/null | grep -q "state UP" && return 0
    fi

    if command -v dumpsys >/dev/null 2>&1; then
        dumpsys connectivity 2>/dev/null | grep -i "WIFI" | grep -qi "state: CONNECTED" && return 0
    fi

    return 1
}

log "=== Zapret2 service starting ==="

# Wait for boot to complete
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 5
done

log "Boot completed, waiting for network..."

# Wait until network appears, up to 15 seconds
waited=0
while [ "$waited" -lt 15 ]; do
    if command -v ip >/dev/null 2>&1; then
        if [ -n "$(ip route show default 2>/dev/null)" ]; then
            log "Network is ready after ${waited}s"
            break
        fi
    elif [ -n "$(getprop net.dns1)" ]; then
        log "DNS property detected after ${waited}s"
        break
    fi

    sleep 1
    waited=$((waited + 1))
done

if [ "$waited" -ge 15 ]; then
    log "Network wait timeout reached, continuing startup"
fi

# Check if autostart is enabled
ZAPRET_DIR="$MODDIR/zapret2"
SCRIPT_DIR="$ZAPRET_DIR/scripts"

# Load VPN config for auto-switch mode
if [ -f "$ZAPRET_DIR/vpn-config.env" ]; then
    . "$ZAPRET_DIR/vpn-config.env"
fi

. "$SCRIPT_DIR/common.sh"
load_effective_core_config

case "$RUNTIME_CONFIG_STATUS" in
    loaded|regenerated)
        log "$(runtime_config_status_message)"
        ;;
    legacy-fallback)
        log "$(runtime_config_status_message)"
        if [ -n "$RUNTIME_CONFIG_ERROR" ]; then
            log "runtime.ini regeneration failed: $RUNTIME_CONFIG_ERROR"
        fi
        ;;
esac

log "$(core_config_source_message)"
log "$(bootstrap_fallback_message)"
log "Category state source: $CATEGORIES_FILE"

# Check for auto-switch mode
if [ -f "$ZAPRET_DIR/vpn-config.env" ]; then
    . "$ZAPRET_DIR/vpn-config.env"
fi

# Resolve effective mode (VPN_MODE takes priority, AUTO_SWITCH for backward compat)
EFFECTIVE_VPN_MODE="${VPN_MODE:-off}"
if [ "$EFFECTIVE_VPN_MODE" = "off" ] && [ "${AUTO_SWITCH:-0}" = "1" ]; then
    EFFECTIVE_VPN_MODE="mobile"
fi

if [ "$EFFECTIVE_VPN_MODE" != "off" ] && [ "$VPN_ENABLED" = "1" ]; then
    log "=== VPN_MODE=$EFFECTIVE_VPN_MODE — starting network monitor ==="
    "$ZAPRET_DIR/scripts/network-monitor.sh"
elif [ "$AUTOSTART" = "1" ]; then
    if [ "$WIFI_ONLY" = "1" ]; then
        if ! wifi_is_up; then
            log "Autostart skipped because WIFI_ONLY=1 and WiFi is not connected"
        else
            log "Autostart enabled in WiFi-only mode, launching zapret2..."
            "$ZAPRET_DIR/scripts/zapret-start.sh"
        fi
    else
        log "Autostart enabled, launching zapret2..."
        "$ZAPRET_DIR/scripts/zapret-start.sh"
    fi
else
    log "Autostart disabled in effective core config ($CORE_CONFIG_SOURCE)"
fi

log "=== Zapret2 service script finished ==="
