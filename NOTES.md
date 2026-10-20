=== НАЧАЛО ФАЙЛА NOTES.md ===

# Treadmill Control — состояние проекта

Android-приложение на Kotlin + Compose для управления беговой дорожкой
UNixFit-990x (модуль FitShow FS-E629DD) через Bluetooth Low Energy (FTMS),
с поддержкой нагрудного пульсометра, авторегулировкой скорости по пульсу
и фоновой работой (Foreground Service).

Работает полностью офлайн — разрешение INTERNET намеренно не запрашивается.

---

## Стек

- Язык: Kotlin 1.9.22, JVM target 17
- UI: Jetpack Compose (BOM 2024.02.00, Material 3 БЕЗ MenuAnchorType)
- Архитектура: MVVM + StateFlow + Foreground Service
- База: Room 2.6.1 (SQLite) с поддержкой миграций
- Сериализация: kotlinx.serialization 1.6.3
- BLE: android.bluetooth.le + собственные обёртки
- Сборка: Gradle 8.6, AGP 8.2.2, KSP 1.9.22-1.0.17
- SDK: min 21, target/compile 34
- CI/CD: GitHub Actions (автоматический versionCode)

---

## Структура проекта

    app/src/main/java/com/custom/treadmill/
    ├── TreadmillApp.kt              — Application, ручной DI
    ├── MainActivity.kt              — точка входа, тема из настроек
    │
    ├── ble/
    │   ├── Uuids.kt                 — UUID сервисов и характеристик
    │   ├── BleManager.kt            — сканирование BLE
    │   ├── BleConnection.kt         — обёртка BluetoothGatt
    │   ├── ITreadmillProtocol.kt    — интерфейс + TreadmillData
    │   ├── FTMSProtocol.kt          — стандартный FTMS (рабочий)
    │   ├── TreadmillSession.kt      — ЯДРО: держит связь, метрики, автопереподключение
    │   └── HeartRateService.kt      — чтение пульса (0x180D)
    │
    ├── data/
    │   ├── database/                — Room (AppDatabase, DAO, Entity)
    │   └── repository/
    │       ├── ProgramRepository.kt — CRUD, JSON, демо
    │       └── SettingsStore.kt     — SharedPreferences + JSON
    │
    ├── logic/
    │   ├── WorkoutManager.kt        — программа + пауза/пропуск/onFinish
    │   ├── HeartRateController.kt   — авторегулировка
    │   └── HeartRateZoneCalculator.kt — расчет пульсовых зон (Karvonen)
    │
    ├── service/
    │   └── TreadmillService.kt      — Foreground Service (уведомление в шторке)
    │
    └── ui/
        ├── Theme.kt, AppNavHost.kt, Permissions.kt
        ├── components/HeartRateChart.kt, RollingText.kt
        ├── screens/
        │   ├── MainScreen.kt, ProgramsScreen.kt
        │   ├── WorkoutScreen.kt, SettingsScreen.kt
        │   ├── HistoryScreen.kt, DebugScreen.kt, AboutScreen.kt
        └── viewmodels/MainViewModel.kt, ProgramViewModel.kt

---

## BLE — детали

### UUID (Uuids.kt)
- FTMS Service: 0x1826
- FTMS Control Point: 0x2AD9
- FTMS Treadmill Data: 0x2ACD (notify)
- HR Service: 0x180D, Measurement: 0x2A37
- *FitShow Proprietary УДАЛЕН из проекта полностью*

### Протокол belt'а FS-E629DD (проверено на железе в 2026-10-19)

Команды:
- `00` — Request Control. Belt молча принимает.
- `07` — Start / resume.
- `02 XX XX` — Speed, км/ч × 100 LE.
- `03 XX XX` — Incline, % × 10 LE. *(Лог подтвердил, что дорожка принимает команду 03, но физически не поднимается, если спит или пустая).*
- `08 01` — **Stop / Pause. РАБОТАЕТ!** Останавливает мотор И сбрасывает таймер на самой дорожке. (Раньше ошибочно считался игнорируемым).

### Критичные особенности протокола

1. **Правильный старт (Wake-up):** `00`, пауза 200 мс, `07`. Сразу после этого нужно отправить `02 XX XX` (скорость), чтобы "разбудить" дорожку. Если belt в глубоком сне, он не поедет, пока не нажать физическую кнопку.
2. **Смена скорости:** только `02 XX XX`.
3. **Остановка:** использовать `08 01`. Старый `02 00 00` останавливал мотор, но таймер на дорожке продолжал идти.
4. **Sleep mode:** belt подключён по BLE, но мотор не реагирует. Программно не будится. Только физическая кнопка.
5. `parseTreadmillData`: если флаг incline (1 shl 3) отсутствует → incline = 0.0.
6. Belt пищит на любую команду. Retry-loop работает только для скорости.

