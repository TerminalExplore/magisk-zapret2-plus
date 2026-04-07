# Architecture

## Overview

Zapret2 Plus consists of two main components:

```
┌─────────────────────────────────────────────────────────────┐
│                    Magisk Module                            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  nfqws2     │    │   Xray      │    │   Scripts   │     │
│  │  (DPI)      │    │   (VPN)     │    │   (Control) │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│         │                  │                  │             │
│         └──────────────────┼──────────────────┘             │
│                            │                               │
│                    ┌───────┴───────┐                       │
│                    │   iptables    │                       │
│                    │   NFQUEUE     │                       │
│                    └───────────────┘                       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Android App (Kotlin)                      │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  UI Layer   │    │  Data Layer │    │  Service     │     │
│  │  (Compose)  │    │  (Repository│    │  Layer       │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Components

### Magisk Module

| Component | Path | Description |
|-----------|------|-------------|
| nfqws2 | `zapret2/bin/*/nfqws2` | DPI bypass engine |
| Xray | `zapret2/bin/*/xray` | VPN client (VLESS/VMess/SS/Trojan) |
| Scripts | `zapret2/scripts/*.sh` | Control and configuration scripts |
| Config | `zapret2/*.ini` | Runtime configuration files |

### Android App

| Layer | Package | Description |
|-------|---------|-------------|
| UI | `ui.screen.*` | Compose screens |
| ViewModel | `viewmodel.*` | State management |
| Data | `data.*` | Repositories, managers |
| Service | `service.*` | Background services |

## Data Flow

```
User Action → ViewModel → Shell Command → Magisk Module → Result
                ↑                              │
                └──────────────────────────────┘
                         Status Update
```

## Network Flow

```
┌─────────┐     ┌────────────┐     ┌───────────┐     ┌──────────┐
│  App    │────▶│  Zapret2   │────▶│ iptables  │────▶│ Internet │
│ Traffic │     │  (NFQUEUE) │     │  NAT      │     │          │
└─────────┘     └────────────┘     └───────────┘     └──────────┘
                      │
                      ▼ (optional)
                ┌────────────┐
                │   Xray     │ VPN Tunnel
                └────────────┘
```

## Build System

```
CI/CD Pipeline:
  Push/PR → Tests → Lint → Build → Artifacts → Release
                ↑                         │
                └───────── Docs? ────────┘
```

## State Files

| File | Purpose |
|------|---------|
| `nfqws2.pid` | Running process PID |
| `xray.pid` | VPN process PID |
| `vpn-config.json` | Generated VPN config |
| `categories.ini` | Category enable/disable state |
| `vpn-subs-raw.txt` | Raw subscription data |
