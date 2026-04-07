# Zapret2 Systemd Integration

This directory contains systemd unit files for running Zapret2 as a system service on Linux.

## Installation

```bash
# Copy service file
sudo cp zapret2.service /etc/systemd/system/

# Copy control script
sudo cp zapret2.sh /opt/zapret2/zapret2.sh
sudo chmod +x /opt/zapret2/zapret2.sh

# Reload systemd
sudo systemctl daemon-reload

# Enable on boot
sudo systemctl enable zapret2

# Start now
sudo systemctl start zapret2
```

## Usage

```bash
# Start/Stop/Restart
sudo systemctl start zapret2
sudo systemctl stop zapret2
sudo systemctl restart zapret2

# Check status
sudo systemctl status zapret2

# View logs
journalctl -u zapret2 -f

# Enable/disable auto-start
sudo systemctl enable zapret2
sudo systemctl disable zapret2
```

## Configuration

Set environment variables in systemd unit or `/etc/default/zapret2`:

```bash
ZAPRET_DIR=/opt/zapret2
LOG_LEVEL=debug
```

## Requirements

- Linux with systemd
- Root access (CAP_NET_ADMIN capability)
- iptables/nftables with NFQUEUE support
