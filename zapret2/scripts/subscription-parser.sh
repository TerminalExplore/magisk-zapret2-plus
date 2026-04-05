#!/system/bin/sh
##########################################################################################
# Subscription Parser
# Handles base64 subscriptions and VLESS URI parsing
##########################################################################################

MODDIR="${0%/*}/../.."
ZAPRET_DIR="$MODDIR/zapret2"
LOGFILE="/data/local/tmp/zapret2-subscription.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [SUB] $1" >> "$LOGFILE"
}

base64_decode() {
    local input="$1"
    if command -v base64 >/dev/null 2>&1; then
        echo "$input" | base64 -d 2>/dev/null
    else
        echo "$input" | tr 'A-Za-z0-9+/=' 'NOPQRSTUVWXYZABCDEFGHIJKLMnopqrstuvwxyzabcdefghijklm0123456789+/' | \
            sed 's/\([^\]\)=/\1/g' | sed 's/\\/=/=/g' 2>/dev/null
    fi
}

is_base64() {
    local input="$1"
    echo "$input" | grep -qE '^[A-Za-z0-9+/]+=*$' && echo "yes" || echo "no"
}

parse_vless_uri() {
    local uri="$1"
    
    log "Parsing VLESS URI: ${uri:0:50}..."
    
    uri="${uri#vless://}"
    
    local uuid="${uri%%@*}"
    local rest="${uri#*@}"
    local server="${rest%%:*}"
    local port="${rest#*:}"
    port="${port%%/*}"
    
    local params="${rest#*:}"
    params="${params#*/}"
    
    local flow=""
    local sni=""
    local tls="tls"
    
    OLDIFS="$IFS"
    IFS='&'
    for param in $params; do
        key="${param%%=*}"
        value="${param#*=}"
        case "$key" in
            flow) flow="$value" ;;
            sni) sni="$value" ;;
            tls) tls="$value" ;;
        esac
    done
    IFS="$OLDIFS"
    
    if [ -z "$sni" ]; then
        sni="$server"
    fi
    
    echo "VLESS:$uuid:$server:$port:$sni:$flow:$tls"
}

parse_singbox_json() {
    local json="$1"
    
    log "Parsing singbox JSON config..."
    
    local out_file="$ZAPRET_DIR/vpn-config.json"
    
    echo "$json" | sed 's/tun-in/listen: "0.0.0.0"\n        listenPort: "tun0"/g' > "$out_file"
    
    log "Config saved to $out_file"
}

