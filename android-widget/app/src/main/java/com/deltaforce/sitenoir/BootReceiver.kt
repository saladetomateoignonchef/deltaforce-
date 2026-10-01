package com.deltaforce.sitenoir

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Les alarmes sont effacées au redémarrage du téléphone : on les reprogramme. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.donnees(context)?.let { Notifs.programmer(context, it, Store.heureMaj(context)) }
        SiteNoirWidget.majTous(context)
    }
}
