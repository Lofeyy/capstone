package com.capstone.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Service

class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val stopIntent = Intent(it, PomodoroService::class.java)
            it.stopService(stopIntent)
        }
    }
}
