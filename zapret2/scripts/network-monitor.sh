#!/system/bin/sh
##########################################################################################
# Network Monitor Script
# Automatically switches between Zapret2 (WiFi) and VPN (Mobile)
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
MODDIR="$(dirname "$ZAPRET_DIR")"
. "$SCRIPT_DIR/common.sh"
LOGFILE="/data/local/tmp/zapret2-network.log"
LAST_NETWORK=""
NETWORK_MONITOR_PIDFILE="$ZAPRET_DIR/network-monitor.pid"
SWITCH_LOCK="$ZAPRET_DIR/.switch.lock"

cleanup_monitor() {
    local current_pid="$1"
    if [ -f "$NETWORK_MONITOR_PIDFILE" ] && [ "$(cat "$NETWORK_MONITOR_PIDFILE" 2>/dev/null)" = "$current_pid" ]; then
        rm -f "$NETWORK_MONITOR_PIDFILE"
    fi
}

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [NETMON] $1" >> "$LOGFILE"
    /system/bin/log -t "Zapret2-NetMon" "$1" 2>/dev/null
}

rotate_log() {
    [ $(wc -c < "$LOGFILE" 2>/dev/null || echo 0) -gt 1048576 ] && > "$LOGFILE"
}

send_notification() {
    local title="$1"
    local text="$2"
    am broadcast -a android.intent.action.SEND \
        --es title "$title" --es text "$text" \
        -n com.zapret2.app/.receiver.NotificationReceiver 2>/dev/null || true
}

acquire_lock() {
    local deadline=$(( $(date +%s) + 10 ))
    while [ -f "$SWITCH_LOCK" ]; do
        [ $(date +%s) -ge $deadline ] && { rm -f "$SWITCH_LOCK"; break; }
        sleep 0.5
    done
    echo $$ > "$SWITCH_LOCK"
}

release_lock() {
    rm -f "$SWITCH_LOCK"
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
    # Check for active default route via a mobile interface (rmnet*, ppp*, ccmni*)
    if command -v ip >/dev/null 2>&1; then
        ip route show default 2>/dev/null | grep -qE 'rmnet|ppp|ccmni' && return 0
    fi

    if [ -f /sys/class/net/ppp0/operstate ]; then
        [ "$(cat /sys/class/net/ppp0/operstate 2>/dev/null)" = "up" ] && return 0
    fi

    for iface in /sys/class/net/rmnet*/operstate /sys/class/net/ccmni*/operstate; do
        [ -f "$iface" ] && [ "$(cat "$iface" 2>/dev/null)" = "up" ] && return 0
    done

    return 1
}

is_vpn_running() {
    [ -f "$ZAPRET_DIR/xray.pid" ] && kill -0 $(cat "$ZAPRET_DIR/xray.pid") 2>/dev/null
    return $?
}

is_zapret_running() {
    [ -f "$PIDFILE" ] && kill -0 $(cat "$PIDFILE") 2>/dev/null
    return $?
}

wait_for_service() {
    local check_fn="$1"
    local timeout="${2:-10}"
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        $check_fn && return 0
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
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
        wait_for_service '! is_vpn_running' 5
    fi

    "$ZAPRET_DIR/scripts/zapret-start.sh"

    if wait_for_service is_zapret_running 8; then
        log "Zapret2 started successfully"
        send_notification "Zapret2" "WiFi mode: DPI bypass active"
        return 0
    else
        log "Failed to start Zapret2"
        send_notification "Zapret2" "Failed to start DPI bypass"
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

    if [ "${VPN_AUTOSTART:-1}" != "1" ]; then
        log "VPN autostart is disabled"
        return 0
    fi

    if is_vpn_running; then
        log "VPN already running"
        return 0
    fi

    if is_zapret_running; then
        log "Stopping Zapret2 first..."
        stop_zapret2
        wait_for_service '! is_zapret_running' 5
    fi

    "$ZAPRET_DIR/scripts/vpn-start.sh"

    if wait_for_service is_vpn_running 10; then
        log "VPN started successfully"
        send_notification "Zapret2" "Mobile mode: VPN active"
        return 0
    else
        log "Failed to start VPN"
        send_notification "Zapret2" "Failed to start VPN"
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
        return 0
    fi

    acquire_lock
    log "=== SWITCHING TO WIFI MODE ==="

    . "$ZAPRET_DIR/vpn-config.env" 2>/dev/null
    local mode="${VPN_MODE:-off}"
    # backward compat
    [ "$mode" = "off" ] && [ "${AUTO_SWITCH:-0}" = "1" ] && mode="mobile"

    case "$mode" in
        always|wifi)
            log "WiFi mode: VPN (VPN_MODE=$mode)"
            stop_zapret2
            start_vpn
            ;;
        *)
            log "WiFi mode: Zapret2 (VPN_MODE=$mode)"
            stop_vpn
            start_zapret2
            ;;
    esac

    LAST_NETWORK="wifi"
    echo "$LAST_NETWORK" > "$ZAPRET_DIR/.last_network" 2>/dev/null
    release_lock
}

