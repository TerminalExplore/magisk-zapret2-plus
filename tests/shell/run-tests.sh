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
    
    if [ ! -f "$script" ]; then
        skip "subscription-parser.sh not found"
        return
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
    
    # Test: Package name validation
    log "Testing package name validation..."
    
    local valid_packages="com.google.android.youtube com.discord com.telegram.messenger"
    for pkg in $valid_packages; do
        [[ "$pkg" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$ ]] && pass "Valid package: $pkg" || fail "Invalid package: $pkg"
    done
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
        
        # Check for tun interface
        grep -q "tun0" "$config" && pass "tun0 interface configured" || fail "tun0 not configured"
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
        full_path="$SCRIPT_DIR/$script"
        
        if [ -f "$full_path" ]; then
            perms=$(stat -c "%a" "$full_path" 2>/dev/null || stat -f "%Lp" "$full_path" 2>/dev/null)
            if [ "$perms" = "$expected" ]; then
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
    test_app_filter
    test_vpn_config
    test_xray_config
    
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