import_subscription() {
    local url="$1"
    local out_file="$ZAPRET_DIR/vpn-config.json"
    local xray_file="$ZAPRET_DIR/xray-config.json"
    
    log "Importing subscription from: $url"
    
    local response
    response=$(curl -sL --connect-timeout 15 --max-time 30 \
        -H "User-Agent: Mozilla/5.0 (Android 14)" \
        -H "Accept: */*" \
        "$url" 2>/dev/null)
    
    if [ -z "$response" ]; then
        log "Failed to fetch subscription (empty response)"
        return 1
    fi
    
    local is_b64
    is_b64=$(is_base64 "$response")
    
    local content="$response"
    if [ "$is_b64" = "yes" ] && [ ${#response} -gt 100 ]; then
        log "Detected base64 encoded subscription"
        content=$(base64_decode "$response")
    fi
    
    if echo "$content" | grep -q '^{'; then
        log "Detected JSON format"
        echo "$content" > "$out_file"
        echo "$content" > "$xray_file"
        
        if echo "$content" | grep -q '"type": "vless"'; then
            log "Detected VLESS protocol"
        fi
    elif echo "$content" | grep -q 'vless://'; then
        log "Detected VLESS URIs - generating Xray config"
        echo "$content" > "$ZAPRET_DIR/vpn-subs-raw.txt"
        
        local first_uri=$(echo "$content" | grep -o 'vless://[^[:space:]]*' | head -1)
        if [ -n "$first_uri" ]; then
            generate_xray_config "$first_uri"
            cp "$ZAPRET_DIR/xray-config.json" "$out_file"
        fi
    elif echo "$content" | grep -q 'vmess://'; then
        log "Detected VMess URIs"
        echo "$content" > "$ZAPRET_DIR/vpn-subs-raw.txt"
    elif echo "$content" | grep -q 'ss://'; then
        log "Detected ShadowSocks URIs"
        echo "$content" > "$ZAPRET_DIR/vpn-subs-raw.txt"
    else
        log "Unknown format, saving raw"
        echo "$content" > "$ZAPRET_DIR/vpn-subs-raw.txt"
    fi
    
    log "Subscription imported successfully"
    return 0
}

parse_uri_list() {
    local file="$1"
    local out_file="$ZAPRET_DIR/vpn-config.json"
    
    if [ ! -f "$file" ]; then
        log "File not found: $file"
        return 1
    fi
    
    log "Parsing URIs from $file"
    
    local has_vless=0
    local has_ss=0
    local has_vmess=0
    
    while IFS= read -r line; do
        case "$line" in
            vless://*)
                has_vless=1
                parse_vless_uri "$line" | while IFS=: read -r proto uuid server port sni flow tls; do
                    log "VLESS: $server:$port (UUID: ${uuid:0:8}...)"
                done
                ;;
            ss://*)
                has_ss=1
                ;;
            vmess://*)
                has_vmess=1
                ;;
        esac
    done < "$file"
    
    if [ "$has_vless" = "1" ]; then
        log "Found VLESS URIs"
    fi
    
    return 0
}

generate_xray_config() {
    local uri="$1"
    local out_file="$ZAPRET_DIR/xray-config.json"
    
    uri="${uri#vless://}"
    
    local uuid="${uri%%@*}"
    local rest="${uri#*@}"
    local server="${rest%%:*}"
    local port="${rest#*:}"
    port="${port%%/*}"
    
    local params="${rest#*:}"
    params="${params#*/}"
    
    local flow=""
    local sni=""
    local tls="tls"
    local alpn=""
    local insecurity=""
    
    OLDIFS="$IFS"
    IFS='&'
    for param in $params; do
        key="${param%%=*}"
        value="${param#*=}"
        value=$(echo "$value" | sed 's/%3A/:/g' | sed 's/%2F/\//g')
        case "$key" in
            flow) flow="$value" ;;
            sni) sni="$value" ;;
            tls) [ "$value" = "none" ] && tls="" ;;
            alpn) alpn="$value" ;;
            allowInsecure) insecurity="$value" ;;
        esac
    done
    IFS="$OLDIFS"
    
    if [ -z "$sni" ]; then
        sni="$server"
    fi
    
    log "Generating Xray config for $server:$port"
    
    cat > "$out_file" << EOF
{
  "log": {
    "loglevel": "warn"
  },
  "inbounds": [
    {
      "tag": "tun-in",
      "protocol": "dokodemo-door",
      "listen": "0.0.0.0",
      "listenPacket": "tun0",
      "settings": {
        "address": "0.0.0.0",
        "port": 0,
        "network": "tcp,udp"
      }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "vless",
      "settings": {
        "vnext": [
          {
            "address": "$server",
            "port": $port,
            "users": [
              {
                "id": "$uuid",
                "encryption": "none"
                ${flow:+,}
                ${flow:+"flow": "$flow"}
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "tcp",
        "security": "${tls:-none}"
        ${tls:+,}
        ${tls:+"tlsSettings": {
          "serverName": "$sni"
          ${insecurity:+,}
          ${insecurity:+"allowInsecure": $insecurity}
        }}
      }
    },
    {
      "tag": "direct",
      "protocol": "freedom",
      "settings": {}
    }
  ],
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {
        "type": "field",
        "ip": ["geoip:private"],
        "outboundTag": "direct"
      }
    ]
  }
}
EOF

    log "Xray config generated: $out_file"
}

apply_vless() {
    local uri="$1"
    
    log "Applying VLESS URI..."
    
    case "$uri" in
        vless://*)
            generate_xray_config "$uri"
            cp "$ZAPRET_DIR/xray-config.json" "$ZAPRET_DIR/vpn-config.json"
            return 0
            ;;
    esac
    
    log "Invalid VLESS URI"
    return 1
}

case "$1" in
    import)
        import_subscription "$2"
        ;;
    parse)
        parse_uri_list "$2"
        ;;
    vless)
        apply_vless "$2"
        ;;
    *)
        echo "Usage: $0 {import|parse|vless} [args]"
        echo "  import <url>   - Import subscription from URL"
        echo "  parse <file>   - Parse URIs from file"
        echo "  vless <uri>    - Apply VLESS URI"
        ;;
esac
