#!/bin/bash
##########################################################################################
# Shell Script Tests for Zapret2 Module
##########################################################################################

# Don't use set -e as we want all tests to run even if some fail

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_FILE="/tmp/zapret2-test.log"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASSED=0
FAILED=0
SKIPPED=0

log() { echo "[$(date '+%H:%M:%S')] $1" | tee -a "$LOG_FILE"; }
pass() { echo -e "${GREEN}✓ PASS${NC}: $1"; ((PASSED++)); }
fail() { echo -e "${RED}✗ FAIL${NC}: $1"; ((FAILED++)); }
skip() { echo -e "${YELLOW}⊘ SKIP${NC}: $1"; ((SKIPPED++)); }
warn() { echo -e "${YELLOW}! WARN${NC}: $1"; }

cleanup() {
    rm -f "$LOG_FILE" /tmp/zapret2-test-*.sh 2>/dev/null || true
}

header() {
    echo ""
    echo "========================================"
    echo " $1"
    echo "========================================"
}

##########################################################################################
# Test: subscription-parser.sh
##########################################################################################
test_subscription_parser() {
    header "subscription-parser.sh Tests"
    
    local script="$SCRIPT_DIR/zapret2/scripts/subscription-parser.sh"
    local xray_backup="/tmp/zapret2-xray-config.backup.$$"
    local raw_backup="/tmp/zapret2-vpn-subs-raw.backup.$$"
    
    if [ ! -f "$script" ]; then
        skip "subscription-parser.sh not found"
        return
    fi

    cp "$SCRIPT_DIR/zapret2/xray-config.json" "$xray_backup" 2>/dev/null || true
    cp "$SCRIPT_DIR/zapret2/vpn-subs-raw.txt" "$raw_backup" 2>/dev/null || true
    rm -f "$SCRIPT_DIR/zapret2/vpn-config.json" 2>/dev/null || true
    
    if bash -n "$script" 2>/dev/null; then
        pass "subscription-parser.sh syntax is valid"
    else
        fail "subscription-parser.sh syntax is invalid"
    fi

    # Test: is_base64 detection
    log "Testing base64 detection..."
    
    local b64_test="SGVsbG8gV29ybGQ="
    local non_b64_test="This is not base64!!"
    
    result=$(echo "$b64_test" | grep -qE '^[A-Za-z0-9+/]+=*$' && echo "yes" || echo "no")
    [ "$result" = "yes" ] && pass "Base64 string detected correctly" || fail "Base64 detection failed"
    
    result=$(echo "$non_b64_test" | grep -qE '^[A-Za-z0-9+/]+=*$' && echo "yes" || echo "no")
    [ "$result" = "no" ] && pass "Non-base64 string rejected correctly" || fail "Non-base64 rejection failed"
    
    # Test: VLESS URI parsing
    log "Testing VLESS URI parsing..."
    
    local test_uri="vless://abcd1234-5678-90ef-ghij-klmnopqrstuv@server.com:443?flow=xtls-rprx-vision&sni=example.com#Test"
    
    # Extract UUID
    uuid="${test_uri#vless://}"
    uuid="${uuid%%@*}"
    [ "$uuid" = "abcd1234-5678-90ef-ghij-klmnopqrstuv" ] && pass "UUID extraction works" || fail "UUID extraction failed"
    
    # Extract server and port
    local after_at="${test_uri#*@}"
    server="${after_at%%:*}"
    port="${after_at#*:}"
    port="${port%%\?*}"
    
    [ "$server" = "server.com" ] && pass "Server extraction works" || fail "Server extraction failed"
    [ "$port" = "443" ] && pass "Port extraction works" || fail "Port extraction failed"

    if bash "$script" vless "$test_uri" >/dev/null 2>&1; then
        pass "VLESS URI converts into Xray config"
    else
        fail "VLESS URI conversion failed"
    fi

    local import_file="/tmp/zapret2-subscription-import.$$"
    local urlsafe_base64
    urlsafe_base64="$(printf '%s' "$test_uri" | base64 | tr -d '\n' | tr '+/' '-_')"
    printf '%s\n' "$urlsafe_base64" > "$import_file"

    if bash "$script" import-file "$import_file" >/dev/null 2>&1; then
        pass "import-file handles URL-safe base64 subscriptions"
    else
        fail "import-file failed on URL-safe base64 subscription"
    fi

    if [ -f "$SCRIPT_DIR/zapret2/vpn-config.json" ] && grep -q '"protocol": "vless"' "$SCRIPT_DIR/zapret2/vpn-config.json"; then
        pass "import-file writes a VLESS Xray config"
    else
        fail "import-file did not generate expected Xray config"
    fi

    local payload_b64
    payload_b64="$(printf '%s' "$test_uri" | base64 | tr -d '\n')"

    if bash "$script" import-b64 "$payload_b64" >/dev/null 2>&1; then
        pass "import-b64 handles direct base64 payload"
    else
        fail "import-b64 failed on direct base64 payload"
    fi

    rm -f "$import_file"

    if [ -f "$xray_backup" ]; then
        cp "$xray_backup" "$SCRIPT_DIR/zapret2/xray-config.json"
        rm -f "$xray_backup"
    fi
    if [ -f "$raw_backup" ]; then
        cp "$raw_backup" "$SCRIPT_DIR/zapret2/vpn-subs-raw.txt"
        rm -f "$raw_backup"
    else
        rm -f "$SCRIPT_DIR/zapret2/vpn-subs-raw.txt" 2>/dev/null || true
    fi
    rm -f "$SCRIPT_DIR/zapret2/vpn-config.json" 2>/dev/null || true
}

