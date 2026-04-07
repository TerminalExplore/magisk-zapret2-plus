#!/bin/bash
##########################################################################################
# Zapret2 Control Script for Linux (systemd compatible)
# Usage: ./zapret2.sh {start|stop|status|restart|reload}
##########################################################################################

set -euo pipefail

ZAPRET_DIR="${ZAPRET_DIR:-/opt/zapret2}"
LOG_DIR="${LOG_DIR:-/var/log/zapret2}"
PIDFILE="$ZAPRET_DIR/zapret2.pid"
CONFIG="$ZAPRET_DIR/config.sh"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [SYSTEMD] $1"
    logger -t zapret2 "$1" 2>/dev/null || true
}

is_running() {
    [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null
}

start() {
    if is_running; then
        log "Already running (PID: $(cat $PIDFILE))"
        return 0
    fi

    mkdir -p "$LOG_DIR" 2>/dev/null || true
    
    if [ ! -f "$CONFIG" ]; then
        log "Config not found: $CONFIG"
        return 1
    fi

    log "Starting Zapret2..."
    
    "$ZAPRET_DIR/zapret2.sh" start &
    local pid=$!
    
    echo $pid > "$PIDFILE"
    sleep 1
    
    if is_running; then
        log "Started successfully (PID: $pid)"
        return 0
    else
        rm -f "$PIDFILE"
        log "Failed to start"
        return 1
    fi
}

stop() {
    if ! is_running; then
        log "Not running"
        rm -f "$PIDFILE"
        return 0
    fi

    log "Stopping Zapret2..."
    local pid=$(cat "$PIDFILE")
    
    kill -TERM "$pid" 2>/dev/null || true
    
    local count=0
    while is_running && [ $count -lt 30 ]; do
        sleep 1
        count=$((count + 1))
    done
    
    if is_running; then
        log "Force killing..."
        kill -KILL "$pid" 2>/dev/null || true
    fi
    
    rm -f "$PIDFILE"
    log "Stopped"
}

status() {
    if is_running; then
        echo "Running (PID: $(cat $PIDFILE))"
        return 0
    else
        echo "Stopped"
        return 1
    fi
}

restart() {
    stop
    sleep 2
    start
}

reload() {
    if is_running; then
        log "Reloading..."
        kill -HUP "$(cat "$PIDFILE")" 2>/dev/null || true
    else
        start
    fi
}

case "${1:-}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        restart
        ;;
    reload)
        reload
        ;;
    *)
        echo "Usage: $0 {start|stop|status|restart|reload}"
        exit 1
        ;;
esac
