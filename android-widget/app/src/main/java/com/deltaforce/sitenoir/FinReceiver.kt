package com.deltaforce.sitenoir

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reçoit l'alarme de fin de fabrication : notification + widget redessiné (« Terminé ✓ »). */
class FinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifs.notifier(
            context,
            intent.getStringExtra("atelier") ?: "",
            intent.getStringExtra("objet") ?: "",
            intent.getIntExtra("numero", 0)
        )
        SiteNoirWidget.majTous(context)
    }
}
