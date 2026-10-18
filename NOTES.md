# Treadmill Control — состояние проекта

Android-приложение на Kotlin + Compose для управления беговой дорожкой
UNixFit-990x (модуль FitShow FS-E629DD) через Bluetooth Low Energy (FTMS),
с поддержкой нагрудного пульсометра и авторегулировкой скорости по пульсу.

**Сборка:** GitHub Actions → GitHub Release с APK (прямая ссылка) + артефакт `treadmill-debug-apk` (zip).
**Работает полностью офлайн** — разрешение `INTERNET` намеренно не запрашивается.

---

## Стек

- **Язык:** Kotlin 1.9.22, JVM target 17
- **UI:** Jetpack Compose (BOM 2024.02.00, Material 3 БЕЗ MenuAnchorType)
- **Архитектура:** MVVM + StateFlow
- **База:** Room 2.6.1 (SQLite)
- **Сериализация:** kotlinx.serialization 1.6.3
- **BLE:** `android.bluetooth.le` + собственные обёртки
- **Сборка:** Gradle 8.6, AGP 8.2.2, KSP 1.9.22-1.0.17
- **SDK:** min 21, target/compile 34

---

## Структура проекта
app/src/main/java/com/custom/treadmill/
├── TreadmillApp.kt — Application, ручной DI
├── MainActivity.kt — точка входа, тема из настроек
│
├── ble/
│ ├── Uuids.kt — UUID сервисов и характеристик
│ ├── BleManager.kt — сканирование BLE
│ ├── BleConnection.kt — обёртка BluetoothGatt
│ ├── ITreadmillProtocol.kt — интерфейс + TreadmillData
│ ├── FTMSProtocol.kt — стандартный FTMS (рабочий)
│ ├── FitShowProprietaryProtocol.kt — proprietary (не работает на нашей)
│ └── HeartRateService.kt — чтение пульса (0x180D)
│
├── data/
│ ├── database/ — Room
│ └── repository/
│ ├── ProgramRepository.kt — CRUD, JSON, демо
│ └── SettingsStore.kt — SharedPreferences + JSON
│
├── logic/
│ ├── WorkoutManager.kt — программа + пауза/пропуск/onFinish
│ └── HeartRateController.kt — авторегулировка
│
└── ui/
├── Theme.kt, AppNavHost.kt, Permissions.kt
├── components/HeartRateChart.kt, RollingText.kt
├── screens/
│ ├── MainScreen.kt
│ ├── ProgramsScreen.kt
│ ├── WorkoutScreen.kt
│ ├── SettingsScreen.kt
│ ├── HistoryScreen.kt
│ ├── DebugScreen.kt
│ └── AboutScreen.kt
└── viewmodels/MainViewModel.kt, ProgramViewModel.kt


---

## BLE — детали

### UUID (`Uuids.kt`)

- FTMS Service: `0x1826`
- FTMS Control Point: `0x2AD9` (0x2A66 тоже есть, но не используется)
- FTMS Treadmill Data: `0x2ACD` (notify)
- FTMS Feature: `0x2ACC`, Status: `0x2ADA`
- HR Service: `0x180D`, Measurement: `0x2A37`
- FitShow proprietary: `0xFFF0` (write `0xFFF2`, notify `0xFFF1`) — **не работает**

### Протокол belt'а FS-E629DD (проверено на железе)

| Команда | Что делает | Работает? |
|---|---|---|
| `02 XX XX` | Speed, км/ч × 100 LE | только когда belt едет |
| `07` | Start / resume | belt в countdown 3-2-1 → 1.0 км/ч |
| `03 XX XX` | Incline, % × 10 LE | применяется с задержкой 5-10 сек |
| `02 00 00` | Stop (скорость → 0) | да |
| `08 01` | Standard Stop/Pause | игнорируется |
| `00` | Request Control | belt молча принимает |
| `01` | Reset | не работает |

### Критичные особенности протокола

1. **Правильный старт:** отдельный write `00`, пауза 200 мс, write `07`.
   Склеенная посылка `00 07` **не работает** — нужны два write.
   Belt уходит в countdown 3-2-1, потом едет 1.0 км/ч.

2. **Смена скорости:** только `02 XX XX`. Никаких `00`, никаких `07`
   перед ней. `07` сбрасывает скорость на 1.0 через 4 сек.

3. **Скорость дробная** (0.1 км/ч) — работает, проверено.

4. **Наклон применяется медленно (5-10 сек).** Belt присылает кадры
   `84 04` без incline ~5 сек, потом `8C 04` с incline.
   Это НОРМА, не баг.

5. **`parseTreadmillData`:** если флаг `1 shl 3` (incline) отсутствует →
   `inclinePercent = 0.0`. НЕ сохранять предыдущее. Belt НЕ присылает
   флаг при incline=0, и если сохранять — incline застревает на 5%.

