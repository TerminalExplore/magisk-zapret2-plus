#!/system/bin/sh
##########################################################################################
# Subscription Parser
# Handles subscription downloads and direct Xray config generation.
##########################################################################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ZAPRET_DIR="$(dirname "$SCRIPT_DIR")"
LOGFILE="/data/local/tmp/zapret2-subscription.log"
OUTPUT_FILE="$ZAPRET_DIR/vpn-config.json"
XRAY_TEMPLATE_FILE="$ZAPRET_DIR/xray-config.json"
SERVER_LIST_FILE="$ZAPRET_DIR/vpn-servers.txt"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [SUB] $1" >> "$LOGFILE"
}

trim() {
    printf '%s' "$1" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

normalize_base64() {
    local input stripped remainder
    input="$1"
    stripped=$(printf '%s' "$input" | tr -d '\r\n\t ')
    stripped=$(printf '%s' "$stripped" | tr '_-' '/+')
    remainder=$((${#stripped} % 4))
    case "$remainder" in
        2) stripped="${stripped}==" ;;
        3) stripped="${stripped}=" ;;
    esac
    printf '%s' "$stripped"
}

base64_decode() {
    local normalized
    normalized="$(normalize_base64 "$1")"

    if command -v base64 >/dev/null 2>&1; then
        printf '%s' "$normalized" | base64 -d 2>/dev/null
        return $?
    fi

    if command -v busybox >/dev/null 2>&1; then
        printf '%s' "$normalized" | busybox base64 -d 2>/dev/null
        return $?
    fi

    return 1
}

is_base64() {
    local stripped
    stripped="$(printf '%s' "$1" | tr -d '\r\n\t ')"
    printf '%s' "$stripped" | grep -qE '^[A-Za-z0-9+/=_-]+$'
}

decode_query_value() {
    printf '%s' "$1" | sed \
        -e 's/%20/ /g' \
        -e 's/%2[Ff]/\//g' \
        -e 's/%3[Aa]/:/g' \
        -e 's/%3[Dd]/=/g' \
        -e 's/%2[Bb]/+/g' \
        -e 's/%2[Cc]/,/g' \
        -e 's/%23/#/g' \
        -e 's/%40/@/g'
}

decode_html_entities() {
    printf '%s' "$1" | sed \
        -e 's/&amp;/\&/g' \
        -e 's/&lt;/</g' \
        -e 's/&gt;/>/g' \
        -e 's/&quot;/"/g' \
        -e "s/&#39;/'/g"
}

copy_to_runtime_files() {
    local src="$1"
    if [ "$src" != "$OUTPUT_FILE" ]; then
        cp "$src" "$OUTPUT_FILE"
    fi
    if [ "$src" != "$XRAY_TEMPLATE_FILE" ]; then
        cp "$src" "$XRAY_TEMPLATE_FILE"
    fi
    chmod 0644 "$OUTPUT_FILE" "$XRAY_TEMPLATE_FILE" 2>/dev/null || true
}

write_xray_preamble() {
    cat <<'EOF'
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
EOF
}

write_xray_postamble() {
    cat <<'EOF'
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
        "ip": [
          "127.0.0.0/8",
          "10.0.0.0/8",
          "172.16.0.0/12",
          "192.168.0.0/16",
          "fc00::/7",
          "::1/128"
        ],
        "outboundTag": "direct"
      }
    ]
  }
}
EOF
}

