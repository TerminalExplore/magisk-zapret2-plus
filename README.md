# Zapret2 Plus — Magisk Module

Расширенная версия модуля Zapret2 для обхода блокировок с поддержкой VPN и автопереключением между WiFi и Mobile.

<img width="2025" height="1125" alt="image" src="https://github.com/user-attachments/assets/7e23e26c-2b78-46f1-973a-94d9b7071d82" />

## Что это?

Модуль для обхода блокировок YouTube, Discord и других сайтов на Android с **автоматическим переключением** между методами:

```
📱 WiFi  → Zapret2 DPI bypass (быстро, напрямую)
📱 Mobile → VPN (надёжно, через сервер)
```

**Это НЕ просто VPN** — на WiFi работает напрямую без VPN (быстрее и бесплатно).

---

## Требования

- ✅ Android 7.0 или новее
- ✅ Root-доступ (Magisk или KernelSU)
- ✅ Приложение Zapret2 Control (опционально)

---

## Установка

### Скачать

📥 Скачайте из **[Releases](https://github.com/TerminalExplore/magisk-zapret2-plus/releases)**:
- `zapret2-magisk-v*.zip` — модуль для Magisk
- `zapret2-control-v*.apk` — приложение для управления

### Установить модуль

1. Откройте **Magisk** → **Модули** → **Установить из хранилища**
2. Выберите ZIP файл
3. Нажмите **Перезагрузить**

### Установить приложение

1. Откройте APK файл
2. Разрешите установку из неизвестных источников
3. Установите **Zapret2 Control**

---

## Функции

### 🔄 Автопереключение WiFi/Mobile

| Сеть | Метод | Описание |
|------|-------|----------|
| WiFi | Zapret2 DPI bypass | Быстрый обход без VPN |
| Mobile | VPN | Надёжное подключение |

Включается в приложении или в `vpn-config.env`:
```bash
AUTO_SWITCH=1
```

### 🔗 VPN подписки

Поддержка протоколов:
- **VLESS** (`vless://...`)
- **ShadowSocks** (`ss://...`)
- **VMess** (`vmess://...`)

Автоматический импорт и парсинг подписок.

### 📱 Фильтр приложений

Белый список приложений для каждого типа сети:
- Разные списки для WiFi и Mobile
- Выбор приложений прямо в приложении

### 🏓 Ping серверов

Выбор метода проверки:
- **ICMP** — стандартный пинг
- **TCP** — подключение к порту
- **Proxy** — через VPN туннель

---

## Управление через приложение

### Вкладка "Control"
- 🟢 Запустить / 🔴 Остановить
- 📊 Статус и uptime
- ⬇️ Проверить обновления

### Вкладка "VPN"
- VPN Enabled — включить/выключить
- Подписка — импорт URL
- Серверы — список с пингом
- Ручной VLESS URI

### Вкладка "App Filter"
- Белый список для WiFi
- Белый список для Mobile

### Вкладка "Auto-Switch"
- Настройка автопереключения
- VPN подписка
- Статус сети

---

## Решение проблем

### YouTube заблокирован
1. Попробуйте другую стратегию в приложении
2. Перезапустите сервис

### Модуль не запускается
```bash
su -c "[ -f /proc/net/netfilter/nfnetlink_queue ] && echo ok || echo missing"
```

### Конфликт с AdGuard/NetGuard
Отключите их или добавьте Zapret2 в исключения.

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
```

---

## Обновление

### Через приложение
1. Откройте **Zapret2 Control**
2. Нажмите **Проверить обновления**
3. Обновите модуль и приложение

### Вручную
1. Скачайте новую версию из [Releases](https://github.com/TerminalExplore/magisk-zapret2-plus/releases)
2. Установите ZIP через Magisk

---

## Благодарности

- [bol-van/zapret2](https://github.com/bol-van/zapret2) — оригинальный проект
- [youtubediscord/magisk-zapret2](https://github.com/youtubediscord/magisk-zapret2) — Android модуль

---

## Лицензия

MIT
