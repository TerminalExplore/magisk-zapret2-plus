#!/system/bin/sh
##########################################################################################
# VPN Start Script
# Starts Xray VPN client with direct config or imported subscriptions.
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
MODDIR="$(dirname "$ZAPRET_DIR")"
LOGFILE="/data/local/tmp/zapret2-vpn.log"
PIDFILE="$ZAPRET_DIR/xray.pid"
CONFIG_FILE="$ZAPRET_DIR/xray-config.json"
TUNNEL_SCRIPT="$ZAPRET_DIR/scripts/vpn-tunnel.sh"
PARSER_SCRIPT="$ZAPRET_DIR/scripts/subscription-parser.sh"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [VPN] $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2-VPN" "$1" 2>/dev/null
}

rotate_log() {
    [ $(wc -c < "$LOGFILE" 2>/dev/null || echo 0) -gt 1048576 ] && > "$LOGFILE"
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
    local arch
    arch="$(uname -m 2>/dev/null)"

    case "$arch" in
        aarch64|arm64) binary="$ZAPRET_DIR/bin/arm64-v8a/xray" ;;
        *) binary="$ZAPRET_DIR/bin/armeabi-v7a/xray" ;;
    esac

    [ -f "$binary" ] || binary="$ZAPRET_DIR/bin/arm64-v8a/xray"
    [ -f "$binary" ] || binary="$ZAPRET_DIR/bin/armeabi-v7a/xray"
    [ -f "$binary" ] || binary="$ZAPRET_DIR/xray-arm64-v8a"
    [ -f "$binary" ] || binary="$ZAPRET_DIR/xray"
    [ -f "$binary" ] || { command -v xray >/dev/null 2>&1 && binary="xray"; }

    if [ -z "$binary" ] || { [ "$binary" != "xray" ] && [ ! -f "$binary" ]; }; then
        log "Xray binary not found"
        return 1
    fi

    [ -x "$binary" ] || chmod 755 "$binary" 2>/dev/null
    log "Using binary: $binary"
    echo "$binary"
}

is_running() {
    [ -f "$PIDFILE" ] && kill -0 $(cat "$PIDFILE") 2>/dev/null
    return $?
}

wait_for_network() {
    local max_wait=30
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if ping -c 1 -W 2 8.8.8.8 >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

check_rate_limit() {
    local fail_file="$ZAPRET_DIR/.vpn_fail_time"
    local now=$(date +%s)
    local last_fail=0
    
    [ -f "$fail_file" ] && last_fail=$(cat "$fail_file" 2>/dev/null || echo 0)
    
    if [ $((now - last_fail)) -lt 60 ]; then
        log "Rate limited: last failure was $((now - last_fail))s ago, waiting..."
        return 1
    fi
    
    return 0
}

prepare_config() {
    if [ -n "$VPN_SELECTED_URI" ]; then
        log "Using selected server URI from subscription"
        if "$PARSER_SCRIPT" uri "$VPN_SELECTED_URI"; then
            log "Selected server config generated"
            return 0
        fi
        log "Failed to generate config from selected server URI"
    fi

    if [ -s "$ZAPRET_DIR/vpn-config.json" ]; then
        log "Using saved VPN config"
        cp "$ZAPRET_DIR/vpn-config.json" "$CONFIG_FILE"
        return 0
    fi
    
    if [ -n "$VPN_SUBSCRIPTION_URL" ]; then
        log "Waiting for network..."
        wait_for_network || log "Network not ready, proceeding anyway"
        log "Fetching subscription from: $VPN_SUBSCRIPTION_URL"
        "$PARSER_SCRIPT" import "$VPN_SUBSCRIPTION_URL"
        
        if [ -s "$ZAPRET_DIR/vpn-config.json" ]; then
            cp "$ZAPRET_DIR/vpn-config.json" "$CONFIG_FILE"
            log "Subscription imported successfully"
            return 0
        fi
        
        log "Failed to fetch subscription"
        return 1
    fi

    if "$PARSER_SCRIPT" env "$ZAPRET_DIR/vpn-config.env"; then
        log "Generated VPN config from direct settings"
        return 0
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

    if grep -q '\${' "$CONFIG_FILE" 2>/dev/null; then
        log "Config file still contains unresolved placeholders"
        return 1
    fi

    $binary run -c "$CONFIG_FILE" >> "$LOGFILE" 2>&1 &
    local pid=$!

    # Wait up to 5s for xray to be alive and tunnel to be up
    local elapsed=0
    while [ $elapsed -lt 5 ]; do
        sleep 1
        elapsed=$((elapsed + 1))
        kill -0 $pid 2>/dev/null || break
    done

    if ! kill -0 $pid 2>/dev/null; then
        log "Xray failed to start"
        return 1
    fi

    echo $pid > "$PIDFILE"
    log "Xray started (PID: $pid)"

    # Verify tunnel is actually routing traffic
    if ping -c 1 -W 3 8.8.8.8 >/dev/null 2>&1; then
        log "Tunnel connectivity verified"
    else
        log "WARNING: Xray running but tunnel connectivity check failed"
    fi

    return 0
}

main() {
    rotate_log
    log "=== VPN Start ==="
    
    check_rate_limit || return 1
    
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
        log "VPN started successfully (PID: $(cat $PIDFILE))"
        echo "VPN started (PID: $(cat $PIDFILE))"
        rm -f "$ZAPRET_DIR/.vpn_fail_time" 2>/dev/null
        return 0
    else
        log "=== VPN Start Failed ==="
        echo $(date +%s) > "$ZAPRET_DIR/.vpn_fail_time"
        return 1
    fi
}

main "$@"
