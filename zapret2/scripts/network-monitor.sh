#!/system/bin/sh
##########################################################################################
# Network Monitor Script
# Automatically switches between Zapret2 (WiFi) and VPN (Mobile)
##########################################################################################

MODDIR="${0%/*}/.."
ZAPRET_DIR="$MODDIR/zapret2"
LOGFILE="/data/local/tmp/zapret2-network.log"
LAST_NETWORK=""

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [NETMON] $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2-NetMon" "$1" 2>/dev/null
}

is_wifi_connected() {
    local wifi_state=""
    
    if [ -f /sys/class/net/wlan0/operstate ]; then
        wifi_state=$(cat /sys/class/net/wlan0/operstate 2>/dev/null)
        [ "$wifi_state" = "up" ] && return 0
    fi
    
    if [ -f /sys/class/net/wl*/operstate ]; then
        wifi_state=$(cat /sys/class/net/wl*/operstate 2>/dev/null | head -1)
        [ "$wifi_state" = "up" ] && return 0
    fi
    
    if command -v ip >/dev/null 2>&1; then
        ip link show wlan0 2>/dev/null | grep -q "state UP" && return 0
        ip link show wl* 2>/dev/null | grep -q "state UP" && return 0
    fi
    
    if command -v dumpsys >/dev/null 2>&1; then
        dumpsys connectivity 2>/dev/null | grep -i "WIFI" | grep -qi "state: CONNECTED" && return 0
    fi
    
    return 1
}

is_mobile_connected() {
    local mobile_state=""
    
    if [ -f /sys/class/net/rmnet*/operstate ]; then
        mobile_state=$(cat /sys/class/net/rmnet*/operstate 2>/dev/null | head -1)
        [ "$mobile_state" = "up" ] && return 0
    fi
    
    if [ -f /sys/class/net/ppp0/operstate ]; then
        mobile_state=$(cat /sys/class/net/ppp0/operstate 2>/dev/null)
        [ "$mobile_state" = "up" ] && return 0
    fi
    
    if command -v ip >/dev/null 2>&1; then
        ip link show rmnet* 2>/dev/null | grep -q "state UP" && return 0
        ip link show ppp0 2>/dev/null | grep -q "state UP" && return 0
    fi
    
    if command -v getprop >/dev/null 2>&1; then
        local data_enabled=$(getprop sys.data.enabled 2>/dev/null)
        [ "$data_enabled" = "true" ] && return 0
    fi
    
    return 1
}

is_vpn_running() {
    [ -f "$ZAPRET_DIR/xray.pid" ] && kill -0 $(cat "$ZAPRET_DIR/xray.pid") 2>/dev/null
    return $?
}

is_zapret_running() {
    [ -f "$ZAPRET_DIR/nfqws2.pid" ] && kill -0 $(cat "$ZAPRET_DIR/nfqws2.pid") 2>/dev/null
    return $?
}

start_zapret2() {
    log "Starting Zapret2 (WiFi mode)..."
    
    if is_zapret_running; then
        log "Zapret2 already running"
        return 0
    fi
    
    if is_vpn_running; then
        log "Stopping VPN first..."
        "$ZAPRET_DIR/scripts/vpn-stop.sh" 2>/dev/null
        sleep 2
    fi
    
    "$ZAPRET_DIR/scripts/zapret-start.sh"
    
    if is_zapret_running; then
        log "Zapret2 started successfully"
        return 0
    else
        log "Failed to start Zapret2"
        return 1
    fi
}

stop_zapret2() {
    log "Stopping Zapret2..."
    "$ZAPRET_DIR/scripts/zapret-stop.sh" 2>/dev/null
}

start_vpn() {
    log "Starting VPN (Mobile mode)..."
    
    . "$ZAPRET_DIR/vpn-config.env" 2>/dev/null
    
    if [ "$VPN_ENABLED" != "1" ]; then
        log "VPN is disabled in config"
        return 1
    fi
    
    if is_vpn_running; then
        log "VPN already running"
        return 0
    fi
    
    if is_zapret_running; then
        log "Stopping Zapret2 first..."
        stop_zapret2
        sleep 2
    fi
    
    "$ZAPRET_DIR/scripts/vpn-start.sh"
    
    if is_vpn_running; then
        log "VPN started successfully"
        return 0
    else
        log "Failed to start VPN"
        return 1
    fi
}

stop_vpn() {
    log "Stopping VPN..."
    "$ZAPRET_DIR/scripts/vpn-stop.sh" 2>/dev/null
}

stop_all() {
    log "Stopping all services..."
    stop_zapret2
    stop_vpn
}

switch_to_wifi_mode() {
    if [ "$LAST_NETWORK" = "wifi" ] && is_zapret_running; then
        log "Already in WiFi/Zapret2 mode"
        return 0
    fi
    
    log "=== SWITCHING TO WIFI MODE (Zapret2) ==="
    
    stop_vpn
    sleep 1
    start_zapret2
    
    LAST_NETWORK="wifi"
    echo "$LAST_NETWORK" > "$ZAPRET_DIR/.last_network" 2>/dev/null
}

switch_to_mobile_mode() {
    if [ "$LAST_NETWORK" = "mobile" ] && is_vpn_running; then
        log "Already in Mobile/VPN mode"
        return 0
    fi
    
    log "=== SWITCHING TO MOBILE MODE (VPN) ==="
    
    stop_zapret2
    sleep 1
    start_vpn
    
    LAST_NETWORK="mobile"
    echo "$LAST_NETWORK" > "$ZAPRET_DIR/.last_network" 2>/dev/null
}

handle_no_network() {
    if [ "$LAST_NETWORK" = "none" ]; then
        return 0
    fi
    
    log "=== NO NETWORK DETECTED ==="
    stop_all
    
    LAST_NETWORK="none"
    echo "$LAST_NETWORK" > "$ZAPRET_DIR/.last_network" 2>/dev/null
}

detect_network() {
    if is_wifi_connected; then
        echo "wifi"
    elif is_mobile_connected; then
        echo "mobile"
    else
        echo "none"
    fi
}

main() {
    log "Network monitor starting..."
    
    LAST_NETWORK=$(cat "$ZAPRET_DIR/.last_network" 2>/dev/null)
    
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 5
    done
    
    sleep 15
    
    log "Network monitor running. PID: $$"
    
    while true; do
        CURRENT_NETWORK=$(detect_network)
        
        case "$CURRENT_NETWORK" in
            wifi)
                switch_to_wifi_mode
                ;;
            mobile)
                switch_to_mobile_mode
                ;;
            none)
                handle_no_network
                ;;
        esac
        
        sleep 5
    done
}

FOREGROUND="${1:-0}"

if [ "$FOREGROUND" = "fg" ] || [ "$FOREGROUND" = "--foreground" ]; then
    main
else
    main &
    echo $! > "$ZAPRET_DIR/network-monitor.pid"
    log "Network monitor started in background (PID: $(cat $ZAPRET_DIR/network-monitor.pid))"
fi