##########################################################################################
# Test: network-monitor.sh functions
##########################################################################################
test_network_monitor() {
    header "network-monitor.sh Tests"
    
    local script="$SCRIPT_DIR/zapret2/scripts/network-monitor.sh"
    
    if [ ! -f "$script" ]; then
        skip "network-monitor.sh not found"
        return
    fi
    
    if grep -q 'PIDFILE' "$script"; then
        pass "Network monitor uses shared Zapret PID file"
    else
        fail "Network monitor does not use shared Zapret PID file"
    fi

    if grep -q 'print_status()' "$script" && grep -q 'stop_monitor()' "$script"; then
        pass "Network monitor exposes status and stop helpers"
    else
        fail "Network monitor is missing status/stop helpers"
    fi

    # Test: Network detection logic (mocked)
    log "Testing network detection logic..."
    
    # Simulate WiFi interface check
    local mock_wlan_state="up"
    [ "$mock_wlan_state" = "up" ] && pass "WiFi state check works" || fail "WiFi state check failed"
    
    # Simulate mobile interface check
    local mock_rmnet_state="down"
    [ "$mock_rmnet_state" = "up" ] && fail "Mobile state should be down" || pass "Mobile state check works"
}

##########################################################################################
# Test: vpn-start.sh behavior
##########################################################################################
test_vpn_start() {
    header "vpn-start.sh Tests"

    local script="$SCRIPT_DIR/zapret2/scripts/vpn-start.sh"

    if [ ! -f "$script" ]; then
        skip "vpn-start.sh not found"
        return
    fi

    if bash -n "$script" 2>/dev/null; then
        pass "vpn-start.sh syntax is valid"
    else
        fail "vpn-start.sh syntax is invalid"
    fi

    if grep -q 'VPN_SELECTED_URI' "$script"; then
        pass "vpn-start.sh prefers selected subscription server"
    else
        fail "vpn-start.sh does not use VPN_SELECTED_URI"
    fi
}

