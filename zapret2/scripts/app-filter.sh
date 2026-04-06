#!/system/bin/sh
##########################################################################################
# App Filter Script
# Manages per-app routing rules using owner matches.
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
. "$SCRIPT_DIR/common.sh"

LOGFILE="/data/local/tmp/zapret2-appfilter.log"
CONFIG_FILE="$ZAPRET_DIR/app-filter.ini"
WIFI_CHAIN="ZAPRET_APPS_WIFI"
MOBILE_CHAIN="ZAPRET_APPS_MOBILE"
VPN_MARK="${VPN_MARK:-1}"

log() {
    echo "[$(date '+%H:%M:%S')] [APPS] $1" >> "$LOGFILE"
}

load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        # shellcheck disable=SC1090
        . "$CONFIG_FILE"
    fi

    WIFI_APPS="${WIFI_APPS:-}"
    MOBILE_APPS="${MOBILE_APPS:-}"
    WIFI_ENABLED="${WIFI_APP_FILTER:-0}"
    MOBILE_ENABLED="${MOBILE_APP_FILTER:-0}"
}

get_app_uid() {
    local package="$1"
    local uid=""

    case "$package" in
        com.google.android.youtube) echo "10059"; return 0 ;;
        com.discord) echo "10085"; return 0 ;;
        com.telegram.messenger) echo "10148"; return 0 ;;
    esac

    if command -v pm >/dev/null 2>&1; then
        uid="$(pm list packages -U "$package" 2>/dev/null | sed -n 's/.*uid:\([0-9][0-9]*\).*/\1/p' | head -1)"
        [ -n "$uid" ] && printf '%s\n' "$uid"
    fi
}

get_installed_apps() {
    if command -v pm >/dev/null 2>&1; then
        pm list packages -3 2>/dev/null | sed 's/package://' | sort
    fi
}

get_app_name() {
    local package="$1"
    local label=""

    case "$package" in
        com.google.android.youtube) echo "YouTube" ;;
        com.google.android.apps.youtube.music) echo "YouTube Music" ;;
        com.discord) echo "Discord" ;;
        com.discord.staff) echo "Discord (Beta)" ;;
        com.telegram.messenger) echo "Telegram" ;;
        com.telegram.messenger.web) echo "Telegram Web" ;;
        org.telegram.plus) echo "TurboTP" ;;
        com.whatsapp) echo "WhatsApp" ;;
        com.instagram.android) echo "Instagram" ;;
        com.facebook.katana) echo "Facebook" ;;
        com.twitter.android) echo "Twitter/X" ;;
        com.netflix.mediaclient) echo "Netflix" ;;
        com.spotify.music) echo "Spotify" ;;
        com.google.android.apps.googlevoice) echo "Google Voice" ;;
        com.ubercab) echo "Uber" ;;
        *)
            label="$(dumpsys package "$package" 2>/dev/null | sed -n 's/.*label=\(.*\)$/\1/p' | head -1)"
            [ -n "$label" ] && echo "$label" || echo "$package"
            ;;
    esac
}

ensure_chain() {
    local chain="$1"

    iptables -t mangle -N "$chain" 2>/dev/null || true
    iptables -t mangle -F "$chain" 2>/dev/null || true
    iptables -t mangle -D OUTPUT -j "$chain" 2>/dev/null || true
    iptables -t mangle -A OUTPUT -j "$chain" 2>/dev/null
}

remove_chain() {
    local chain="$1"

    iptables -t mangle -D OUTPUT -j "$chain" 2>/dev/null || true
    iptables -t mangle -F "$chain" 2>/dev/null || true
    iptables -t mangle -X "$chain" 2>/dev/null || true
}

add_iptables_rules() {
    local mode="$1"
    local apps="$2"
    local chain="$3"
    local count=0
    local package uid

    [ -n "$apps" ] || return 1

    ensure_chain "$chain"

    for package in $apps; do
        uid="$(get_app_uid "$package")"
        if [ -z "$uid" ]; then
            log "Cannot get UID for $package, skipping"
            continue
        fi

        log "Adding rules for $package (UID: $uid)"

        case "$mode" in
            wifi)
                iptables -t mangle -A "$chain" -m owner --uid-owner "$uid" \
                    -p tcp -j NFQUEUE --queue-num "$QNUM" --queue-bypass 2>/dev/null
                iptables -t mangle -A "$chain" -m owner --uid-owner "$uid" \
                    -p udp --dport 443 -j NFQUEUE --queue-num "$QNUM" --queue-bypass 2>/dev/null
                ;;
            mobile)
                iptables -t mangle -A "$chain" -m owner --uid-owner "$uid" \
                    -p tcp -j MARK --set-mark "$VPN_MARK" 2>/dev/null
                iptables -t mangle -A "$chain" -m owner --uid-owner "$uid" \
                    -p udp --dport 443 -j MARK --set-mark "$VPN_MARK" 2>/dev/null
                ;;
        esac

        count=$((count + 1))
    done

    log "Added $count app rules for $mode mode"
}

