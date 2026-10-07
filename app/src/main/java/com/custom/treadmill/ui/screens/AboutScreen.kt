package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("О программе", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Section("Как подключить дорожку") {
                """
                1. Включите беговую дорожку (аварийный ключ должен быть вставлен).
                2. Убедитесь, что Bluetooth на телефоне включён.
                3. На главном экране нажмите «Подкл.» в блоке «Дорожка».
                4. Разрешите доступ к Bluetooth (на Android 12+).
                5. Выберите устройство «FS-…» или «FitShow…».
                """.trimIndent()
            }

            Section("Как подключить пульсометр") {
                """
                1. Наденьте нагрудный датчик, смочите электроды.
                2. На главном экране нажмите «Подкл.» в блоке «Пульсометр».
                3. Выберите устройство с сервисом Heart Rate (0x180D).
                4. Пульс отображается в реальном времени.
                """.trimIndent()
            }

            Section("Кнопки управления дорожкой") {
                """
                • Старт — дорожка запускается с отсчётом 3-2-1 и едет 1 км/ч.
                  Если перед этим была пауза — восстановит прежнюю скорость.

                • Пауза — дорожка останавливается, текущая скорость запоминается.
                  Кнопка превращается в «Продолжить» — по нажатию belt снова
                  отсчитает 3-2-1 и вернётся к прежней скорости.

                • Стоп — полная остановка, скорость сбрасывается. Следующий
                  «Старт» начнётся с 1 км/ч.

                • ЭКСТРЕННАЯ ОСТАНОВКА — немедленно останавливает belt,
                  сбрасывает скорость и наклон, выключает автопульс.
                """.trimIndent()
            }

            Section("Особенности этой дорожки") {
                """
                • Belt стартует ТОЛЬКО по команде 0x07. При старте проходит
                  отсчёт 3-2-1, и belt начинает с 1.0 км/ч.

                • Скорость меняется целыми значениями (1, 2, 3 … 20 км/ч).
                  Дробные (0.5) belt игнорирует.

                • Наклон принимается целыми процентами (0, 1, 2 …).

                • Максимум скорости — 20 км/ч. По умолчанию в настройках
                  стоит 20, но вы можете ограничить меньше для безопасности.

                • Belt пищит на каждую команду. Чтобы не пищал лишнего,
                  повторение скорости отключено (0 сек в настройках).
                """.trimIndent()
            }

            Section("Как создать программу") {
                """
                1. Откройте «Программы» на нижней панели.
                2. Нажмите «Создать» — выберите шаблон или начните с нуля.
                3. Заполните название, описание и сегменты.
                4. Сохраните. Нажмите «Старт» в карточке программы.
                """.trimIndent()
            }

            Section("Импорт / экспорт программ") {
                """
                Формат JSON:
                {
                  "name": "Программа",
                  "description": "Описание",
                  "segments": [
                    {"duration_sec": 300, "speed_kmh": 5.0,
                     "incline_percent": 0.0, "name": "Разминка"}
                  ]
                }

                • Экспорт: карточка программы → «Экспорт».
                • Импорт: кнопка «Импорт» → выберите .json файл.
                """.trimIndent()
            }

            Section("Авторегулировка по пульсу") {
                """
                Включите Switch «Автопульс» на главном экране.
                Настройки целевого пульса — Настройки → «Авторегулировка».

                Каждые N секунд:
                • если пульс выше цели на X → скорость снижается;
                • если ниже цели на Y → скорость повышается.
                """.trimIndent()
            }

            Section("Безопасность") {
                """
                • Максимум скорости по умолчанию 20 км/ч (настраивается).
                • Кнопка «ЭКСТРЕННАЯ ОСТАНОВКА» сбрасывает скорость и наклон.
                • При потере связи с дорожкой программа останавливается.
                """.trimIndent()
            }

            Section("Конфиденциальность") {
                "Приложение работает полностью офлайн. Разрешение INTERNET не запрашивается. " +
                        "Все данные хранятся локально (Room/SQLite)."
            }
        }
    }
}

@Composable
private fun Section(title: String, content: () -> String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(content(), fontSize = 14.sp)
}
