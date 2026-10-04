package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("О программе", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Section("Как подключить дорожку") {
            """
            1. Включите беговую дорожку (аварийный ключ должен быть вставлен).
            2. Убедитесь, что Bluetooth на телефоне включён.
            3. На главном экране нажмите «Подключить» в блоке «Дорожка».
            4. Разрешите доступ к Bluetooth (и геолокации — на Android < 12).
            5. Выберите устройство с именем, начинающимся на «FS-» или «FitShow».
            6. После подключения статус станет «подключено».
            """.trimIndent()
        }

        Section("Как подключить пульсометр") {
            """
            1. Наденьте нагрудный датчик, смочите электроды.
            2. На главном экране нажмите «Подключить» в блоке «Пульсометр».
            3. Выберите устройство — сюда попадают только устройства с сервисом Heart Rate (0x180D).
            4. Пульс отображается в реальном времени.
            """.trimIndent()
        }

        Section("Как создать программу") {
            """
            1. Откройте «Тренировочные программы».
            2. Нажмите «Создать».
            3. Заполните название, описание и сегменты: имя, длительность (сек), скорость (км/ч).
            4. Сохраните. Нажмите «Старт» в карточке — начнётся автоматическая смена скорости.
            """.trimIndent()
        }

        Section("Импорт / экспорт программ") {
            """
            Формат JSON:
            {
              "name": "...",
              "description": "...",
              "segments": [
                {"duration_sec": 300, "speed_kmh": 5.0, "name": "Разминка"}
              ]
            }

            • Экспорт: карточка программы → «Экспорт» → выберите файл.
            • Импорт: кнопка «Импорт» → выберите .json файл.
            """.trimIndent()
        }

        Section("Авторегулировка по пульсу") {
            """
            Включите «Авторегулировку по пульсу» в настройках.
            Каждые N секунд:
            • если пульс выше цели на X → скорость снижается на шаг (по умолчанию 0.5 км/ч);
            • если пульс ниже цели на Y → скорость повышается на шаг.
            Настраиваемые параметры: цель/зона, X, Y, интервал, шаг, мин/макс скорость.
            """.trimIndent()
        }

        Section("Отладка протокола") {
            """
            Если стандартный FTMS Control Point не работает для вашей модели:
            1. Откройте экран Debug и подключитесь к дорожке.
            2. В логе увидите все сервисы и характеристики с их UUID.
            3. Настройки → «Протокол» → выберите FitShow и/или укажите UUID вручную.
            4. Через Debug-экран можно отправлять произвольные hex-команды
               (например, «A3 50» — попытка установить скорость 5.0 км/ч).
            """.trimIndent()
        }

        Section("Безопасность") {
            """
            • Максимальная скорость по умолчанию ограничена 12 км/ч.
            • Кнопка «СТОП!» мгновенно устанавливает скорость 0.
            • При потере соединения с дорожкой программа останавливается.
            • При потере пульсометра тренировка продолжается, автокоррекция — нет.
            """.trimIndent()
        }

        Section("Конфиденциальность") {
            "Приложение работает полностью офлайн. Разрешение INTERNET не запрашивается. " +
            "Все данные хранятся локально (Room/SQLite)."
        }
    }
}

@Composable
private fun Section(title: String, content: () -> String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(content(), fontSize = 14.sp)
}