switch_to_mobile_mode() {
    if [ "$LAST_NETWORK" = "mobile" ] && is_vpn_running; then
        return 0
    fi

    acquire_lock
    log "=== SWITCHING TO MOBILE MODE ==="

    . "$ZAPRET_DIR/vpn-config.env" 2>/dev/null
    local mode="${VPN_MODE:-off}"
    [ "$mode" = "off" ] && [ "${AUTO_SWITCH:-0}" = "1" ] && mode="mobile"

    case "$mode" in
        always|mobile)
            log "Mobile mode: VPN (VPN_MODE=$mode)"
            stop_zapret2
            start_vpn
            ;;
        wifi)
            log "Mobile mode: Zapret2 (VPN_MODE=$mode)"
            stop_vpn
            start_zapret2
            ;;
        *)
            log "Mobile mode: Zapret2 (VPN_MODE=$mode)"
            stop_vpn
            start_zapret2
            ;;
    esac

    LAST_NETWORK="mobile"
    echo "$LAST_NETWORK" > "$ZAPRET_DIR/.last_network" 2>/dev/null
    release_lock
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

watchdog() {
    case "$LAST_NETWORK" in
        wifi)
            if ! is_zapret_running; then
                log "[WATCHDOG] Zapret2 died, restarting..."
                send_notification "Zapret2" "DPI bypass crashed, restarting..."
                start_zapret2
            fi
            ;;
        mobile)
            if ! is_vpn_running; then
                log "[WATCHDOG] VPN died, restarting..."
                send_notification "Zapret2" "VPN crashed, restarting..."
                start_vpn
            fi
            ;;
    esac
}

main() {
    log "Network monitor starting..."
    trap 'cleanup_monitor "$$"; release_lock' EXIT INT TERM

    LAST_NETWORK=$(cat "$ZAPRET_DIR/.last_network" 2>/dev/null)

    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 5
    done

    sleep 15

    log "Network monitor running. PID: $$"

    while true; do
        rotate_log
        CURRENT_NETWORK=$(detect_network)

        case "$CURRENT_NETWORK" in
            wifi)   switch_to_wifi_mode ;;
            mobile) switch_to_mobile_mode ;;
            none)   handle_no_network ;;
        esac

        watchdog

        sleep 5
    done
}

stop_monitor() {
    if [ -f "$NETWORK_MONITOR_PIDFILE" ]; then
        local pid
        pid="$(cat "$NETWORK_MONITOR_PIDFILE" 2>/dev/null)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            log "Stopping network monitor (PID: $pid)"
            kill "$pid" 2>/dev/null
            rm -f "$NETWORK_MONITOR_PIDFILE"
            echo "Network monitor stopped"
            return 0
        fi
    fi

    rm -f "$NETWORK_MONITOR_PIDFILE"
    echo "Network monitor is not running"
    return 1
}

print_status() {
    local monitor_status="stopped"
    local monitor_pid=""
    local current_network
    local zapret_status="stopped"
    local vpn_status="stopped"

    if [ -f "$NETWORK_MONITOR_PIDFILE" ]; then
        monitor_pid="$(cat "$NETWORK_MONITOR_PIDFILE" 2>/dev/null)"
        if [ -n "$monitor_pid" ] && kill -0 "$monitor_pid" 2>/dev/null; then
            monitor_status="running"
        fi
    fi

    current_network="$(detect_network)"
    is_zapret_running && zapret_status="running"
    is_vpn_running && vpn_status="running"

    echo "Monitor: $monitor_status${monitor_pid:+ (PID: $monitor_pid)}"
    echo "Network: $current_network"
    echo "Zapret2: $zapret_status"
    echo "VPN: $vpn_status"
    echo "Last network: $(cat "$ZAPRET_DIR/.last_network" 2>/dev/null || echo unknown)"
}

FOREGROUND="${1:-0}"

if [ "$FOREGROUND" = "status" ] || [ "$FOREGROUND" = "--status" ]; then
    print_status
elif [ "$FOREGROUND" = "stop" ] || [ "$FOREGROUND" = "--stop" ]; then
    stop_monitor
elif [ "$FOREGROUND" = "fg" ] || [ "$FOREGROUND" = "--foreground" ]; then
    main
else
    if [ -f "$NETWORK_MONITOR_PIDFILE" ] && kill -0 "$(cat "$NETWORK_MONITOR_PIDFILE" 2>/dev/null)" 2>/dev/null; then
        log "Network monitor already running"
        exit 0
    fi
    main &
    MONITOR_PID=$!
    echo $MONITOR_PID > "$NETWORK_MONITOR_PIDFILE"
    log "Network monitor started in background (PID: $MONITOR_PID)"
fi
