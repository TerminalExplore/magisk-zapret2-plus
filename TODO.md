## Changelog v2.0-beta.1

### New Features
- **Auto-Switch WiFi/Mobile**: Automatically switch between Zapret2 (WiFi) and VPN (Mobile)
- **VPN Subscription Support**: Import VLESS, ShadowSocks, VMess subscriptions
- **App Filtering**: Whitelist specific apps per network type (WiFi/Mobile)
- **Xray Integration**: Built-in Xray binary for VPN functionality

### New Files
- `zapret2/scripts/network-monitor.sh` - Network type detection
- `zapret2/scripts/vpn-start.sh`, `vpn-stop.sh`, `vpn-tunnel.sh` - VPN control
- `zapret2/scripts/subscription-parser.sh` - Subscription parsing
- `zapret2/scripts/app-filter.sh` - App filtering by UID
- `zapret2/vpn-config.env` - VPN configuration template
- `zapret2/xray-config.json` - Xray config template
- `zapret2/app-filter.ini` - App filter configuration
- Android companion app screens for VPN and app filter settings

### Testing
- Shell script tests (57 tests)
- Android unit tests (VlessParser, UpdateManager)
- Integration test templates
- CI/CD with GitHub Actions
