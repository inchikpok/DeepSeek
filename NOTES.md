markdown

# Состояние проекта

## Экраны
- MainScreen.kt — главный, скорость/наклон плитками, метрики
- ProgramsScreen.kt — список + редактор + шаблоны + поиск + сортировка
- WorkoutScreen.kt — прогресс + показатели + график пульса
- SettingsScreen.kt — тема, протокол, скорость, наклон, автопульс
- HistoryScreen.kt — список + статистика + демо-данные
- DebugScreen.kt — лог BLE + отправка hex
- AboutScreen.kt — инструкция

## Текущая версия AppSettings
- protocol, themeMode, min/maxSpeedKmh, inclineStep, maxInclinePercent
- hrEnabled, hrMode, targetHr, zone*, threshold*, intervalSec, stepKmh
- manualWriteUuid, manualNotifyUuid

## Протоколы
- FTMS: 0x1826, Control Point 0x2AD9 / 0x2A66
- FitShow: proprietary, auto-detect writable char
- HR: 0x180D, measurement 0x2A37

## Что уже работает
- BLE подключение, скорость, наклон, автопульс
- Программы с импортом/экспортом JSON
- График пульса в реальном времени
- Статистика + демо-данные
- Светлая/тёмная тема
