/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.emergency

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.core.content.ContextCompat
import com.vevak.app.R
import com.vevak.app.ui.SafetyCenterActivity
import kotlinx.coroutines.launch

enum class EmergencyFeedbackMode(val label: String) {
    Silent("Aucun retour — discret"), Vibrate("Vibration courte à l'armement"), Notification("Notification temporaire avec annulation");
    companion object {
        fun decode(value: String?) = entries.firstOrNull { it.name == value } ?: Silent
    }
}

/** Optional local feedback only. Never determines whether an emergency is sent. */
class EmergencyFeedback(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("emergency_feedback", Context.MODE_PRIVATE)
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    fun mode() = EmergencyFeedbackMode.decode(prefs.getString("mode", null))
    fun setMode(mode: EmergencyFeedbackMode) {
        prefs.edit().putString("mode", mode.name).apply()
        runCatching {
            manager?.activeNotifications?.filter { it.tag?.startsWith(TAG_PREFIX) == true }
                ?.forEach { manager?.cancel(it.tag, it.id) }
        }
    }
    fun notificationsAvailable(): Boolean = manager?.areNotificationsEnabled() == true &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager?.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun armed(armId: String) { runCatching {
        when (mode()) {
            EmergencyFeedbackMode.Silent -> Unit
            EmergencyFeedbackMode.Vibrate -> {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            EmergencyFeedbackMode.Notification -> show(armId, "Urgence préparée", "Envoi demandé après 4 secondes. Android peut le retarder. Annulable jusqu'à sa prise en charge.", true)
        }
    } }
    fun clear(armId: String) { runCatching { manager?.cancel(TAG_PREFIX + armId, 1) } }
    fun result(armId: String, message: String) { runCatching { show(armId, "Urgence VeVak", message, false) } }

    @android.annotation.SuppressLint("MissingPermission") // notificationsAvailable checks the optional grant.
    private fun show(armId: String, title: String, message: String, cancellable: Boolean) {
        if (mode() != EmergencyFeedbackMode.Notification || !notificationsAvailable()) return
        val channel = NotificationChannel(CHANNEL, "Retour d'urgence volontaire", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager?.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(context, 0, Intent(context, SafetyCenterActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_emergency_tile)
            .setContentTitle(title).setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true).setAutoCancel(true).setTimeoutAfter(60_000L)
        if (cancellable) {
            val intent = Intent(context, EmergencyFeedbackCancelReceiver::class.java).apply {
                action = ACTION_CANCEL
                data = Uri.Builder().scheme("vevak").authority("cancel-feedback").appendPath(armId).build()
                putExtra("arm_id", armId)
            }
            val cancel = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Annuler", cancel).build())
        }
        manager?.notify(TAG_PREFIX + armId, 1, builder.build())
    }
    companion object {
        const val ACTION_CANCEL = "com.vevak.app.action.CANCEL_EMERGENCY_FEEDBACK"
        private const val CHANNEL = "emergency_feedback"
        private const val TAG_PREFIX = "emergency-feedback:"
    }
}

/** Explicit, private receiver: an old action can only cancel its own arm id, never arm. */
class EmergencyFeedbackCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EmergencyFeedback.ACTION_CANCEL) return
        val armId = intent.getStringExtra("arm_id")?.takeIf { it.isNotBlank() } ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                EmergencyShortcutArmController(appContext).cancelIfArmed(armId)
            } catch (_: Exception) {
                EmergencyFeedback(appContext).result(armId, "Annulation non confirmée. L'envoi peut encore être pris en charge.")
            } finally {
                pending.finish()
            }
        }
    }
}