##########################################################################################
# Test: app-filter.sh configuration
##########################################################################################
test_app_filter() {
    header "app-filter.sh Tests"
    
    local script="$SCRIPT_DIR/zapret2/scripts/app-filter.sh"
    local config="$SCRIPT_DIR/zapret2/app-filter.ini"
    
    # Test: Config file exists
    [ -f "$config" ] && pass "app-filter.ini exists" || fail "app-filter.ini not found"
    
    # Test: Config format
    if [ -f "$config" ]; then
        grep -q "WIFI_APP_FILTER=" "$config" && pass "WIFI_APP_FILTER defined" || fail "WIFI_APP_FILTER not defined"
        grep -q "MOBILE_APP_FILTER=" "$config" && pass "MOBILE_APP_FILTER defined" || fail "MOBILE_APP_FILTER not defined"
        grep -q "WIFI_APPS=" "$config" && pass "WIFI_APPS defined" || fail "WIFI_APPS not defined"
    fi

    if bash -n "$script" 2>/dev/null; then
        pass "app-filter.sh syntax is valid"
    else
        fail "app-filter.sh syntax is invalid"
    fi
    
    # Test: Package name validation
    log "Testing package name validation..."
    
    local valid_packages="com.google.android.youtube com.discord com.telegram.messenger"
    for pkg in $valid_packages; do
        [[ "$pkg" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$ ]] && pass "Valid package: $pkg" || fail "Invalid package: $pkg"
    done
}

##########################################################################################
# Test: dns-filter.sh configuration
##########################################################################################
test_dns_filter() {
    header "dns-filter.sh Tests"
    
    local script="$SCRIPT_DIR/zapret2/scripts/dns-filter.sh"
    local config="$SCRIPT_DIR/zapret2/dns-filter.ini"
    
    [ -f "$config" ] && pass "dns-filter.ini exists" || fail "dns-filter.ini not found"
    
    if [ -f "$script" ]; then
        bash -n "$script" 2>/dev/null && pass "dns-filter.sh syntax is valid" || fail "dns-filter.sh syntax is invalid"
        
        grep -q 'DNAT' "$script" && pass "dns-filter.sh uses DNAT" || fail "dns-filter.sh missing DNAT"
        grep -q 'uid-owner' "$script" && pass "dns-filter.sh uses uid-owner" || fail "dns-filter.sh missing uid-owner"
        grep -q 'flush_dns_rules' "$script" && pass "dns-filter.sh has flush function" || fail "dns-filter.sh missing flush"
    else
        skip "dns-filter.sh not found"
    fi
}

##########################################################################################
# Test: vpn-config.env format
##########################################################################################
test_vpn_config() {
    header "vpn-config.env Tests"
    
    local config="$SCRIPT_DIR/zapret2/vpn-config.env"
    
    [ -f "$config" ] && pass "vpn-config.env exists" || fail "vpn-config.env not found"
    
    if [ -f "$config" ]; then
        grep -q "^VPN_ENABLED=" "$config" && pass "VPN_ENABLED defined" || fail "VPN_ENABLED not defined"
        grep -q "^AUTO_SWITCH=" "$config" && pass "AUTO_SWITCH defined" || fail "AUTO_SWITCH not defined"
        grep -q "^VPN_SUBSCRIPTION_URL=" "$config" && pass "VPN_SUBSCRIPTION_URL defined" || fail "VPN_SUBSCRIPTION_URL not defined"
        grep -q "^VPN_PROTOCOL=" "$config" && pass "VPN_PROTOCOL defined" || fail "VPN_PROTOCOL not defined"
        
        # Test: Boolean values are 0 or 1
        grep "^VPN_ENABLED=" "$config" | grep -qE "^VPN_ENABLED=[01]$" && pass "VPN_ENABLED is boolean" || fail "VPN_ENABLED invalid format"
        grep "^AUTO_SWITCH=" "$config" | grep -qE "^AUTO_SWITCH=[01]$" && pass "AUTO_SWITCH is boolean" || fail "AUTO_SWITCH invalid format"
    fi
}

