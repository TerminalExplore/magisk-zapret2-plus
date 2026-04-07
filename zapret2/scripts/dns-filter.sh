#!/system/bin/sh
##########################################################################################
# Per-App DNS Filter
# Redirects DNS queries from specific apps to custom DNS servers
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
DNS_CONFIG="$ZAPRET_DIR/dns-filter.ini"
LOGFILE="/data/local/tmp/zapret2-dns-filter.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DNS-FILTER] $1" >> "$LOGFILE"
}

flush_dns_rules() {
    log "Flushing DNS filter rules..."
    
    # Remove rules by comment
    for chain in OUTPUT; do
        local rules
        rules=$(iptables -t nat -L $chain -n --line-numbers 2>/dev/null | grep -E "DNS-FILTER-[0-9]+" | awk '{print $1}' | sort -rn)
        for num in $rules; do
            iptables -t nat -D $chain $num 2>/dev/null || true
        done
    done
    
    log "DNS rules flushed"
}

apply_dns_rule() {
    local uid="$1"
    local dns="$2"
    local priority="$3"
    
    [ -z "$uid" ] || [ -z "$dns" ] && return 1
    
    log "Applying DNS rule: UID=$uid -> $dns"
    
    # UDP DNS
    iptables -t nat -A OUTPUT \
        -m owner --uid-owner "$uid" \
        -p udp --dport 53 \
        -j DNAT --to-destination "${dns}:53" \
        -m comment --comment "DNS-FILTER-${priority}" 2>/dev/null || true
        
    # TCP DNS
    iptables -t nat -A OUTPUT \
        -m owner --uid-owner "$uid" \
        -p tcp --dport 53 \
        -j DNAT --to-destination "${dns}:53" \
        -m comment --comment "DNS-FILTER-${priority}" 2>/dev/null || true
}

get_app_uid() {
    local package="$1"
    local uid
    
    uid=$(dumpsys package "$package" 2>/dev/null | grep "userId=" | head -1 | sed 's/.*userId=\([0-9]*\).*/\1/')
    
    if [ -z "$uid" ]; then
        uid=$(pm list packages -U "$package" 2>/dev/null | grep -oP 'uid:\s*\K\d+' | head -1)
    fi
    
    echo "$uid"
}

parse_config() {
    local config_file="${1:-$DNS_CONFIG}"
    
    [ -f "$config_file" ] || {
        log "Config not found: $config_file"
        return 1
    }
    
    log "Parsing config: $config_file"
    
    local line_num=0
    local current_section=""
    local current_dns=""
    
    while IFS= read -r line || [ -n "$line" ]; do
        line_num=$((line_num + 1))
        
        # Trim whitespace
        line=$(echo "$line" | tr -d ' \t')
        
        # Skip empty lines and comments
        [ -z "$line" ] && continue
        [ "${line#\#}" != "$line" ] && continue
        [ "${line#;}" != "$line" ] && continue
        
        # Section headers
        case "$line" in
            \[*\])
                current_section="${line#[}"; current_section="${current_section%]}"
                current_dns=""
                continue
                ;;
        esac
        
        # Skip if no section
        [ -z "$current_section" ] && continue
        
        # Check if line looks like an IP address
        case "$line" in
            *.*.*.*)
                # This is a DNS IP
                current_dns="$line"
                ;;
        esac
        
        # Handle sections
        case "$current_section" in
            app:*)
                local package="${current_section#app:}"
                local uid
                
                # Only apply if we have both package and DNS
                if [ -n "$package" ] && [ -n "$current_dns" ]; then
                    uid=$(get_app_uid "$package")
                    
                    if [ -n "$uid" ]; then
                        apply_dns_rule "$uid" "$current_dns" "$line_num"
                    else
                        log "Warning: Could not find UID for $package"
                    fi
                fi
                ;;
        esac
        
    done < "$config_file"
    
    log "DNS filter applied"
}

status() {
    log "Current DNS filter rules:"
    iptables -t nat -L OUTPUT -n --line-numbers 2>/dev/null | grep "DNS-FILTER" || echo "No DNS filter rules"
}

case "${1:-}" in
    start|apply)
        flush_dns_rules
        parse_config "$2"
        ;;
    stop|flush)
        flush_dns_rules
        ;;
    status)
        status
        ;;
    reload)
        parse_config "$2"
        ;;
    *)
        echo "Usage: $0 {start|stop|reload|status}"
        echo "  start     - Apply DNS filter rules"
        echo "  stop      - Flush all DNS filter rules"
        echo "  reload    - Reload config"
        echo "  status    - Show current rules"
        exit 1
        ;;
esac