6. **Belt пищит на любую команду.** Поэтому:
   - `setSpeed`/`setIncline` при `speedKmh < 0.5` → только запоминают
     (`lastRequestedSpeed/Incline`), не пишут на belt
   - `stop()` при уже стоящем belt → без команды
   - **retry incline не делается** (только писк каждые 5 сек)

7. **Sleep mode:** belt подключён по BLE, но мотор не реагирует.
   Wake-up burst `00 01 00` **не работает**. Только физическая кнопка.
   В UI: `waitForBeltRunning` timeout → «Дорожка не отвечает...».

8. **Retry-loop только для скорости** (belt игнорирует ~5-20% команд).
   Условия: `beltSpeed >= 0.5 && targetSpeed >= 0.5 && 5s since last`.

9. **Belt продолжает ехать после программы.** `WorkoutManager.onFinish`
   → `protocol.stop()`.

10. **Belt считает elapsed с момента своего включения**, а не тренировки,
    и продолжает считать на паузе. В приложении свои метрики
    (`uiElapsedSec`, `uiDistanceKm`, `uiCalories`).

### Определение дорожек (`BleManager.isTreadmillDevice`)

Имя начинается с `FS-`, содержит `fitshow`, или в рекламе есть сервис `0x1826`.

---

## Архитектура BLE (FTMSProtocol)

- **Без senders / SharedFlow** — прямые write в suspend-функциях.
- **Без debounce, без collectLatest, без подтверждений** — упростили.
- `setSpeed(v)` / `setIncline(p)` — прямая отправка (с проверкой `speed < 0.5`).
- `start()` — `00` + пауза 200 + `07`.
- `ping07()` — публичный метод для повторной отправки `07` из VM.
- `stop()` — `02 00 00` (если belt уже стоит — пропускается).
- **Retry-loop только для скорости.**
- **`setIncline` оптимистично обновляет UI** (`_data.value.copy(inclinePercent = t)`),
  т.к. belt применяет медленно. Иначе retry сходит с ума.

---

## Логика тренировки

### `WorkoutManager.kt`

- `start(scope, program, onSetSpeed, onSetIncline, onFinish)`
- `pause` / `resume` / `togglePause`
- `skipCurrentSegment()` — пропускает текущий, учитывает remaining в total;
  в `launchLoop` на resume (`segmentElapsedSec > 0`) сразу отправляет команды
- `stop()` — полный сброс
- `onFinish` вызывается в конце программы → belt останавливается

### `MainViewModel`

**Старт ручной (`startTreadmill`):**
1. Если `!isResume` → `resetMetrics()`
2. `protocol.start()` → `waitForBeltRunning(0.9, 30000)`
3. Если belt не поехал — сообщение «нажмите кнопку»
4. Sync `targetSpeed`/`targetIncline` с belt'ом (если > 1.0 и > 0)

**Старт программы (`startWorkout`):**
1. `protocol.start()` → `waitForBeltRunning`
2. `workoutManager.start(..., onFinish = { protocol.stop(); _isPaused=false })`

**Пауза (`pauseTreadmill` / `pauseWorkout`):**
- main: запоминает speed в `speedBeforePause`, `_isPaused=true`, `protocol.stop()`
- program: `workoutManager.pause()` + `protocol.stop()`

**Стоп (`stopTreadmill`):**
- Если идёт программа → `stopWorkout()` (с сохранением лога)
- Иначе → `_isPaused=false`, `protocol.stop()`

**`waitForBeltRunning(minSpeed=0.9, timeout=30s)`:**
- каждые 4 сек `ping07()`
- возвращает `true` если belt едет, `false` при таймауте

**Свои метрики:**
- цикл 1 сек: если `speed < 0.3` — пропуск, иначе
  `uiElapsedSec++`, `uiDistanceKm += speed/3600`, `uiCalories = distance * 60`
- На паузе/стопе метрики стоят
- `resetMetrics()` на `startWorkout` и `startTreadmill` (если не resume)

**`saveWorkoutLog`** — идемпотентна через `workoutLogSaved`.
Берёт данные из своих счётчиков, не из belt'а.

### Авторегулировка (`HeartRateController.kt`)

- TARGET (целевой) или ZONE (мин-макс)
- При превышении: −stepKmh, при недостатке: +stepKmh
- Интервал 30 сек
- Не работает, если `targetSpeed <= 0.5`
- Останавливается при потере дорожки

---

## Настройки (`AppSettings`)