remove_iptables_rules() {
    local mode="$1"

    case "$mode" in
        wifi) remove_chain "$WIFI_CHAIN" ;;
        mobile) remove_chain "$MOBILE_CHAIN" ;;
        all)
            remove_chain "$WIFI_CHAIN"
            remove_chain "$MOBILE_CHAIN"
            ;;
    esac

    log "Removed app filter rules for $mode"
}

setup_wifi_apps() {
    log "Setting up WiFi app filter..."
    remove_iptables_rules "wifi"

    if [ "$WIFI_ENABLED" = "1" ] && [ -n "$WIFI_APPS" ]; then
        add_iptables_rules "wifi" "$WIFI_APPS" "$WIFI_CHAIN"
    fi
}

setup_mobile_apps() {
    log "Setting up Mobile app filter..."
    remove_iptables_rules "mobile"

    if [ "$MOBILE_ENABLED" = "1" ] && [ -n "$MOBILE_APPS" ]; then
        add_iptables_rules "mobile" "$MOBILE_APPS" "$MOBILE_CHAIN"
    fi
}

list_apps() {
    local common_apps package name uid

    common_apps="
com.google.android.youtube:YouTube
com.google.android.apps.youtube.music:YouTube Music
com.discord:Discord
com.telegram.messenger:Telegram
com.whatsapp:WhatsApp
com.instagram.android:Instagram
com.facebook.katana:Facebook
com.twitter.android:Twitter
com.netflix.mediaclient:Netflix
com.spotify.music:Spotify
"

    for entry in $common_apps; do
        package="${entry%%:*}"
        name="${entry#*:}"
        uid="$(get_app_uid "$package")"
        if [ -n "$uid" ]; then
            echo "  $name ($package): UID=$uid"
        fi
    done
}

show_config() {
    echo "=== App Filter Configuration ==="
    echo ""
    echo "WiFi App Filter: ${WIFI_ENABLED:-0}"
    echo "WiFi Apps: ${WIFI_APPS:-none}"
    echo ""
    echo "Mobile App Filter: ${MOBILE_ENABLED:-0}"
    echo "Mobile Apps: ${MOBILE_APPS:-none}"
}

append_package() {
    local key="$1"
    local package="$2"
    local current updated

    current="$(sed -n "s/^${key}=\"\\(.*\\)\"/\\1/p" "$CONFIG_FILE" | head -1)"
    case " $current " in
        *" $package "*) return 0 ;;
    esac

    updated="$(trim "$current $package")"
    sed -i "s|^${key}=.*|${key}=\"${updated}\"|" "$CONFIG_FILE"
}

remove_package() {
    local key="$1"
    local package="$2"
    local current updated

    current="$(sed -n "s/^${key}=\"\\(.*\\)\"/\\1/p" "$CONFIG_FILE" | head -1)"
    updated="$(printf ' %s ' "$current" | sed "s| ${package} | |g")"
    updated="$(trim "$updated")"
    sed -i "s|^${key}=.*|${key}=\"${updated}\"|" "$CONFIG_FILE"
}

case "$1" in
    setup-wifi)
        load_config
        setup_wifi_apps
        ;;
    setup-mobile)
        load_config
        setup_mobile_apps
        ;;
    clear)
        remove_iptables_rules "all"
        ;;
    list)
        list_apps
        ;;
    show)
        load_config
        show_config
        ;;
    add-wifi)
        if [ -n "$2" ]; then
            append_package "WIFI_APPS" "$2"
            log "Added $2 to WiFi whitelist"
        fi
        ;;
    add-mobile)
        if [ -n "$2" ]; then
            append_package "MOBILE_APPS" "$2"
            log "Added $2 to Mobile whitelist"
        fi
        ;;
    remove-wifi)
        if [ -n "$2" ]; then
            remove_package "WIFI_APPS" "$2"
            log "Removed $2 from WiFi whitelist"
        fi
        ;;
    remove-mobile)
        if [ -n "$2" ]; then
            remove_package "MOBILE_APPS" "$2"
            log "Removed $2 from Mobile whitelist"
        fi
        ;;
    *)
        echo "Usage: $0 {setup-wifi|setup-mobile|clear|list|show|add-wifi|add-mobile|remove-wifi|remove-mobile}"
        echo ""
        echo "Commands:"
        echo "  setup-wifi       - Apply WiFi app filter rules"
        echo "  setup-mobile     - Apply Mobile app filter rules"
        echo "  clear            - Remove all app filter rules"
        echo "  list             - List available apps with UIDs"
        echo "  show             - Show current configuration"
        echo "  add-wifi APP     - Add app to WiFi whitelist"
        echo "  add-mobile APP   - Add app to Mobile whitelist"
        echo "  remove-wifi APP  - Remove app from WiFi whitelist"
        echo "  remove-mobile APP - Remove app from Mobile whitelist"
        exit 1
        ;;
esac
