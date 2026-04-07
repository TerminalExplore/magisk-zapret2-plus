# Интеграция Zapret2 с Systemd

В этом каталоге находятся файлы systemd unit для запуска Zapret2 как системного сервиса на Linux.

## Установка

```bash
# Скопировать service файл
sudo cp zapret2.service /etc/systemd/system/

# Скопировать управляющий скрипт
sudo cp zapret2.sh /opt/zapret2/zapret2.sh
sudo chmod +x /opt/zapret2/zapret2.sh

# Перезагрузить systemd
sudo systemctl daemon-reload

# Включить автозапуск
sudo systemctl enable zapret2

# Запустить сейчас
sudo systemctl start zapret2
```

## Использование

```bash
# Запуск/Остановка/Перезапуск
sudo systemctl start zapret2
sudo systemctl stop zapret2
sudo systemctl restart zapret2

# Проверить статус
sudo systemctl status zapret2

# Просмотр логов
journalctl -u zapret2 -f

# Включить/выключить автозапуск
sudo systemctl enable zapret2
sudo systemctl disable zapret2
```

## Конфигурация

Установите переменные окружения в systemd unit или `/etc/default/zapret2`:

```bash
ZAPRET_DIR=/opt/zapret2
LOG_LEVEL=debug
```

## Требования

- Linux с systemd
- Root доступ (CAP_NET_ADMIN capability)
- iptables/nftables с поддержкой NFQUEUE