---

## Логика работы (TreadmillSession)

Вся BLE-логика вынесена из ViewModel в `TreadmillSession`.
- **Автопереподключение**: Если связь теряется, `TreadmillSession` 10 раз (с интервалом 3 сек) пытается переподключиться к MAC-адресу.
- **Метрики**: Свои `uiElapsedSec`, `uiDistanceKm`, `uiCalories` (с учетом веса и наклона по формуле MET).
- **Foreground Service**: `TreadmillService` держит процесс живым и показывает плашку в шторке (Пульс, Скорость, Время). Свернуть приложение теперь безопасно.
- **WorkoutManager**: Управляет программой. При пропуске сегмента время больше не накручивается. При окончании программы останавливает belt.

---

## Настройки (AppSettings)

- themeMode: SYSTEM / LIGHT / DARK
- keepScreenOn: не гасить экран во время тренировки
- **weightKg / age / isMale / restingHr**: Профиль спортсмена для расчета калорий и пульсовых зон.
- **autoMaxHr / maxHr**: Авто-расчет максимального пульса (Tanaka/Gulati) или ручной.
- minSpeedKmh / maxSpeedKmh: 1.0 / 20.0
- maxInclinePercent: 15.0
- speedStepKmh: шаг кнопок +/-
- hrEnabled / hrMode / targetHr / zoneMin / zoneMax / stepKmh / intervalSec
- *Удалено:* protocol, manualWriteUuid, manualNotifyUuid, sendStartCommand (зашито `true`).

---

## UI

### Экран Настроек
Полностью переработан в стиле Material 3.
- Карточки (SettingsGroup) с иконками.
- Компактные слайдеры (CompactSliderSetting) и выпадающие списки (CompactEnumDropdown).
- Профиль спортсмена свернут в аккордеон.

### График пульса (HeartRateChart)
- Раскрашивается в 5 цветных полупрозрачных зон (от синего до красного) на основе профиля спортсмена (формула Карвонена).

### Экран Тренировки
- При нажатии «Назад» или «Стоп» появляется диалог подтверждения завершения.
- Экран не гаснет (`FLAG_KEEP_SCREEN_ON`).

### Главный экран
- Прокрутка убрана (экран помещается целиком).
- Кнопки +/- скорости и наклона дизейблятся, если нет подключения.
- Неоново-киберпанк иконка приложения (adaptive icon).

---

## Сборка

### .github/workflows/build.yml
- Сборка APK при каждом push.
- `versionCode` автоматически берется из `github.run_number` (+10000).
- Настроено кэширование папки `~/.android` для сохранения ключа подписи (Debug Keystore).

### 🔑 Подпись APK (решено 2026-10-19)

**Проблема:** каждый билд подписывался новым случайным ключом → «Конфликт пакетов» при установке поверх старого APK.

**Причина:** Gradle неявно использовал `~/.android/debug.keystore`, которого на чистом раннере GitHub Actions нет. Секрет `DEBUG_KEYSTORE_BASE64` в старом `build.yml` либо не был задан, либо декодировался битым (из-за `echo` вместо `printf`) → Gradle генерировал новый ключ на каждом прогоне.

**Решение (вариант «keystore в репозитории»):**
- Постоянный файл `app/keystore/treadmill.keystore` закоммичен в репо (пароли `treadmill123`, alias `treadmill`).
- В `app/build.gradle.kts` добавлен явный `signingConfigs.create("stable")` и прописан в `buildTypes.debug` и `buildTypes.release`.
- В `build.yml` добавлен обязательный шаг `Verify keystore` (падает с понятной ошибкой, если файла нет / он битый) и информационный шаг `Show APK signature` (печатает SHA-256 отпечаток в лог; `continue-on-error: true`).

**Как проверить, что обновление встанет поверх:**
1. Actions → Build APK → шаг `Verify keystore` → строка `SHA256: ...`
2. Сравнить с предыдущим прогоном — отпечатки должны совпадать побайтово.
3. На телефоне при установке нового APK должна быть кнопка **«Обновить»**, а не «Установить» рядом с копией.

**⚠️ Нельзя (иначе конфликт вернётся):**
- Удалять/заменять `app/keystore/treadmill.keystore`.
- Менять пароли/alias в `build.gradle.kts` (`treadmill123` / `treadmill`).
- Полагаться на `~/.android/debug.keystore` — его больше не используем.

**Резервная копия keystore** хранится вне GitHub (облако + менеджер паролей). Потеря ключа = разовая переустановка приложения с потерей данных.

---

## Тесты — статус

