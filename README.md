![Zapret2 Plus](images/zapret2.png)
# Zapret2 Plus — Magisk Module

![Logo](images/zapret2.png)

DPI bypass module for Android with VPN support, flexible network mode management, and a companion app.

---

## What is it?

Zapret2 Plus is a Magisk module that runs [nfqws2](https://github.com/bol-van/zapret) at the kernel level via iptables/NFQUEUE. Traffic is intercepted without VPN API — fast, no permission requests, and no VPN icon in the status bar (if not needed).

Additionally, Xray is built-in for VPN tunneling via VLESS/VMess/ShadowSocks/Trojan — with the ability to automatically switch between DPI bypass and VPN depending on network type.

---

## Requirements

- Android 7.0+
- Root (Magisk 20.4+ or KernelSU)
- Kernel with NFQUEUE support

Check NFQUEUE:
```bash
su -c "[ -f /proc/net/netfilter/nfnetlink_queue ] && echo ok || echo missing"
```

---

## Installation

Download from **[Releases](https://github.com/TerminalExplore/magisk-zapret2-plus/releases)**:
- `zapret2-magisk-v*.zip` — module
- `zapret2-control-v*.apk` — companion app

**Module:**
1. Magisk → Modules → Install from storage → select ZIP
2. Reboot device

**App:**
1. Install APK (allow unknown sources)

---

## Operating Modes

Configure via app (VPN tab → VPN Mode) or manually in `vpn-config.env`:

| VPN_MODE | WiFi | Mobile |
|----------|------|--------|
| `off` | Zapret2 | Zapret2 |
| `mobile` | Zapret2 | VPN |
| `wifi` | VPN | Zapret2 |
| `always` | VPN | VPN |

Default is `off` — DPI bypass only, no VPN.

---

## Features

### DPI Bypass
- Traffic interception via iptables NFQUEUE without VPN API
- Strategies for YouTube, Discord, Telegram, Rutracker, and others
- Auto-select strategies — tests real site access via zapret2 and picks the best
- TCP, UDP, STUN protocol support
- Categories with separate strategies for each service

### VPN
- Protocols: VLESS, VMess, ShadowSocks, Trojan
- Subscription import by URL (base64 and plain)
- Server list with parallel TCP ping
- Auto-select fastest server
- External IP with country flag — to verify traffic goes through VPN
- Status bar notifications when VPN/Zapret2 is active

### App Filter
- Whitelist of apps separately for WiFi and Mobile
- Shows all installed apps with icons

### Auto-Switch
- Watchdog — automatically restarts crashed service
- Notifications when switching modes
- Protection against race conditions during fast network switches

---

## Zapret2 Control App

### Control
- Start / stop Zapret2 and VPN
- Status, uptime, PID, memory
- Network type, iptables status
- Check for updates

### Strategies
- Select strategy for each category (YouTube, Discord, etc.)
- Auto-select — iterates through strategies and checks real access
- View nfqws2 arguments for each strategy
- Reorder strategies

### VPN
- Enable VPN and select mode (off / mobile / wifi / always)
- External IP with country flag (auto-updates when VPN changes)
- Import subscription (Save & Apply saves URL)
- Server list with ping, server selection
- Manual URI input (vless:// vmess:// ss:// trojan://)
- Ping settings: ICMP / TCP, timeout, auto-select fastest

### App Filter
- Search apps
- Separate lists for WiFi and Mobile

### Presets / Strategies / Config
- Ready-made strategy presets
- nfqws2 command line editor
- Hostlist management

---

## Configuration

### vpn-config.env
```bash
VPN_ENABLED=1           # Enable VPN
VPN_MODE="mobile"       # Mode: off / mobile / wifi / always
VPN_SUBSCRIPTION_URL="" # Subscription URL
VPN_AUTOSTART=1         # Auto-start VPN when switching to target network
KILL_SWITCH=0          # Block traffic if VPN drops
PING_METHOD="tcp"      # Ping method: icmp / tcp
PING_TIMEOUT=3         # Ping timeout in seconds
```

### runtime.ini
Core module settings — edited via app (Control → settings).

---

## Terminal Commands

```bash
# Zapret2
su -c zapret2-start
su -c zapret2-stop
su -c zapret2-restart
su -c zapret2-status

# VPN
su -c zapret2-vpn-start
su -c zapret2-vpn-stop

# Network monitor (auto-switch)
su -c zapret2-network-monitor
su -c "zapret2-network-monitor status"
su -c "zapret2-network-monitor stop"
```

---

## Updates

Settings (`runtime.ini`, `categories.ini`, `vpn-config.env`) are automatically preserved when updating module via Magisk.

Update via app: Control → Check for updates.

Update manually: install new ZIP via Magisk.

---

## Troubleshooting

**Site not opening:**
1. Try different strategy in Strategies → auto-select
2. Restart service

**Module not starting:**
```bash
su -c zapret2-status
```

**VPN not connecting:**
- Check logs in VPN tab → Logs
- Make sure subscription is imported and server is selected

**Conflict with AdGuard / NetGuard:**
Disable them or add Zapret2 to exceptions.

---

## Acknowledgements

- [bol-van/zapret](https://github.com/bol-van/zapret) — original nfqws
- [youtubediscord/magisk-zapret2](https://github.com/youtubediscord/magisk-zapret2) — Android port
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core) — VPN core

---

## License

MIT
