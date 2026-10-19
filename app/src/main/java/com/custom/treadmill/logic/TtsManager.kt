package com.custom.treadmill.logic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Русский язык не поддерживается")
            } else {
                isInitialized = true
                tts?.setSpeechRate(1.0f) // Обычная скорость
            }
        } else {
            Log.e("TTS", "Ошибка инициализации TTS")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            // QUEUE_FLUSH прерывает текущую фразу, чтобы не было задержек
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "treadmill_tts")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
