package com.custom.treadmill

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// Центральный хаб логирования.
//
// Пишет одновременно в:
//   - StateFlow<List<String>> — для UI (DebugScreen)
//   - Logcat (фильтр: TC.TAG)
//   - Файл filesDir/logs/debug-YYYY-MM-DD.log — для экспорта
//
// Формат: [HH:mm:ss.SSS][TAG] сообщение
//
// Теги: SESSION, VM, UI, NAV, TR, HR, PROTO, WM, SVC, SYS, ERR-префикс
object DebugLog {

    private const val MAX_LINES = 3000
    private const val LOG_KEEP_DAYS = 3

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val writer = Executors.newSingleThreadExecutor()

    @Volatile private var logFile: File? = null
    @Volatile private var logDay: String? = null
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            cleanupOld(dir)
            rotate(dir)
            d("SYS", "DebugLog готов, dir=${dir.absolutePath}")
        } catch (e: Exception) {
            Log.w("DebugLog", "init failed: ${e.message}")
        }
    }

    private fun cleanupOld(dir: File) {
        try {
            val cutoff = System.currentTimeMillis() - LOG_KEEP_DAYS * 24L * 3600 * 1000
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        } catch (_: Exception) {}
    }

    private fun rotate(dir: File) {
        val today = dayFmt.format(Date())
        if (logDay != today) {
            logDay = today
            logFile = File(dir, "debug-$today.log")
        }
    }

    fun d(tag: String, msg: String) {
        val line = "[${timeFmt.format(Date())}][$tag] $msg"
        _lines.update { old ->
            (old + line).let { if (it.size > MAX_LINES) it.takeLast(MAX_LINES) else it }
        }
        Log.d("TC.$tag", msg)
        val f = logFile ?: return
        writer.execute {
            try {
                if (dayFmt.format(Date()) != logDay) f.parentFile?.let { rotate(it) }
                f.appendText(line + "\n")
            } catch (e: Exception) {
                Log.w("DebugLog", "write failed: ${e.message}")
            }
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val text = buildString {
            append(msg)
            if (t != null) append(" | ${t.javaClass.simpleName}: ${t.message}")
        }
        d("ERR/$tag", text)
    }

    fun clear() {
        _lines.value = emptyList()
        writer.execute {
            try { logFile?.writeText("") } catch (_: Exception) {}
        }
    }

    fun exportText(): String = buildString {
        appendLine("=== Treadmill Control debug log ===")
        appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        appendLine("Memory lines: ${_lines.value.size}")
        val f = logFile
        if (f != null && f.exists()) {
            appendLine("File: ${f.name} (${f.length()} bytes)")
            appendLine("-----------------------------------")
            try { append(f.readText()) }
            catch (e: Exception) { appendLine("read failed: ${e.message}") }
        } else {
            appendLine("-----------------------------------")
            append(_lines.value.joinToString("\n"))
        }
    }
}
