#!/system/bin/sh
##########################################################################################
# VPN Stop Script
# Stops Xray VPN client
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
LOGFILE="/data/local/tmp/zapret2-vpn.log"
PIDFILE="$ZAPRET_DIR/xray.pid"
TUNNEL_SCRIPT="$ZAPRET_DIR/scripts/vpn-tunnel.sh"
CONFIG_FILE="$ZAPRET_DIR/xray-config.json"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [VPN] $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2-VPN" "$1" 2>/dev/null
}

stop_tunnel() {
    if [ -f "$TUNNEL_SCRIPT" ]; then
        "$TUNNEL_SCRIPT" stop 2>/dev/null
    fi
}

kill_process() {
    local pid="$1"
    local name="$2"
    
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        log "Stopping $name (PID: $pid)..."
        kill "$pid" 2>/dev/null
        
        local count=0
        while kill -0 "$pid" 2>/dev/null && [ $count -lt 10 ]; do
            sleep 0.5
            count=$((count + 1))
        done
        
        if kill -0 "$pid" 2>/dev/null; then
            log "Force killing $name..."
            kill -9 "$pid" 2>/dev/null
        fi
        
        log "$name stopped"
    fi
}

main() {
    log "=== VPN Stop ==="
    
    local killed=0
    
    if [ -f "$PIDFILE" ]; then
        local pid=$(cat "$PIDFILE")
        kill_process "$pid" "Xray"
        rm -f "$PIDFILE"
        killed=1
    fi
    
    local xray_pids=$(pgrep -f "$CONFIG_FILE" 2>/dev/null)
    if [ -n "$xray_pids" ]; then
        for pid in $xray_pids; do
            kill_process "$pid" "Xray (orphan)"
        done
        killed=1
    fi

    stop_tunnel
    
    if [ "$killed" -eq 1 ]; then
        log "=== VPN Stopped ==="
        echo "VPN stopped"
    else
        log "VPN was not running"
        echo "VPN was not running"
    fi
}

main "$@"
