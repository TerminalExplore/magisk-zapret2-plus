#!/system/bin/sh
##########################################################################################
# App Filter Script
# Manages per-app traffic routing using iptables owner match
##########################################################################################

MODDIR="${0%/*}/.."
ZAPRET_DIR="$MODDIR/zapret2"
LOGFILE="/data/local/tmp/zapret2-appfilter.log"
CONFIG_FILE="$ZAPRET_DIR/app-filter.ini"

log() {
    echo "[$(date '+%H:%M:%S')] [APPS] $1" >> "$LOGFILE"
}

load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        . "$CONFIG_FILE"
    fi
    
    WIFI_APPS="${WIFI_APPS:-}"
    MOBILE_APPS="${MOBILE_APPS:-}"
    WIFI_ENABLED="${WIFI_APP_FILTER:-0}"
    MOBILE_ENABLED="${MOBILE_APP_FILTER:-0}"
}

get_app_uid() {
    local package="$1"
    
    case "$package" in
        com.google.android.youtube)
            echo "10059"
            ;;
        com.discord)
            echo "10085"
            ;;
        com.telegram.messenger)
            echo "10148"
            ;;
        *)
            if command -v pm >/dev/null 2>&1; then
                local uid=$(pm list packages -U "$package" 2>/dev/null | grep -oP 'uid:\s*\K\d+' | head -1)
                [ -n "$uid" ] && echo "$uid" || echo ""
            else
                echo ""
            fi
            ;;
    esac
}

get_installed_apps() {
    if command -v pm >/dev/null 2>&1; then
        pm list packages -3 2>/dev/null | sed 's/package://' | sort
    fi
}

get_app_name() {
    local package="$1"
    
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
            local label=$(dumpsys package "$package" 2>/dev/null | grep "launchable-activity" | head -1 | sed "s/.*\.\(.*)\/.*/\1/")
            [ -n "$label" ] && echo "$label" || echo "$package"
            ;;
    esac
}

add_iptables_rules() {
    local mode="$1"
    local apps="$2"
    
    if [ -z "$apps" ]; then
        log "No apps specified for $mode mode"
        return 1
    fi
    
    local count=0
    
    for package in $apps; do
        local uid=$(get_app_uid "$package")
        
        if [ -z "$uid" ]; then
            log "Cannot get UID for $package, skipping"
            continue
        fi
        
        log "Adding rules for $package (UID: $uid)"
        
        case "$mode" in
            wifi)
                iptables -t mangle -A OUTPUT -m owner --uid-owner "$uid" \
                    -p tcp -j NFQUEUE --queue-num 200 --queue-bypass 2>/dev/null
                iptables -t mangle -A OUTPUT -m owner --uid-owner "$uid" \
                    -p udp --dport 443 -j NFQUEUE --queue-num 200 --queue-bypass 2>/dev/null
                ;;
            mobile)
                iptables -t mangle -A OUTPUT -m owner --uid-owner "$uid" \
                    -p tcp -j MARK --set-mark 1 2>/dev/null
                iptables -t mangle -A OUTPUT -m owner --uid-owner "$uid" \
                    -p udp --dport 443 -j MARK --set-mark 1 2>/dev/null
                ;;
        esac
        
        count=$((count + 1))
    done
    
    log "Added $count app rules for $mode mode"
}

remove_iptables_rules() {
    local mode="$1"
    
    local chain="ZAPRET_APPS"
    
    while iptables -t mangle -L "$chain" -n 2>/dev/null | grep -q "NFQUEUE\|MARK"; do
        iptables -t mangle -F "$chain" 2>/dev/null
        iptables -t mangle -X "$chain" 2>/dev/null
    done
    
    iptables -t mangle -D OUTPUT -m owner --uid-owner 10000-99999 \
        -p tcp -j "$chain" 2>/dev/null
    
    log "Removed app filter rules for $mode"
}

setup_wifi_apps() {
    log "Setting up WiFi app filter..."
    
    remove_iptables_rules "wifi"
    
    if [ "$WIFI_ENABLED" = "1" ] && [ -n "$WIFI_APPS" ]; then
        add_iptables_rules "wifi" "$WIFI_APPS"
    fi
}

setup_mobile_apps() {
    log "Setting up Mobile app filter..."
    
    remove_iptables_rules "mobile"
    
    if [ "$MOBILE_ENABLED" = "1" ] && [ -n "$MOBILE_APPS" ]; then
        add_iptables_rules "mobile" "$MOBILE_APPS"
    fi
}

list_apps() {
    log "Available apps with UIDs:"
    
    local common_apps="
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
        uid=$(get_app_uid "$package")
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

case "$1" in
    setup-wifi)
        load_config
        setup_wifi_apps
        ;;
    setup-mobile)
        load_config
        setup_mobile_apps
        ;;
    list)
        list_apps
        ;;
    show)
        load_config
        show_config
        ;;
    add-wifi)
        local app="$2"
        if [ -n "$app" ]; then
            sed -i "s/^WIFI_APPS=\"\\(.*\\)\"/WIFI_APPS=\"\\1 $app\"/" "$CONFIG_FILE"
            log "Added $app to WiFi whitelist"
        fi
        ;;
    add-mobile)
        local app="$2"
        if [ -n "$app" ]; then
            sed -i "s/^MOBILE_APPS=\"\\(.*\\)\"/MOBILE_APPS=\"\\1 $app\"/" "$CONFIG_FILE"
            log "Added $app to Mobile whitelist"
        fi
        ;;
    remove-wifi)
        local app="$2"
        if [ -n "$app" ]; then
            sed -i "s/ $app//g; s/^WIFI_APPS=\"$app //g" "$CONFIG_FILE"
            log "Removed $app from WiFi whitelist"
        fi
        ;;
    remove-mobile)
        local app="$2"
        if [ -n "$app" ]; then
            sed -i "s/ $app//g; s/^MOBILE_APPS=\"$app //g" "$CONFIG_FILE"
            log "Removed $app from Mobile whitelist"
        fi
        ;;
    *)
        echo "Usage: $0 {setup-wifi|setup-mobile|list|show|add-wifi|add-mobile|remove-wifi|remove-mobile}"
        echo ""
        echo "Commands:"
        echo "  setup-wifi     - Apply WiFi app filter rules"
        echo "  setup-mobile   - Apply Mobile app filter rules"
        echo "  list          - List available apps with UIDs"
        echo "  show          - Show current configuration"
        echo "  add-wifi APP  - Add app to WiFi whitelist"
        echo "  add-mobile APP - Add app to Mobile whitelist"
        echo "  remove-wifi APP - Remove app from WiFi whitelist"
        echo "  remove-mobile APP - Remove app from Mobile whitelist"
        ;;
esac
