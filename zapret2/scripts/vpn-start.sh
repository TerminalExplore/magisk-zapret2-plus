#!/system/bin/sh
##########################################################################################
# VPN Start Script
# Starts Xray/Singbox VPN client with VLESS/SS/VMess support
##########################################################################################

MODDIR="${0%/*}/.."
ZAPRET_DIR="$MODDIR/zapret2"
LOGFILE="/data/local/tmp/zapret2-vpn.log"
PIDFILE="$ZAPRET_DIR/xray.pid"
CONFIG_FILE="$ZAPRET_DIR/xray-config.json"
TUNNEL_SCRIPT="$ZAPRET_DIR/scripts/vpn-tunnel.sh"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [VPN] $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2-VPN" "$1" 2>/dev/null
}

load_config() {
    if [ -f "$ZAPRET_DIR/vpn-config.env" ]; then
        . "$ZAPRET_DIR/vpn-config.env"
    else
        log "VPN config not found: $ZAPRET_DIR/vpn-config.env"
        return 1
    fi
    
    if [ "$VPN_ENABLED" != "1" ]; then
        log "VPN is disabled in config"
        return 1
    fi
    
    return 0
}

check_binary() {
    local binary=""
    local arch=$(uname -m)
    
    if [ -f "$ZAPRET_DIR/xray" ]; then
        binary="$ZAPRET_DIR/xray"
    elif [ -f "$ZAPRET_DIR/bin/arm64-v8a/xray" ]; then
        binary="$ZAPRET_DIR/bin/arm64-v8a/xray"
    elif [ -f "$ZAPRET_DIR/bin/armeabi-v7a/xray" ]; then
        binary="$ZAPRET_DIR/bin/armeabi-v7a/xray"
    elif command -v xray >/dev/null 2>&1; then
        binary="xray"
    fi
    
    if [ -z "$binary" ]; then
        log "Xray binary not found"
        return 1
    fi
    
    if [ ! -x "$binary" ]; then
        chmod 755 "$binary" 2>/dev/null
    fi
    
    log "Using binary: $binary"
    echo "$binary"
}

is_running() {
    [ -f "$PIDFILE" ] && kill -0 $(cat "$PIDFILE") 2>/dev/null
    return $?
}

prepare_config() {
    if [ -f "$ZAPRET_DIR/vpn-config.json" ]; then
        log "Using saved VPN config"
        cp "$ZAPRET_DIR/vpn-config.json" "$CONFIG_FILE"
        return 0
    fi
    
    if [ -n "$VPN_SUBSCRIPTION_URL" ]; then
        log "Fetching subscription from: $VPN_SUBSCRIPTION_URL"
        "$ZAPRET_DIR/scripts/subscription-parser.sh" import "$VPN_SUBSCRIPTION_URL"
        
        if [ -f "$ZAPRET_DIR/vpn-config.json" ]; then
            cp "$ZAPRET_DIR/vpn-config.json" "$CONFIG_FILE"
            log "Subscription imported successfully"
            return 0
        fi
        
        log "Failed to fetch subscription"
        return 1
    fi
    
    log "No VPN configuration provided"
    return 1
}

start_tunnel() {
    if [ -f "$TUNNEL_SCRIPT" ]; then
        "$TUNNEL_SCRIPT" start
    fi
}

start_xray() {
    local binary="$1"
    
    log "Starting Xray..."
    
    if [ ! -f "$CONFIG_FILE" ]; then
        log "Config file not found: $CONFIG_FILE"
        return 1
    fi
    
    $binary run -c "$CONFIG_FILE" >> "$LOGFILE" 2>&1 &
    local pid=$!
    
    sleep 2
    
    if kill -0 $pid 2>/dev/null; then
        echo $pid > "$PIDFILE"
        log "Xray started (PID: $pid)"
        return 0
    else
        log "Xray failed to start"
        return 1
    fi
}

main() {
    log "=== VPN Start ==="
    
    if is_running; then
        log "VPN already running (PID: $(cat $PIDFILE))"
        echo "VPN already running (PID: $(cat $PIDFILE))"
        return 0
    fi
    
    load_config || return 1
    
    local xray_binary
    xray_binary=$(check_binary) || return 1
    
    prepare_config || return 1
    
    start_tunnel
    
    start_xray "$xray_binary" || return 1
    
    if is_running; then
        log "=== VPN Started Successfully ==="
        echo "VPN started (PID: $(cat $PIDFILE))"
        return 0
    else
        log "=== VPN Start Failed ==="
        return 1
    fi
}

main "$@"
