# Внесение вклада в Zapret2 Plus

Спасибо за интерес к проекту!

## Начало работы

1. Сделайте форк репозитория
2. Клонируйте свой форк
3. Создайте ветку от `dev`

## Модель веток

```
main          ← Продакшн релизы (теги здесь)
└── dev       ← Интеграция разработки
    ├── feature/xxx   ← Новые функции
    ├── fix/xxx       ← Исправления багов
    └── docs/xxx      ← Документация
```

## Процесс разработки

1. **Создайте ветку**: `git checkout -b feature/my-feature dev`
2. **Внесите изменения** и протестируйте локально
3. **Запустите тесты**:
   ```bash
   # Shell тесты
   bash tests/shell/run-tests.sh
   
   # Android unit тесты
   cd android-app && ./gradlew test
   ```
4. **Зафиксируйте**: `git commit -m "feat: add my feature"`
5. **Отправьте**: `git push origin feature/my-feature`
6. **Создайте Pull Request** в `dev`

## Стиль кода

### Shell скрипты
- Используйте [shellcheck](https://www.shellcheck.net/) для проверки
- Следуйте стандарту POSIX sh
- Используйте описательные имена переменных
- Делайте функции небольшими и сфокусированными

### Kotlin
- Следуйте [соглашениям Kotlin](https://kotlinlang.org/docs/coding-conventions.html)
- Используйте `ktlint` для проверки (включён в проект)
- Предпочитайте неизменяемость
- Используйте понятные имена

## Сообщения коммитов

Формат: `тип: краткое описание`

Типы:
- `feat`: Новая функция
- `fix`: Исправление бага
- `docs`: Документация
- `chore`: Обслуживание (зависимости, CI и т.д.)
- `refactor`: Рефакторинг кода
- `test`: Добавление или обновление тестов

Примеры:
```
feat: add server ping functionality
fix: resolve VPN connection timeout
docs: update installation guide
chore: update Xray to v26.3.23
```

## Чеклист Pull Request

- [ ] Тесты проходят локально
- [ ] Код следует гайдлайнам стиля
- [ ] Сообщения коммитов понятны и следуют соглашениям
- [ ] Документация обновлена (если нужно)
- [ ] Нет console логов или debug кода

## Тестирование

### Shell тесты
```bash
bash tests/shell/run-tests.sh
```

### Android Unit тесты
```bash
cd android-app && ./gradlew test
```

### Проверка стиля
```bash
# Shell скрипты
shellcheck zapret2/scripts/*.sh

# Kotlin
cd android-app && ./gradlew ktlintCheck
```

## Структура проекта

```
magisk-zapret2/
├── android-app/          # Android companion app
│   └── app/src/main/
│       ├── java/         # Kotlin исходники
│       └── res/          # Ресурсы
├── zapret2/              # Файлы Magisk модуля
│   ├── scripts/          # Shell скрипты
│   ├── bin/              # Бинарники (nfqws2, xray)
│   └── lua/              # Lua скрипты
├── systemd/              # Linux systemd сервис
├── tests/                # Наборы тестов
├── docs/                  # Документация
└── .github/workflows/    # CI/CD
```

## Вопросы?

- Откройте [issue](https://github.com/TerminalExplore/magisk-zapret2-plus/issues) для багов
- Для обсуждений используйте [discussions](https://github.com/TerminalExplore/magisk-zapret2-plus/discussions)