write_vless_config() {
    local out_file="$1"
    local server="$2"
    local port="$3"
    local uuid="$4"
    local flow="$5"
    local sni="$6"
    local tls_mode="$7"
    local escaped_server escaped_uuid escaped_flow escaped_sni

    escaped_server="$(json_escape "$server")"
    escaped_uuid="$(json_escape "$uuid")"
    escaped_flow="$(json_escape "$flow")"
    escaped_sni="$(json_escape "$sni")"

    {
        write_xray_preamble
        cat <<EOF
    {
      "tag": "proxy",
      "protocol": "vless",
      "settings": {
        "vnext": [
          {
            "address": "$escaped_server",
            "port": $port,
            "users": [
              {
                "id": "$escaped_uuid",
                "encryption": "none"$( [ -n "$flow" ] && printf ',\n                "flow": "%s"' "$escaped_flow" )
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "tcp",
        "security": "$( [ -n "$tls_mode" ] && printf '%s' "$tls_mode" || printf 'none' )"$( [ -n "$tls_mode" ] && printf ',\n        "tlsSettings": {\n          "serverName": "%s"\n        }' "$escaped_sni" )
      }
    },
EOF
        write_xray_postamble
    } > "$out_file"
}

write_vmess_config() {
    local out_file="$1"
    local server="$2"
    local port="$3"
    local uuid="$4"
    local aid="$5"
    local network="$6"
    local host="$7"
    local path="$8"
    local tls_mode="$9"
    local escaped_server escaped_uuid escaped_host escaped_path

    escaped_server="$(json_escape "$server")"
    escaped_uuid="$(json_escape "$uuid")"
    escaped_host="$(json_escape "$host")"
    escaped_path="$(json_escape "$path")"
    [ -n "$network" ] || network="tcp"
    [ -n "$aid" ] || aid="0"

    {
        write_xray_preamble
        cat <<EOF
    {
      "tag": "proxy",
      "protocol": "vmess",
      "settings": {
        "vnext": [
          {
            "address": "$escaped_server",
            "port": $port,
            "users": [
              {
                "id": "$escaped_uuid",
                "alterId": $aid,
                "security": "auto"
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "$network",
        "security": "$( [ -n "$tls_mode" ] && printf '%s' "$tls_mode" || printf 'none' )"$( [ -n "$host$path" ] && printf ',\n        "wsSettings": {\n          "headers": {\n            "Host": "%s"\n          },\n          "path": "%s"\n        }' "$( [ -n "$host" ] && printf '%s' "$escaped_host" || printf '%s' "$escaped_server" )" "$( [ -n "$path" ] && printf '%s' "$escaped_path" || printf '/' )" )$( [ -n "$tls_mode" ] && printf ',\n        "tlsSettings": {\n          "serverName": "%s"\n        }' "$escaped_server" )
      }
    },
EOF
        write_xray_postamble
    } > "$out_file"
}

write_shadowsocks_config() {
    local out_file="$1"
    local server="$2"
    local port="$3"
    local method="$4"
    local password="$5"
    local escaped_server escaped_method escaped_password

    escaped_server="$(json_escape "$server")"
    escaped_method="$(json_escape "$method")"
    escaped_password="$(json_escape "$password")"

    {
        write_xray_preamble
        cat <<EOF
    {
      "tag": "proxy",
      "protocol": "shadowsocks",
      "settings": {
        "servers": [
          {
            "address": "$escaped_server",
            "port": $port,
            "method": "$escaped_method",
            "password": "$escaped_password"
          }
        ]
      }
    },
EOF
        write_xray_postamble
    } > "$out_file"
}

write_trojan_config() {
    local out_file="$1"
    local server="$2"
    local port="$3"
    local password="$4"
    local sni="$5"
    local tls_mode="$6"
    local escaped_server escaped_password escaped_sni

    escaped_server="$(json_escape "$server")"
    escaped_password="$(json_escape "$password")"
    escaped_sni="$(json_escape "$sni")"

    {
        write_xray_preamble
        cat <<EOF
    {
      "tag": "proxy",
      "protocol": "trojan",
      "settings": {
        "servers": [
          {
            "address": "$escaped_server",
            "port": $port,
            "password": "$escaped_password"
          }
        ]
      },
      "streamSettings": {
        "network": "tcp",
        "security": "$( [ -n "$tls_mode" ] && printf '%s' "$tls_mode" || printf 'none' )"$( [ -n "$tls_mode" ] && printf ',\n        "tlsSettings": {\n          "serverName": "%s"\n        }' "$escaped_sni" )
      }
    },
EOF
        write_xray_postamble
    } > "$out_file"
}

parse_vless_uri() {
    local uri without_proto query_part userinfo hostinfo uuid server port flow sni tls_mode param key value
    uri="$1"

    case "$uri" in
        vless://*) ;;
        *) return 1 ;;
    esac

    without_proto="${uri#vless://}"
    without_proto="$(decode_html_entities "$without_proto")"
    without_proto="${without_proto%%#*}"
    query_part="${without_proto#*\?}"
    [ "$query_part" = "$without_proto" ] && query_part=""
    without_proto="${without_proto%%\?*}"
    without_proto="${without_proto%%/*}"

    userinfo="${without_proto%%@*}"
    hostinfo="${without_proto#*@}"
    [ "$userinfo" = "$hostinfo" ] && return 1

    uuid="$userinfo"
    server="${hostinfo%%:*}"
    port="${hostinfo#*:}"
    [ "$server" = "$hostinfo" ] && port="443"
    [ -n "$port" ] || port="443"

    flow=""
    sni="$server"
    tls_mode="tls"

    if [ -n "$query_part" ]; then
        OLDIFS="$IFS"
        IFS='&'
        for param in $query_part; do
            key="${param%%=*}"
            value="${param#*=}"
            value="$(decode_query_value "$value")"
            case "$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]')" in
                flow) flow="$value" ;;
                sni) sni="$value" ;;
                tls) [ "$value" = "none" ] && tls_mode="" || tls_mode="$value" ;;
            esac
        done
        IFS="$OLDIFS"
    fi

    printf '%s|%s|%s|%s|%s|%s\n' "$uuid" "$server" "$port" "$flow" "$sni" "$tls_mode"
}

generate_xray_config() {
    local uri="$1"
    local parsed uuid server port flow sni tls_mode

    parsed="$(parse_vless_uri "$uri")" || return 1
    uuid="${parsed%%|*}"
    parsed="${parsed#*|}"
    server="${parsed%%|*}"
    parsed="${parsed#*|}"
    port="${parsed%%|*}"
    parsed="${parsed#*|}"
    flow="${parsed%%|*}"
    parsed="${parsed#*|}"
    sni="${parsed%%|*}"
    tls_mode="${parsed#*|}"

    [ -n "$uuid" ] || return 1
    [ -n "$server" ] || return 1

    log "Generating VLESS config for $server:$port"
    write_vless_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$uuid" "$flow" "$sni" "$tls_mode"
}

generate_vmess_config() {
    local uri="$1"
    local json_decoded server port uuid aid network host path tls_mode

    uri="${uri#vmess://}"
    json_decoded="$(base64_decode "$uri")" || return 1
    [ -n "$json_decoded" ] || return 1

    server="$(printf '%s' "$json_decoded" | sed -n 's/.*"add":"\([^"]*\)".*/\1/p' | head -1)"
    port="$(printf '%s' "$json_decoded" | sed -n 's/.*"port":"\{0,1\}\([0-9][0-9]*\)"\{0,1\}.*/\1/p' | head -1)"
    uuid="$(printf '%s' "$json_decoded" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)"
    aid="$(printf '%s' "$json_decoded" | sed -n 's/.*"aid":"\{0,1\}\([^"]*\)"\{0,1\}.*/\1/p' | head -1)"
    network="$(printf '%s' "$json_decoded" | sed -n 's/.*"net":"\([^"]*\)".*/\1/p' | head -1)"
    host="$(printf '%s' "$json_decoded" | sed -n 's/.*"host":"\([^"]*\)".*/\1/p' | head -1)"
    path="$(printf '%s' "$json_decoded" | sed -n 's/.*"path":"\([^"]*\)".*/\1/p' | head -1)"
    tls_mode="$(printf '%s' "$json_decoded" | sed -n 's/.*"tls":"\([^"]*\)".*/\1/p' | head -1)"

    [ -n "$server" ] || return 1
    [ -n "$port" ] || port="443"
    [ -n "$uuid" ] || return 1
    [ "$tls_mode" = "none" ] && tls_mode=""

    log "Generating VMess config for $server:$port"
    write_vmess_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$uuid" "$aid" "$network" "$host" "$path" "$tls_mode"
}

generate_ss_config() {
    local uri="$1"
    local without_scheme encoded_creds server_part server port decoded method password

    without_scheme="${uri#ss://}"
    without_scheme="${without_scheme%%#*}"

    if printf '%s' "$without_scheme" | grep -q '@'; then
        encoded_creds="${without_scheme%%@*}"
        server_part="${without_scheme#*@}"
    else
        return 1
    fi

    decoded="$(base64_decode "$encoded_creds")" || return 1
    method="${decoded%%:*}"
    password="${decoded#*:}"
    server="${server_part%%:*}"
    port="${server_part#*:}"
    port="${port%%/*}"

    [ -n "$server" ] || return 1
    [ -n "$port" ] || port="443"
    [ -n "$method" ] || return 1

    log "Generating Shadowsocks config for $server:$port"
    write_shadowsocks_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$method" "$password"
}

generate_trojan_uri_config() {
    local uri="$1"
    local without_proto query_part userinfo hostinfo password server port sni tls_mode param key value

    case "$uri" in
        trojan://*) ;;
        *) return 1 ;;
    esac

    without_proto="${uri#trojan://}"
    without_proto="$(decode_html_entities "$without_proto")"
    without_proto="${without_proto%%#*}"
    query_part="${without_proto#*\?}"
    [ "$query_part" = "$without_proto" ] && query_part=""
    without_proto="${without_proto%%\?*}"
    without_proto="${without_proto%%/*}"

    userinfo="${without_proto%%@*}"
    hostinfo="${without_proto#*@}"
    [ "$userinfo" = "$hostinfo" ] && return 1

    password="$userinfo"
    server="${hostinfo%%:*}"
    port="${hostinfo#*:}"
    [ "$server" = "$hostinfo" ] && port="443"
    [ -n "$port" ] || port="443"

    sni="$server"
    tls_mode="tls"

    if [ -n "$query_part" ]; then
        OLDIFS="$IFS"
        IFS='&'
        for param in $query_part; do
            key="${param%%=*}"
            value="${param#*=}"
            value="$(decode_query_value "$value")"
            case "$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]')" in
                sni) sni="$value" ;;
                security|tls) [ "$value" = "none" ] && tls_mode="" || tls_mode="$value" ;;
            esac
        done
        IFS="$OLDIFS"
    fi

    [ -n "$server" ] || return 1
    [ -n "$password" ] || return 1

    log "Generating Trojan config for $server:$port"
    write_trojan_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$password" "$sni" "$tls_mode"
}

apply_env_config() {
    local config_file="${1:-$ZAPRET_DIR/vpn-config.env}"
    local protocol server port sni

    [ -f "$config_file" ] || return 1
    # shellcheck disable=SC1090
    . "$config_file"

    protocol="$(printf '%s' "${VPN_PROTOCOL:-vless}" | tr '[:upper:]' '[:lower:]')"
    server="$(trim "${VPN_SERVER:-}")"
    port="$(trim "${VPN_PORT:-443}")"
    sni="$(trim "${VLESS_SNI:-${VPN_SERVER:-}}")"

    case "$protocol" in
        vless)
            [ -n "${VLESS_UUID:-}" ] || return 1
            [ -n "$server" ] || return 1
            write_vless_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$VLESS_UUID" "${VLESS_FLOW:-}" "${sni:-$server}" "${VLESS_TLS:-tls}"
            ;;
        ss|shadowsocks)
            [ -n "${SS_PASSWORD:-}" ] || return 1
            [ -n "$server" ] || return 1
            write_shadowsocks_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "${SS_METHOD:-aes-256-gcm}" "$SS_PASSWORD"
            ;;
        vmess)
            [ -n "${VMESS_UUID:-}" ] || return 1
            [ -n "$server" ] || return 1
            write_vmess_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$VMESS_UUID" "${VMESS_ALTER_ID:-0}" "${VMESS_NETWORK:-tcp}" "${VMESS_HOST:-}" "${VMESS_PATH:-/}" "${VMESS_TLS:-}"
            ;;
        trojan)
            [ -n "${TROJAN_PASSWORD:-}" ] || return 1
            [ -n "$server" ] || return 1
            write_trojan_config "$XRAY_TEMPLATE_FILE" "$server" "$port" "$TROJAN_PASSWORD" "${TROJAN_SNI:-${sni:-$server}}" "${TROJAN_TLS:-tls}"
            ;;
        *)
            return 1
            ;;
    esac

    copy_to_runtime_files "$XRAY_TEMPLATE_FILE"
    log "Generated direct VPN config from $config_file"
}

fetch_subscription() {
    local url="$1"

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --connect-timeout 15 --max-time 30 \
            -H "User-Agent: Mozilla/5.0 (Android 14)" \
            -H "Accept: */*" \
            "$url" 2>/dev/null
        return $?
    fi

    if command -v wget >/dev/null 2>&1; then
        wget -qO- --timeout=30 "$url" 2>/dev/null
        return $?
    fi

    return 1
}

import_subscription() {
    local url="$1"
    local response

    [ -n "$url" ] || return 1
    log "Importing subscription from: $url"

    response="$(fetch_subscription "$url")" || response=""
    process_subscription_content "$response"
}

process_subscription_content() {
    local response="$1"
    local stripped content first_uri preview

    [ -n "$response" ] || return 1

    stripped="$(printf '%s' "$response" | tr -d '\r')"
    content="$stripped"

    if is_base64 "$stripped" && [ ${#stripped} -gt 64 ]; then
        content="$(base64_decode "$stripped")" || content="$stripped"
    fi

    printf '%s\n' "$content" > "$ZAPRET_DIR/vpn-subs-raw.txt"

    case "$(printf '%s' "$content" | sed -n '/[^[:space:]]/ {s/^\(.\).*$/\1/p; q;}')" in
        '{'|'[')
            printf '%s\n' "$content" > "$OUTPUT_FILE"
            printf '%s\n' "$content" > "$XRAY_TEMPLATE_FILE"
            chmod 0644 "$OUTPUT_FILE" "$XRAY_TEMPLATE_FILE" 2>/dev/null || true
            log "Detected JSON subscription"
            return 0
            ;;
    esac

    # Save full server list for fallback use
    printf '%s\n' "$content" | grep -E '^(vless|vmess|ss|trojan)://' | while IFS= read -r uri; do
        decode_html_entities "$uri"
    done > "$SERVER_LIST_FILE" 2>/dev/null
    local total
    total=$(wc -l < "$SERVER_LIST_FILE" 2>/dev/null || echo 0)
    log "Saved $total server(s) to $SERVER_LIST_FILE"

    # Try each server in order until one succeeds
    while IFS= read -r first_uri || [ -n "$first_uri" ]; do
        [ -n "$first_uri" ] || continue
        case "$first_uri" in
            vless://*)
                generate_xray_config "$first_uri" && copy_to_runtime_files "$XRAY_TEMPLATE_FILE" && return 0
                log "Server failed: $first_uri, trying next..."
                ;;
            vmess://*)
                generate_vmess_config "$first_uri" && copy_to_runtime_files "$XRAY_TEMPLATE_FILE" && return 0
                log "Server failed: $first_uri, trying next..."
                ;;
            ss://*)
                generate_ss_config "$first_uri" && copy_to_runtime_files "$XRAY_TEMPLATE_FILE" && return 0
                log "Server failed: $first_uri, trying next..."
                ;;
            trojan://*)
                generate_trojan_uri_config "$first_uri" && copy_to_runtime_files "$XRAY_TEMPLATE_FILE" && return 0
                log "Server failed: $first_uri, trying next..."
                ;;
        esac
    done < "$SERVER_LIST_FILE"

    preview="$(printf '%s' "$content" | head -n 3 | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-200)"
    log "No usable server found in subscription. Preview: $preview"
    return 1
}

import_subscription_file() {
    local file="$1"
    local response

    [ -f "$file" ] || return 1
    log "Importing subscription from file: $file"
    response="$(cat "$file" 2>/dev/null)" || response=""
    process_subscription_content "$response"
}

import_subscription_b64() {
    local payload="$1"
    local response

    [ -n "$payload" ] || return 1
    log "Importing subscription from base64 payload"
    response="$(base64_decode "$payload")" || response=""
    process_subscription_content "$response"
}

parse_uri_list() {
    local file="$1"
    local count=0

    [ -f "$file" ] || return 1

    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            vless://*|vmess://*|ss://*|trojan://*)
                count=$((count + 1))
                ;;
        esac
    done < "$file"

    log "Found $count supported URI(s) in $file"
    [ "$count" -gt 0 ]
}

apply_vless() {
    local uri="$1"
    generate_xray_config "$uri" || return 1
    copy_to_runtime_files "$XRAY_TEMPLATE_FILE"
}

apply_uri() {
    local uri="$1"

    case "$uri" in
        vless://*)
            apply_vless "$uri"
            ;;
        vmess://*)
            generate_vmess_config "$uri" || return 1
            copy_to_runtime_files "$XRAY_TEMPLATE_FILE"
            ;;
        ss://*)
            generate_ss_config "$uri" || return 1
            copy_to_runtime_files "$XRAY_TEMPLATE_FILE"
            ;;
        trojan://*)
            generate_trojan_uri_config "$uri" || return 1
            copy_to_runtime_files "$XRAY_TEMPLATE_FILE"
            ;;
        *)
            return 1
            ;;
    esac
}

case "$1" in
    import)
        import_subscription "$2"
        ;;
    import-file)
        import_subscription_file "$2"
        ;;
    import-b64)
        import_subscription_b64 "$2"
        ;;
    parse)
        parse_uri_list "$2"
        ;;
    vless)
        apply_vless "$2"
        ;;
    uri)
        apply_uri "$2"
        ;;
    env)
        apply_env_config "$2"
        ;;
    *)
        echo "Usage: $0 {import|import-file|import-b64|parse|vless|uri|env} [args]"
        echo "  import <url>         - Import subscription from URL"
        echo "  import-file <file>   - Import subscription content from file"
        echo "  import-b64 <base64>  - Import subscription content from base64 payload"
        echo "  parse <file>         - Count supported URIs in file"
        echo "  vless <uri>          - Apply VLESS URI"
        echo "  uri <uri>            - Apply any supported URI"
        echo "  env [config_file]    - Generate config from vpn-config.env"
        exit 1
        ;;
esac