| Поле | Назначение | По умолчанию |
|---|---|---|
| `protocol` | FTMS / FitShow | FTMS |
| `themeMode` | SYSTEM / LIGHT / DARK | SYSTEM |
| `manualWriteUuid` / `manualNotifyUuid` | для FitShow | пусто |
| `minSpeedKmh` / `maxSpeedKmh` | границы | 1.0 / **20.0** |
| `maxInclinePercent` | максимум наклона | 15.0 |
| `speedStepKmh` | шаг кнопок +/− скорости | 0.5 |
| `hrEnabled` | автопульс | false |
| `hrMode` | TARGET / ZONE | TARGET |
| `targetHr` | целевой пульс | 130 |
| `zoneMin` / `zoneMax` | зона | 120 / 140 |
| `thresholdHigh` / `thresholdLow` | X / Y | 5 / 5 |
| `intervalSec` | интервал | 30 |
| `stepKmh` | шаг HR-авто | 0.5 |
| `sendStartCommand` | отправлять `0x07` в start | true |

**Удалено:** `inclineStep` — шаг наклона всегда `1.0` (belt не понимает 0.5%).
**Удалено:** `speedKeepAliveSec` — keep-alive не используется, belt держит скорость сам.

---

## Программы тренировок

### JSON формат
```json
{
  "name": "Интервальная тренировка",
  "description": "5 интервалов",
  "segments": [
    { "duration_sec": 300, "speed_kmh": 5.0, "incline_percent": 0.0, "name": "Разминка" }
  ]
}
```

UI «Программы»
Поиск, сортировка, шаблоны (Пустая / Интервалы 4×3 / Пирамида / Горки / Fartlek)

Редактор сегментов: ↑/↓/⎘/🗑

Дублирование программы (⎘ у названия)

Кнопка «Старт» дизейблится без подключённой дорожки.
При тапе → AlertDialog «Дорожка не подключена».

Редактор сегмента
Имя, длительность (сек), скорость, наклон

Фильтрация ввода, парсинг запятой и точки

UI
Нижняя навигация
4 вкладки через HorizontalPager: Главная / Программы / Журнал / Настройки.
Debug и About — внутри Настроек.

Главный экран
Заголовок «Treadmill Control» + имя подключённой дорожки (синим)

Плитки Дорожка / Пульсометр (Подкл./Откл. + имя)

Плитки Скорость / Наклон с кнопками −/+ (шаг из speedStepKmh / 1.0)

Метрики по центру: Пульс, Время, Дистанция, Калории

Карточка Автопульс с Switch и кнопкой «Настроить»

Кнопки: Старт / Пауза-Продолжить / Стоп.
Экстренной остановки НЕТ.
Все дизейблятся, если не READY (кроме Паузы при _isPaused=true).

Экран тренировки
Тёмная тема, не прокручивается

Прогресс: «Сегмент N/M», остаток, общий %

MiniControl Скорость / Наклон

График пульса (30 мин), растягивается по высоте

Метрики (свои, из VM)

Кнопки: Пауза-Продолжить / Пропустить / Стоп.
Экстренной и Сброса НЕТ.

Анимация чисел
RollingText — прокрутка снизу-вверх, 180 мс.

Тема
SYSTEM / LIGHT / DARK, статус-бар тоже перекрашивается.

SettingsScreen
LazyColumn, SettingsCard (rounded 16 dp), EnumDropdown (ExposedDropdownMenuBox)

SliderSetting (слайдер + значение в Surface)

Секции: Внешний вид, Протокол, Скорость, Наклон, Автопульс, О приложении

.menuAnchor() без параметров — BOM 2024.02.00, нет MenuAnchorType

Нет поля «Шаг кнопок» в разделе Наклон (удалено)

Журнал и статистика
Вкладка «Список»: все тренировки с датой, программой, длительностью, км, ккал, пульсом.

Вкладка «Статистика»:

Общая сводка (тренировок, часов, км, ккал)

Пульс (средний / максимальный / лучший средний)

Тренд (при 4+ тренировках) — сравнение первой и второй половины

Два графика (средний и максимальный пульс по датам)

Демо-данные: ⋮ → «Загрузить демо-данные» (20 тренировок за 10 недель).

Debug-экран
Тёмный фон, шапка: Очистить / 💾 Сохранить (txt файл)

Карточка статуса (имя, состояние, протокол, кол-во записей)

Ручной hex (02 96 00 = 1.5 км/ч) — одна посылка = один write

3 таба:

Лог — прокрутка, цвета <- зелёный, -> синий, FTMS: жёлтый

Справочник — Запустить belt, +1/−1 км/ч, +0.5/−0.5 км/ч,
скорости (02 XX XX — только на ходу), наклоны (только целые %),
управление (00, 07, 02 00 00)

Конструктор — ввод скорости/наклона вручную → hex, Запустить belt

Писк на Debug-команды — норма (belt пищит на любую запись).

Сборка
.github/workflows/build.yml
Триггеры: push в main/master, manual

JDK 17 (temurin)

