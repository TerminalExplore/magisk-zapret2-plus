#!/system/bin/sh
##########################################################################################
# VPN Tunnel Script
# Manages TUN interface for VPN
##########################################################################################

MODDIR="${0%/*}/.."
ZAPRET_DIR="$MODDIR/zapret2"
LOGFILE="/data/local/tmp/zapret2-vpn.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [TUNNEL] $1" >> "$LOGFILE"
}

TUN_DEV="tun0"
TUN_ADDR="10.0.0.2/30"
TUN_MTU="1500"

setup_tun() {
    log "Setting up TUN interface..."
    
    ip link del "$TUN_DEV" 2>/dev/null
    
    if command -v ip >/dev/null 2>&1; then
        ip tuntap add mode tun dev "$TUN_DEV" 2>/dev/null
        ip addr add "$TUN_ADDR" dev "$TUN_DEV" 2>/dev/null
        ip link set "$TUN_DEV" up 2>/dev/null
        ip link set mtu "$TUN_MTU" dev "$TUN_DEV" 2>/dev/null
        
        log "TUN interface $TUN_DEV created: $TUN_ADDR"
        return 0
    fi
    
    log "ip command not found, TUN may already exist"
    return 0
}

cleanup_tun() {
    log "Cleaning up TUN interface..."
    
    if command -v ip >/dev/null 2>&1; then
        ip link del "$TUN_DEV" 2>/dev/null
        log "TUN interface $TUN_DEV deleted"
    fi
}

is_tun_up() {
    ip link show "$TUN_DEV" 2>/dev/null | grep -q "state UP"
    return $?
}

main() {
    case "$1" in
        start)
            if is_tun_up; then
                log "TUN interface already up"
            else
                setup_tun
            fi
            ;;
        stop)
            cleanup_tun
            ;;
        restart)
            cleanup_tun
            sleep 1
            setup_tun
            ;;
        status)
            if is_tun_up; then
                echo "TUN: UP"
                ip addr show "$TUN_DEV" 2>/dev/null
            else
                echo "TUN: DOWN"
            fi
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status}"
            ;;
    esac
}

main "$@"
