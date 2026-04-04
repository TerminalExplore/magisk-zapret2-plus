#!/system/bin/sh
##########################################################################################
# Zapret2 Magisk Module - Service Script (runs at boot)
##########################################################################################

MODDIR="${0%/*}"
LOGFILE="/data/local/tmp/zapret2.log"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2" "$1" 2>/dev/null
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
if [ "$AUTO_SWITCH" = "1" ]; then
    log "=== AUTO-SWITCH MODE ENABLED ==="
    log "WiFi → Zapret2"
    log "Mobile → VPN"
    
    if [ "$VPN_ENABLED" != "1" ]; then
        log "WARNING: AUTO_SWITCH enabled but VPN not configured (VPN_ENABLED=0)"
        log "Falling back to standard Zapret2 mode"
        if [ "$AUTOSTART" = "1" ]; then
            "$ZAPRET_DIR/scripts/zapret-start.sh"
        fi
    else
        log "Starting network monitor for auto-switch..."
        "$ZAPRET_DIR/scripts/network-monitor.sh"
    fi
elif [ "$AUTOSTART" = "1" ]; then
    log "Autostart enabled, launching zapret2..."
    "$ZAPRET_DIR/scripts/zapret-start.sh"
else
    log "Autostart disabled in effective core config ($CORE_CONFIG_SOURCE)"
fi

log "=== Zapret2 service script finished ==="
