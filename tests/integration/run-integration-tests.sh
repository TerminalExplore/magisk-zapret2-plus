#!/bin/bash
##########################################################################################
# Integration Tests for Zapret2 Module
# These tests require a running Android environment or emulator
##########################################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASSED=0
FAILED=0
SKIPPED=0

pass() { echo -e "${GREEN}✓ PASS${NC}: $1"; ((PASSED++)); }
fail() { echo -e "${RED}✗ FAIL${NC}: $1"; ((FAILED++)); }
skip() { echo -e "${YELLOW}⊘ SKIP${NC}: $1"; ((SKIPPED++)); }

header() {
    echo ""
    echo "========================================"
    echo " $1"
    echo "========================================"
}

check_root() {
    if command -v su >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

check_adb() {
    if command -v adb >/dev/null 2>&1 && adb devices 2>/dev/null | grep -q "device$"; then
        return 0
    fi
    return 1
}

##########################################################################################
# Test: Device connectivity
##########################################################################################
test_device_connectivity() {
    header "Device Connectivity"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local model=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    local android=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    
    [ -n "$model" ] && pass "Device: $model" || fail "Cannot get device model"
    [ -n "$android" ] && pass "Android: $Android $android" || fail "Cannot get Android version"
}

##########################################################################################
# Test: Root access
##########################################################################################
test_root_access() {
    header "Root Access"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    if adb shell "su -c 'echo root'" 2>/dev/null | grep -q "root"; then
        pass "Root access available"
    else
        fail "Root access denied"
    fi
}

##########################################################################################
# Test: Module installation
##########################################################################################
test_module_installed() {
    header "Module Installation"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local module_path="/data/adb/modules/zapret2"
    
    if adb shell "[ -d $module_path ]" 2>/dev/null; then
        pass "Module installed at $module_path"
    else
        fail "Module not found at $module_path"
        skip "Install module first to run further tests"
        return
    fi
    
    # Check key files
    local files=(
        "/data/adb/modules/zapret2/service.sh"
        "/data/adb/modules/zapret2/zapret2/scripts/zapret-start.sh"
        "/data/adb/modules/zapret2/zapret2/vpn-config.env"
    )
    
    for file in "${files[@]}"; do
        if adb shell "[ -f $file ]" 2>/dev/null; then
            pass "File exists: $(basename $file)"
        else
            fail "File missing: $(basename $file)"
        fi
    done
}

##########################################################################################
# Test: Zapret2 service status
##########################################################################################
test_zapret_status() {
    header "Zapret2 Service Status"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local status=$(adb shell "zapret2-status 2>/dev/null" | tr -d '\r')
    
    if [ -n "$status" ]; then
        pass "Zapret2 status command works"
        echo "Status: $status"
    else
        fail "Cannot get Zapret2 status"
    fi
}

##########################################################################################
# Test: VPN binary presence
##########################################################################################
test_vpn_binary() {
    header "VPN Binary"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local xray_path="/data/adb/modules/zapret2/zapret2/xray"
    local nfqws_path="/data/adb/modules/zapret2/zapret2/nfqws2"
    
    if adb shell "[ -f $xray_path ]" 2>/dev/null; then
        pass "Xray binary found"
    else
        skip "Xray binary not found (VPN won't work)"
    fi
    
    if adb shell "[ -f $nfqws_path ]" 2>/dev/null; then
        pass "nfqws2 binary found"
    else
        skip "nfqws2 binary not found"
    fi
}

##########################################################################################
# Test: iptables/nfqueue support
##########################################################################################
test_iptables_support() {
    header "iptables/NFQUEUE Support"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    # Check NFQUEUE
    if adb shell "su -c 'grep -q NFQUEUE /proc/net/ip_tables_targets 2>/dev/null && echo yes || echo no'" 2>/dev/null | grep -q "yes"; then
        pass "NFQUEUE support available"
    else
        skip "NFQUEUE support not detected"
    fi
    
    # Check iptables
    if adb shell "su -c 'command -v iptables'" 2>/dev/null | grep -q "iptables"; then
        pass "iptables available"
    else
        fail "iptables not available"
    fi
}

##########################################################################################
# Test: Network status
##########################################################################################
test_network_status() {
    header "Network Status"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local wifi_state=$(adb shell "ip link show wlan0 2>/dev/null | grep state UP" | tr -d '\r')
    local mobile_state=$(adb shell "ip link show rmnet0 2>/dev/null | grep state UP" | tr -d '\r')
    
    if [ -n "$wifi_state" ]; then
        pass "WiFi is UP"
    else
        skip "WiFi is not connected"
    fi
    
    if [ -n "$mobile_state" ]; then
        pass "Mobile data is UP"
    else
        skip "Mobile data is not connected"
    fi
}

##########################################################################################
# Test: VPN config
##########################################################################################
test_vpn_config() {
    header "VPN Configuration"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    local config="/data/adb/modules/zapret2/zapret2/vpn-config.env"
    
    if adb shell "[ -f $config ]" 2>/dev/null; then
        pass "VPN config file exists"
        
        local vpn_enabled=$(adb shell "su -c 'source $config && echo \$VPN_ENABLED'" 2>/dev/null | tr -d '\r')
        local auto_switch=$(adb shell "su -c 'source $config && echo \$AUTO_SWITCH'" 2>/dev/null | tr -d '\r')
        
        echo "  VPN_ENABLED: ${vpn_enabled:-not set}"
        echo "  AUTO_SWITCH: ${auto_switch:-not set}"
    else
        skip "VPN config file not found"
    fi
}

##########################################################################################
# Test: Start/Stop Zapret2
##########################################################################################
test_zapret_start_stop() {
    header "Zapret2 Start/Stop"
    
    if ! check_adb; then
        skip "No ADB device connected"
        return
    fi
    
    if ! adb shell "[ -f /data/adb/modules/zapret2/zapret2/nfqws2 ]" 2>/dev/null; then
        skip "nfqws2 binary not found"
        return
    fi
    
    # Stop first
    adb shell "su -c zapret2-stop 2>/dev/null" >/dev/null
    sleep 2
    
    # Check stopped
    local pid_file="/data/adb/modules/zapret2/zapret2/nfqws2.pid"
    local before=$(adb shell "su -c '[ -f $pid_file ] && cat $pid_file'" 2>/dev/null | tr -d '\r')
    
    if [ -z "$before" ]; then
        pass "Zapret2 stopped successfully"
    else
        fail "Zapret2 still running after stop"
    fi
    
    # Start
    adb shell "su -c zapret2-start 2>/dev/null" >/dev/null
    sleep 2
    
    # Check started
    local after=$(adb shell "su -c '[ -f $pid_file ] && cat $pid_file'" 2>/dev/null | tr -d '\r')
    
    if [ -n "$after" ]; then
        pass "Zapret2 started successfully (PID: $after)"
    else
        fail "Zapret2 did not start"
    fi
    
    # Clean up
    adb shell "su -c zapret2-stop 2>/dev/null" >/dev/null
}

##########################################################################################
# Main
##########################################################################################
main() {
    echo "Zapret2 Integration Tests"
    echo "=========================="
    echo "Started at: $(date)"
    echo ""
    
    # Check prerequisites
    echo "Checking prerequisites..."
    if ! check_adb; then
        echo -e "${YELLOW}Warning: No ADB device connected${NC}"
        echo "Some tests will be skipped."
        echo ""
    fi
    
    # Run all integration tests
    test_device_connectivity
    test_root_access
    test_module_installed
    test_iptables_support
    test_network_status
    test_vpn_binary
    test_vpn_config
    test_zapret_start_stop
    
    # Summary
    header "Test Summary"
    echo ""
    echo -e "  ${GREEN}Passed:  $PASSED${NC}"
    echo -e "  ${RED}Failed:  $FAILED${NC}"
    echo -e "  ${YELLOW}Skipped: $SKIPPED${NC}"
    echo ""
    
    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        exit 1
    fi
}

main "$@"