gradle/actions/setup-gradle@v4 с gradle-version: '8.6' (обязательно! 9.x несовместим с AGP 8.2.2)

gradle assembleDebug --no-daemon --no-configuration-cache
(configuration-cache на AGP 8.2.2 падает)

permissions: contents: write (для релиза)

GitHub Release с APK-файлом (прямая ссылка на скачивание)

Плюс артефакт treadmill-debug-apk (zip) — запасной вариант

build.gradle.kts — версии
versionCode = 9      // поднимать перед каждой сборкой
versionName = "1.9"

Обновление без удаления
Увеличить versionCode (9 → 10 → 11)

Push → Actions → скачать APK из Release

Открыть на телефоне → «Обновить»

Данные сохраняются

Удаление нужно, только если:

не подняли versionCode

или APK подписан другим ключом

gradle.properties
org.gradle.caching=true
org.gradle.parallel=true
# org.gradle.configuration-cache=true  — НЕ включать (AGP 8.2.2)

Gradle wrapper
В CI не используется. Сборка через setup-gradle@v4 + gradle-version: 8.6

Тесты — статус
Пройдено
A1: подключение к разбуженному belt'у

A2: подключение к спящему — belt не отвечает, показывается сообщение

B1: старт с разбуженного — belt едет за 3 сек

B2: старт со спящего — timeout 30 сек, сообщение

B3: смена скорости (2.0 → 3.0 → 4.0)

B4: дробная скорость (3.1, 3.2)

B5: наклон (задержка 5-10 сек, работает)

B7: стоп

C1: кнопка «Старт» дизейблится без подключения

C3: программа разгоняется на первом сегменте (9.5 → 16.0)

C5: журнал сохраняет тренировку

C6: стоп с главного во время программы — программа останавливается

D1: таймер точен (±2 сек за 60 сек)

D2: метрики замирают на паузе

Не проверено на железе после последних правок
B6: пауза/продолжить с восстановлением скорости

C4: пауза во время программы

Известные подводные камни (НЕ ПЕРЕДЕЛЫВАТЬ)
MenuAnchorType — нет в BOM 2024.02.00. Только .menuAnchor() без параметров.

Gradle 9.x — несовместим с AGP 8.2.2. Только 8.6.

Configuration cache — отключён (AGP 8.2.2 падает).

inclineStep — удалён из AppSettings, шаг наклона хардкод 1.0.

Wake-up burst 00 01 00 — удалён из initialize(), belt его игнорирует.

Retry incline — удалён, только скорость.

parseTreadmillData — при отсутствии флага incline = 0.

setSpeed/setIncline — при speed < 0.5 не пишут на belt.

GitHub Release требует permissions: contents: write.

MenuAnchorType — если обновите Compose BOM до 2024.09+ — можно добавить.

Backlog
FIT/TCX экспорт тренировок (Strava / Garmin)

Автопереподключение BLE при потере связи

Зоны пульса на графике (цветные полосы)

TTS голосовые подсказки («Интервал 3 из 4, 15 км/ч»)

Плавающая emergency stop (если понадобится)

Сравнение одноимённых программ (тренд)

Widget / Quick Settings Tile

Уведомление с метриками на локскрине

Экспорт всей БД одним ZIP

Диагностика
Если не работает наклон / скорость / программа:

Открыть Debug (Настройки → Дополнительно → Debug)

Повторить проблему

Нажать 💾 Сохранить → файл treadmill_log_YYYY-MM-DD_HH-mm-ss.txt

Содержимое — в чат

В логе будет видно:

Все сервисы/характеристики при подключении

Команды (синие ->), ответы (зелёные <-), логи FTMS (жёлтые)

Все ошибки

Changelog
2026-10-18 (текущее состояние)
Belt FS-E629DD полностью разобран, протокол зафиксирован

FTMSProtocol переписан с нуля: без senders, без debounce, прямые write

Старт = 00 + пауза + 07 (без скорости)

waitForBeltRunning(0.9, 30s) с ping07 каждые 4 сек

onFinish в WorkoutManager → belt останавливается после программы

skipCurrentSegment — кнопка «Пропустить» в WorkoutScreen

Свои метрики (uiElapsedSec, uiDistanceKm, uiCalories)

isPaused для главного экрана (Пауза/Продолжить)

Наклон: оптимистичный UI + отсутствие retry + incline=0 при отсутствии флага

Удалены: emergency stop, inclineStep, speedKeepAliveSec, wake-up burst, retry incline

SettingsScreen: LazyColumn, слайдеры, dropdown'ы, без шага наклона

Debug: 3 таба (Лог / Справочник / Конструктор)

GitHub Release с APK (прямая ссылка)

Тесты A1-B7, C1-C6, D1-D2 пройдены (кроме B6, C4)