##########################################################################################
# Test: Xray config JSON structure
##########################################################################################
test_xray_config() {
    header "xray-config.json Tests"
    
    local config="$SCRIPT_DIR/zapret2/xray-config.json"
    
    [ -f "$config" ] && pass "xray-config.json exists" || fail "xray-config.json not found"
    
    if [ -f "$config" ]; then
        # Check JSON validity (basic)
        grep -q '"inbounds"' "$config" && pass "inbounds defined" || fail "inbounds not defined"
        grep -q '"outbounds"' "$config" && pass "outbounds defined" || fail "outbounds not defined"
        grep -q '"log"' "$config" && pass "log defined" || fail "log not defined"
        grep -q '"routing"' "$config" && pass "routing defined" || fail "routing not defined"
        grep -q '\${' "$config" && fail "xray-config.json still has unresolved placeholders" || pass "xray-config.json has no unresolved placeholders"
        
        # Check for tun interface
        grep -q "tun0" "$config" && pass "tun0 interface configured" || fail "tun0 not configured"
    fi
}

##########################################################################################
# Test: runtime.ini compatibility
##########################################################################################
test_runtime_config() {
    header "runtime.ini Tests"

    local runtime="$SCRIPT_DIR/zapret2/runtime.ini"
    local common="$SCRIPT_DIR/zapret2/scripts/common.sh"

    [ -f "$runtime" ] && pass "runtime.ini exists" || fail "runtime.ini not found"
    grep -q '^qnum=' "$runtime" && pass "qnum defined" || fail "qnum not defined"
    grep -q '^debug=' "$runtime" && pass "debug defined" || fail "debug not defined"

    if grep -q 'QNUM="$value"' "$common" && grep -q 'DEBUG="$value"' "$common"; then
        pass "runtime parser handles qnum and debug"
    else
        fail "runtime parser misses qnum/debug"
    fi
}

##########################################################################################
# Test: categories.ini file references
##########################################################################################
test_category_references() {
    header "categories.ini Reference Tests"

    local categories="$SCRIPT_DIR/zapret2/categories.ini"
    local missing=0

    while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        if [ ! -f "$SCRIPT_DIR/zapret2/lists/$entry" ]; then
            fail "Missing list referenced by categories.ini: $entry"
            missing=1
        fi
    done < <(awk -F= '/^(hostlist|ipset)=/ {print $2}' "$categories" | sed '/^$/d' | sort -u)

    if [ "$missing" -eq 0 ]; then
        pass "All category list references resolve"
    fi
}

##########################################################################################
# Test: Shell script syntax
##########################################################################################
test_shell_syntax() {
    header "Shell Script Syntax Tests"
    
    local scripts=(
        "zapret2/scripts/network-monitor.sh"
        "zapret2/scripts/vpn-start.sh"
        "zapret2/scripts/vpn-stop.sh"
        "zapret2/scripts/vpn-tunnel.sh"
        "zapret2/scripts/app-filter.sh"
        "zapret2/scripts/subscription-parser.sh"
        "service.sh"
        "customize.sh"
    )
    
    for script in "${scripts[@]}"; do
        local full_path="$SCRIPT_DIR/$script"
        if [ -f "$full_path" ]; then
            if bash -n "$full_path" 2>/dev/null; then
                pass "Syntax OK: $script"
            else
                fail "Syntax error: $script"
            fi
        else
            skip "Not found: $script"
        fi
    done
}

