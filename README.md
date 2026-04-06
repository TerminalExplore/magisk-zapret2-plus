![Zapret2 Plus](images/zapret2.png)
# Zapret2 Plus — Magisk Module

Модуль для обхода DPI-блокировок на Android с поддержкой VPN, гибким управлением режимами сети и companion-приложением.

---

## Что это?

Zapret2 Plus — это Magisk-модуль, который запускает [nfqws2](https://github.com/bol-van/zapret) на уровне ядра через iptables/NFQUEUE. Трафик перехватывается без VPN API — быстро, без запроса разрешений и без иконки VPN в статусбаре (если не нужна).

Дополнительно встроен Xray для VPN-туннелирования через VLESS/VMess/ShadowSocks/Trojan — с возможностью автоматического переключения между DPI bypass и VPN в зависимости от типа сети.

---

## Требования

- Android 7.0+
- Root (Magisk 20.4+ или KernelSU)
- Ядро с поддержкой NFQUEUE

Проверить NFQUEUE:
```bash
su -c "[ -f /proc/net/netfilter/nfnetlink_queue ] && echo ok || echo missing"
```

---

## Установка

Скачайте из **[Releases](https://github.com/TerminalExplore/magisk-zapret2-plus/releases)**:
- `zapret2-magisk-v*.zip` — модуль
- `zapret2-control-v*.apk` — приложение управления

**Модуль:**
1. Magisk → Модули → Установить из хранилища → выберите ZIP
2. Перезагрузите устройство

**Приложение:**
1. Установите APK (разрешите установку из неизвестных источников)

---

## Режимы работы

Настраивается через приложение (вкладка VPN → VPN Mode) или вручную в `vpn-config.env`:

| VPN_MODE | WiFi | Mobile |
|----------|------|--------|
| `off` | Zapret2 | Zapret2 |
| `mobile` | Zapret2 | VPN |
| `wifi` | VPN | Zapret2 |
| `always` | VPN | VPN |

По умолчанию `off` — только DPI bypass без VPN.

---

## Функции

### DPI Bypass
- Перехват трафика через iptables NFQUEUE без VPN API
- Стратегии для YouTube, Discord, Telegram, Rutracker и других
- Автоподбор стратегий — тестирует реальный доступ к сайтам через zapret2 и выбирает лучшую
- Поддержка TCP, UDP, STUN протоколов
- Категории с отдельными стратегиями для каждого сервиса

### VPN
- Протоколы: VLESS, VMess, ShadowSocks, Trojan
- Импорт подписок по URL (base64 и plain)
- Список серверов с параллельным TCP-пингом
- Автовыбор быстрейшего сервера
- Внешний IP с флагом страны — для проверки что трафик идёт через VPN
- Уведомления в статусбаре при активном VPN/Zapret2

### App Filter
- Белый список приложений отдельно для WiFi и Mobile
- Показывает все установленные приложения с иконками

### Auto-Switch
- Watchdog — автоматически перезапускает упавший сервис
- Уведомления при переключении режимов
- Защита от race condition при быстром переключении сетей

---

## Приложение Zapret2 Control

### Control
- Запуск / остановка Zapret2 и VPN
- Статус, uptime, PID, память
- Тип сети, iptables статус
- Проверка обновлений

### Strategies
- Выбор стратегии для каждой категории (YouTube, Discord, и др.)
- Автоподбор — перебирает стратегии и проверяет реальный доступ
- Просмотр аргументов nfqws2 для каждой стратегии
- Изменение порядка стратегий

### VPN
- Включение VPN и выбор режима (off / mobile / wifi / always)
- Внешний IP с флагом страны (обновляется автоматически при смене VPN)
- Импорт подписки (Save & Apply сохраняет URL)
- Список серверов с пингом, выбор сервера
- Ручной ввод URI (vless:// vmess:// ss:// trojan://)
- Настройки пинга: ICMP / TCP, таймаут, автовыбор быстрейшего

### App Filter
- Поиск по приложениям
- Отдельные списки для WiFi и Mobile

### Presets / Strategies / Config
- Готовые пресеты стратегий
- Редактор командной строки nfqws2
- Управление хостлистами

---

## Конфигурация

### vpn-config.env
```bash
VPN_ENABLED=1           # Включить VPN
VPN_MODE="mobile"       # Режим: off / mobile / wifi / always
VPN_SUBSCRIPTION_URL="" # URL подписки
VPN_AUTOSTART=1         # Автозапуск VPN при переключении на нужную сеть
KILL_SWITCH=0           # Блокировать трафик если VPN упал
PING_METHOD="tcp"       # Метод пинга: icmp / tcp
PING_TIMEOUT=3          # Таймаут пинга в секундах
```

### runtime.ini
Основные настройки модуля — редактируется через приложение (Control → настройки).

---

## Команды терминала

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

## Обновление

Настройки (`runtime.ini`, `categories.ini`, `vpn-config.env`) автоматически сохраняются при обновлении модуля через Magisk.

Обновить через приложение: Control → Check for updates.

Обновить вручную: установить новый ZIP через Magisk.

---

## Решение проблем

**Сайт не открывается:**
1. Попробуйте другую стратегию в Strategies → автоподбор
2. Перезапустите сервис

**Модуль не запускается:**
```bash
su -c zapret2-status
```

**VPN не подключается:**
- Проверьте логи во вкладке VPN → Logs
- Убедитесь что подписка импортирована и сервер выбран

**Конфликт с AdGuard / NetGuard:**
Отключите их или добавьте Zapret2 в исключения.

---

## Благодарности

- [bol-van/zapret](https://github.com/bol-van/zapret) — оригинальный nfqws
- [youtubediscord/magisk-zapret2](https://github.com/youtubediscord/magisk-zapret2) — Android-порт
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core) — VPN ядро

---

## Лицензия

MIT
