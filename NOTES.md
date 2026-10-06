# Treadmill Control — состояние проекта

## Что это
Android-приложение на Kotlin + Compose для управления дорожкой UNixFit-990x 
через BLE (FTMS), с пульсометром и авторегулировкой скорости по пульсу.

## Стек
- Kotlin, Jetpack Compose, MVVM
- Room (SQLite), kotlinx.serialization
- BLE: android.bluetooth.le + собственные обёртки
- Сборка: GitHub Actions → APK артефактом
- Работает ОФЛАЙН (нет INTERNET)

## Структура
app/src/main/java/com/custom/treadmill/
├── TreadmillApp.kt
├── MainActivity.kt
├── ble/          — Uuids, BleManager, BleConnection, FTMSProtocol, FitShowProprietaryProtocol, HeartRateService, ITreadmillProtocol
├── data/         — database (Program, WorkoutLog, DAO, AppDatabase), repository (ProgramRepository, SettingsStore)
├── logic/        — WorkoutManager (пауза/возобновление), HeartRateController
└── ui/
    ├── screens/  — MainScreen, ProgramsScreen, WorkoutScreen, SettingsScreen, HistoryScreen, DebugScreen, AboutScreen
    ├── viewmodels/ — MainViewModel, ProgramViewModel
    ├── components/ — RollingText, HeartRateChart
    ├── Theme.kt (светлая/тёмная)
    ├── AppNavHost.kt (4 вкладки через HorizontalPager + нижняя панель)
    └── Permissions.kt

## Ключевые особенности
- FTMS Control Point: 0x2AD9 (или 0x2A66)
- FitShow proprietary: 0xFFF0/0xFFF2, для UNixFit не работает
- Управление наклоном: команда 03 XX 00, округление до целых %
- Команды скорости/наклона дублируются (belt иногда пропускает)
- Debounce 200ms, 2 попытки, confirm 800ms — для отзывчивости
- HR-auto: не поднимает скорость, если пользователь остановился
- Программа стартует через protocol.start() → delay 600ms → workoutManager.start()
- TreadmillData: speed, incline, distance, calories, elapsed, hr

## Решённые проблемы
- Углы округляются до целых %, дорожка игнорирует 0.5%
- Команда 08 01 (Stop) не работает — используем 02 00 00 (скорость 0)
- HR-авто поднимал скорость после стопа — исправлено (проверка targetSpeed > 0.5)
- Программа не стартовала — теперь start() сначала запускает дорожку

## Что можно улучшить (backlog)
- Экспорт в FIT/TCX
- Зоны пульса на графике
- Голосовые подсказки (TTS)
- Плавающая кнопка экстренного стопа
- Автопереподключение BLE
- Сравнение тренировок по программам

## Демо-данные
В Журнале ⋮ → «Загрузить демо-данные» — 20 тренировок с трендом пульса.
