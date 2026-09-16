/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.vevak.app.emergency.EmergencyFeedback
import com.vevak.app.emergency.EmergencyFeedbackMode

@Composable
internal fun EmergencyFeedbackSettings() {
    val context = LocalContext.current
    val feedback = remember { EmergencyFeedback(context) }
    var selected by remember { mutableStateOf(feedback.mode()) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            feedback.setMode(EmergencyFeedbackMode.Notification)
            selected = EmergencyFeedbackMode.Notification
            message = "Retour par notification choisi. Si le canal est bloqué, autorisez-le dans les réglages Android."
        } else message = "Notifications non autorisées. Votre choix précédent est conservé ; l'urgence reste utilisable."
    }
    Text("Retour après un appui d'urgence", style = MaterialTheme.typography.titleMedium)
    Text("Facultatif. Le mode discret reste silencieux. Une vibration confirme seulement l'armement. Les notifications rendent l'utilisation de VeVak visible dans le volet Android.")
    EmergencyFeedbackMode.entries.forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == mode, onClick = {
                if (mode == EmergencyFeedbackMode.Notification && Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    feedback.setMode(mode)
                    selected = mode
                    message = if (mode == EmergencyFeedbackMode.Notification && !feedback.notificationsAvailable()) "Notifications bloquées dans Android. L'envoi d'urgence reste utilisable." else null
                }
            })
            Text(mode.label)
        }
    }
    if (selected == EmergencyFeedbackMode.Notification) {
        Text("Notification sans son, masquée sur l'écran verrouillé et retirée après une minute. Sa disparition n'annule pas l'urgence. Ensuite, utilisez la tuile ou le raccourci pour annuler une attente. Le retour d'envoi ne prouve pas la livraison du SMS.")
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Réglages des notifications Android") }
    }
    message?.let { Text(it) }
}
