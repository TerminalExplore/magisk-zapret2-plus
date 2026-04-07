# Тесты Zapret2

## Обзор

Этот каталог содержит тесты для Magisk модуля Zapret2.

## Структура

```
tests/
├── shell/                    # Тесты shell скриптов (без устройства)
│   └── run-tests.sh          # Основной запускатель тестов
├── integration/              # Интеграционные тесты (требуется устройство)
│   └── run-integration-tests.sh
└── README.md                 # Этот файл
```

## Запуск тестов

### Shell тесты (без устройства)

```bash
# Запустить все shell тесты
bash tests/shell/run-tests.sh

# Или напрямую
chmod +x tests/shell/run-tests.sh
./tests/shell/run-tests.sh
```

**Тесты включены:**
- Валидация структуры модуля
- Проверка синтаксиса shell скриптов
- Верификация прав на файлы
- Валидация формата конфигурационных файлов
- Парсинг VLESS URI
- Логика определения сети

### Интеграционные тесты (требуется устройство)

```bash
# Подключите устройство через ADB
adb devices

# Запустите интеграционные тесты
bash tests/integration/run-integration-tests.sh
```

**Тесты включены:**
- Подключение устройства
- Проверка root доступа
- Проверка установки модуля
- Поддержка iptables/NFQUEUE
- Состояние сети
- Наличие VPN бинарника
- Zapret2 start/stop

### Android Unit тесты

```bash
cd android-app
./gradlew test
```

**Тесты включены:**
- Парсинг VLESS URI
- Генерация Xray JSON
- Парсинг подписок
- Сравнение версий

## CI/CD

Тесты запускаются автоматически при:
- Каждом push в `main`
- Каждом pull request
- Перед сборкой релизов

См. `.github/workflows/build.yml` для конфигурации CI.

## Добавление новых тестов

### Shell тесты
Добавьте тестовые функции в `tests/shell/run-tests.sh`:

```bash
test_my_feature() {
    header "My Feature Tests"
    
    # Тестовая логика
    [ condition ] && pass "Тест прошёл" || fail "Тест не прошёл"
}
```

### Android тесты
Добавьте тестовые файлы в `android-app/app/src/test/java/com/zapret2/app/data/`:

```kotlin
@RunWith(AndroidJUnit4::class)
class MyFeatureTest {
    @Test
    fun `test description`() {
        // тестовый код
    }
}
```
