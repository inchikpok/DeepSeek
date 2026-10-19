package com.custom.treadmill.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.custom.treadmill.MainActivity
import com.custom.treadmill.R
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.ble.BleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TreadmillService : Service() {
    private val notificationId = 1
    private val channelId = "treadmill_channel"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            TreadmillApp.instance.session.stopTreadmill()
            stopSelf()
            return START_NOT_STICKY
        }

        val session = TreadmillApp.instance.session
        
        startForeground(notificationId, buildNotification())

        serviceScope.launch {
            session.treadmillData.collectLatest { updateNotification() }
        }
        serviceScope.launch {
            session.heartRate.collectLatest { updateNotification() }
        }
        serviceScope.launch {
            session.uiElapsedSec.collectLatest { updateNotification() }
        }
        serviceScope.launch {
            session.treadmillState.collectLatest { state ->
                updateNotification()
                if (state == BleConnectionState.DISCONNECTED && !session.workoutState.value.running) {
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildNotification())
    }

    private fun buildNotification(): Notification {
        val session = TreadmillApp.instance.session
        val data = session.treadmillData.value
        val hr = session.heartRate.value
        val sec = session.uiElapsedSec.value

        val timeStr = String.format("%02d:%02d", sec / 60, sec % 60)
        val hrStr = if (hr > 0) "$hr уд/мин" else "—"
        
        val title = "Treadmill Control"
        val content = "%.1f км/ч · %s · %s".format(data.speedKmh, hrStr, timeStr)

        val stopIntent = Intent(this, TreadmillService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Стоп", stopPendingIntent) // Кнопка Стоп
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Тренировка",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает текущие метрики тренировки"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
