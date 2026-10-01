package com.deltaforce.sitenoir

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

/** Notifications de fin de fabrication : une alarme par atelier, programmée à chaque rafraîchissement. */
object Notifs {
    private const val CANAL = "fin_fabrication"
    private const val MAX_ATELIERS = 10
    const val ACTION_FIN = "com.deltaforce.sitenoir.FIN"

    /** Annule les anciennes alarmes puis en programme une pour chaque production en cours. */
    fun programmer(c: Context, data: JSONObject, heureMaj: Long) {
        val alarmes = c.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_ATELIERS) alarmes.cancel(intentionFin(c, i, null))

        val cartes = data.optJSONArray("personal") ?: return
        val maintenant = System.currentTimeMillis()
        for (i in 0 until minOf(cartes.length(), MAX_ATELIERS)) {
            val carte = cartes.getJSONObject(i)
            val fin = Store.fin(carte, heureMaj)
            // "AllowWhileIdle" : l'alarme sonne même si le téléphone est en veille (à quelques minutes près)
            if (fin > maintenant) alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, intentionFin(c, i, carte))
        }
    }

    private fun intentionFin(c: Context, i: Int, carte: JSONObject?): PendingIntent {
        val intent = Intent(c, FinReceiver::class.java).setAction(ACTION_FIN)
        if (carte != null) {
            intent.putExtra("atelier", carte.optString("atelier"))
            intent.putExtra("objet", carte.optString("objet"))
            intent.putExtra("numero", i)
        }
        return PendingIntent.getBroadcast(c, 100 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notifier(c: Context, atelier: String, objet: String, numero: Int) {
        // Depuis Android 13, il faut l'autorisation de l'utilisateur (demandée au lancement de l'appli)
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Fin de fabrication", NotificationManager.IMPORTANCE_DEFAULT))

        val ouvrir = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(c, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(objet.ifEmpty { "Fabrication" } + " est prêt")
            .setContentText("$atelier : fabrication terminée au Site noir")
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        nm.notify(1000 + numero, notif)
    }
}
