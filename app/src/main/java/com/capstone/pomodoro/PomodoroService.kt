package com.capstone.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.capstone.pomodoro.R

class PomodoroService : Service() {

    private val binder = LocalBinder()
    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 25 * 60 * 1000 // Default to 25 minutes
    private var taskId: String? = null

    companion object {
        const val CHANNEL_ID = "PomodoroServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // Create notification channel
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        timeLeftInMillis = intent?.getLongExtra("TIME_LEFT", 25 * 60 * 1000) ?: 25 * 60 * 1000
        taskId = intent?.getStringExtra("TASK_ID")

        // Start the countdown timer
        startTimer()

        // Create the notification with resume and stop actions
        val resumeIntent = Intent(this, PomodoroActivity::class.java)
        val resumePendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for Android 12+
        )

        val stopIntent = Intent(this, StopServiceReceiver::class.java)
        val stopPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for Android 12+
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText("You have an ongoing task. Want to come back?")
            .setSmallIcon(R.drawable.baseline_notifications_none_24)
            .addAction(R.drawable.baseline_replay_24, "Resume", resumePendingIntent)
            .addAction(R.drawable.baseline_stop_24, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        startForeground(1, notification)

        return START_NOT_STICKY
    }

    private fun startTimer() {
        timer?.cancel()

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
            }

            override fun onFinish() {
                // Optionally notify the user or update UI when timer finishes
                stopSelf() // Stop the service when timer finishes
            }
        }.start()
    }

    inner class LocalBinder : Binder() {
        fun getService(): PomodoroService = this@PomodoroService
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