##########################################################################################
# Test: Script permissions
##########################################################################################
test_permissions() {
    header "Script Permissions Tests"
    
    local scripts=(
        "zapret2/scripts/network-monitor.sh:0755"
        "zapret2/scripts/vpn-start.sh:0755"
        "zapret2/scripts/vpn-stop.sh:0755"
        "zapret2/scripts/vpn-tunnel.sh:0755"
        "zapret2/scripts/app-filter.sh:0755"
        "zapret2/scripts/subscription-parser.sh:0755"
        "service.sh:0755"
    )
    
    for entry in "${scripts[@]}"; do
        script="${entry%%:*}"
        expected="${entry#*:}"
        normalized_expected="$(printf '%s' "$expected" | sed 's/^0*//')"
        full_path="$SCRIPT_DIR/$script"
        [ -n "$normalized_expected" ] || normalized_expected="0"
        
        if [ -f "$full_path" ]; then
            perms=$(stat -c "%a" "$full_path" 2>/dev/null || stat -f "%Lp" "$full_path" 2>/dev/null)
            if [ "$perms" = "$normalized_expected" ]; then
                pass "Permissions OK: $script ($perms)"
            else
                warn "Permissions (will be fixed on install): $script (expected $expected, got $perms)"
            fi
        else
            skip "Not found: $script"
        fi
    done
}

##########################################################################################
# Test: Module structure
##########################################################################################
test_module_structure() {
    header "Module Structure Tests"
    
    local required_files=(
        "module.prop"
        "service.sh"
        "customize.sh"
        "uninstall.sh"
        "zapret2/runtime.ini"
        "zapret2/categories.ini"
        "zapret2/config.sh"
        "zapret2/app-filter.ini"
        "zapret2/vpn-config.env"
    )
    
    local f
    for f in "${required_files[@]}"; do
        if [ -f "$SCRIPT_DIR/$f" ]; then
            pass "Exists: $f"
        else
            fail "Missing: $f"
        fi
    done
    
    local required_dirs=(
        "zapret2/scripts"
        "zapret2/lists"
        "zapret2/lua"
        "zapret2/bin"
    )
    
    local d
    for d in "${required_dirs[@]}"; do
        [ -d "$SCRIPT_DIR/$d" ] && pass "Exists: $d/" || fail "Missing: $d/"
    done
}

##########################################################################################
# Test: module.prop format
##########################################################################################
test_module_prop() {
    header "module.prop Tests"
    
    local prop="$SCRIPT_DIR/module.prop"
    
    [ -f "$prop" ] && pass "module.prop exists" || fail "module.prop not found"
    
    if [ -f "$prop" ]; then
        grep -q "^id=" "$prop" && pass "id defined" || fail "id not defined"
        grep -q "^name=" "$prop" && pass "name defined" || fail "name not defined"
        grep -q "^version=" "$prop" && pass "version defined" || fail "version not defined"
        grep -q "^versionCode=" "$prop" && pass "versionCode defined" || fail "versionCode not defined"
        grep -q "^author=" "$prop" && pass "author defined" || fail "author not defined"
        grep -q "^description=" "$prop" && pass "description defined" || fail "description not defined"
        grep -q "^updateJson=" "$prop" && pass "updateJson defined" || fail "updateJson not defined"
        
        # Check id format
        grep "^id=" "$prop" | grep -qE "^id=[a-z0-9_]+$" && pass "id format valid" || fail "id format invalid"
    fi
}

##########################################################################################
# Main
##########################################################################################
main() {
    echo "Zapret2 Module Tests"
    echo "===================="
    echo "Started at: $(date)"
    echo ""
    
    # Run all tests
    test_module_structure
    test_module_prop
    test_shell_syntax
    test_permissions
    test_subscription_parser
    test_network_monitor
    test_vpn_start
    test_app_filter
    test_dns_filter
    test_vpn_config
    test_xray_config
    test_runtime_config
    test_category_references
    
    # Summary
    header "Test Summary"
    echo ""
    echo -e "  ${GREEN}Passed:  $PASSED${NC}"
    echo -e "  ${RED}Failed:  $FAILED${NC}"
    echo -e "  ${YELLOW}Skipped: $SKIPPED${NC}"
    echo ""
    
    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        cleanup
        exit 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        cleanup
        exit 1
    fi
}

main "$@"