### Пройдено
- A1, A2, B1-B7 (все базовые команды, кроме наклона — он принимается, но не двигается).
- C1-C6 (программы, пауза, стоп).
- D1-D2 (метрики).
- **B6** (пауза/продолжить восстанавливает 1.0 км/ч).
- **C4** (пауза в программе).
- **Работа в фоне:** При сворачивании экрана связь не рвется, метрики идут, уведомление висит.
- **Автопереподключение:** Логи показывают, что при обрыве сервис начинает цикл реконнекта.
- - **Подпись APK:** keystore в репо, отпечаток SHA-256 стабилен между билдами.
- **Обновление поверх:** новый APK ставится без удаления старого, данные сохраняются.

---

## Известные подводные камни (НЕ ПЕРЕДЕЛЫВАТЬ)

1. MenuAnchorType — нет в BOM 2024.02.00. Только `.menuAnchor()`.
2. Gradle 9.x несовместим с AGP 8.2.2. Только 8.6.
3. Configuration cache отключен.
4. Wake-up: если дорожка спит, нужно нажать физическую кнопку.
5. `stop()` теперь использует `08 01`, а не `02 00 00`.
6. В `TreadmillSession` нельзя вызывать `viewModelScope`. Используется свой `scope = CoroutineScope(Dispatchers.IO + SupervisorJob())`.

---

## Backlog

- [x] Автопереподключение BLE (сделано, работает в `TreadmillSession`).
- [x] **Починить CI (Keystore) — сделано 2026-10-19.** Keystore в репо + явный signingConfig + Verify keystore в CI.
- [ ] TTS голосовые подсказки.
- [ ] Плавающая emergency stop.
- [ ] Экспорт в TCX / FIT.
- [ ] Резервное копирование БД (ZIP).

---

## Changelog

### 2026-10-19 (v2.0+ · CI fix)
- **CI / Подпись**: Починен конфликт пакетов при обновлении APK. Причина — неявное использование `~/.android/debug.keystore` и ненадёжное декодирование секрета в `build.yml`.
- **Keystore**: постоянный файл `app/keystore/treadmill.keystore` закоммичен в репо; явный `signingConfigs.create("stable")` в `app/build.gradle.kts` для debug и release.
- **CI проверки**: шаг `Verify keystore` (жёсткий — падает при отсутствии/повреждении ключа) и шаг `Show APK signature` (информационный, `continue-on-error`).
- **`build.yml`**: удалён шаг `Decode Debug Keystore` и зависимость от `secrets.DEBUG_KEYSTORE_BASE64`. `gradle assembleDebug` теперь подписывает APK одним и тем же сертификатом на каждом прогоне.
- **`.gitignore`**: добавлен (в репо ранее отсутствовал).

### 2026-10-19 (v2.0+)
- **Архитектура**: Внедрен `TreadmillSession` (вместо монолитного VM) и `TreadmillService` (Foreground Service). Работа в фоне стабилизирована.
- **Протокол**: Команда остановки заменена на `08 01` (сбрасывает таймер дорожки). Добавлен "пинок" скоростью при старте.
- **Автопульс и Зоны**: Добавлен профиль спортсмена. График пульса раскрашен по зонам Карвонена.
- **Баги**: Починено сохранение ручных тренировок, пропуск сегментов (время не накручивается), скорость 1.0 после паузы, утечка GATT.
- **UI**: Убрана прокрутка главного экрана, добавлено подтверждение выхода из тренировки, переработан дизайн настроек, добавлена неоновая иконка.
- **Удалено**: Протокол FitShow, настройки UUID и протокола.

  ### 2026-10-20 (v2.1 - UI & BLE Fixes)
- **UI Редизайн**: Экран "Программы" и "Редактор" полностью переработаны. Карточки стали раскрывающимися, добавлены кнопки "+/-" с авто-повтором, ввод времени в формате ММ:СС (с Wheel Picker), компактные поля. Экран Debug сжат, добавлено логирование тапов/свайпов и экспорт лога. Экран About обновлен, добавлен шаблон тренировки.
- **BLE Фиксы**: 
  - `BleConnection.write()` заменен на `writeSuspend()` (ждет ACK от BLE). Это решило проблему потери команд при быстрой смене сегментов.
  - В `FTMSProtocol` добавлен `sendRaw` и логирование ошибок записи.
  - `setSpeed` и `setIncline` больше не игнорируют команды, если дорожка еще разгоняется.
- **Тренировки**: Прогресс теперь считается по сегментам, а не по времени. При завершении программы лог сохраняется автоматически, а кнопка "Стоп" меняется на "Готово".
- **DebugLog**: Внедрен единый хаб логирования в файл и память.

=== КОНЕЦ ФАЙЛА NOTES.md ===
