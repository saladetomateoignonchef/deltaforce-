package com.deltaforce.sitenoir

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le widget d'écran d'accueil. Une liste défilante de 2 lignes :
 * production en cours (avec compte à rebours en direct), puis recommandations.
 */
class SiteNoirWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, construire(context))
    }

    companion object {
        // Couleur de rareté selon la classe lv2…lv6 du site (vert, bleu, violet, or, rouge)
        private val COULEURS = mapOf(
            "lv2" to "#5FC77A", "lv3" to "#4AA3E8", "lv4" to "#A07DE0", "lv5" to "#E08A3C", "lv6" to "#E5534B"
        )
        private val NOMS_COURTS = mapOf(
            "Div. de la cyberguerre" to "Cyberguerre", "Établi d'armure" to "Armure"
        )
        private val HEURE = SimpleDateFormat("HH:mm", Locale.FRANCE)

        private const val TAG = "SiteNoir"

        fun majTous(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            val ids = manager.getAppWidgetIds(ComponentName(c, SiteNoirWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, construire(c))
        }

        fun construire(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_site_noir)
            v.setTextViewText(R.id.titre, c.getString(R.string.app_name) + " · v" + BuildConfig.VERSION_NAME)
            val heureMaj = Store.heureMaj(c)
            var statut = Store.statut(c)

            // Liste défilante : ligne 0 = production, ligne 1 = recommandations
            var prochaineFin = Long.MAX_VALUE
            try {
                val liste = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
                Store.donnees(c)?.let { d ->
                    val (ligneProd, fin) = ligne(c, "Production en cours", d.optJSONArray("personal"), true, heureMaj)
                    prochaineFin = fin
                    liste.addItem(0, ligneProd)
                    liste.addItem(1, ligne(c, "Recommandations", d.optJSONArray("recommend"), false, heureMaj).first)
                }
                v.setRemoteAdapter(R.id.liste, liste.build())
                v.setEmptyView(R.id.liste, R.id.vide)
            } catch (e: Exception) {
                // Plutôt que de rester bloqué, on affiche l'erreur dans le widget
                Log.e(TAG, "Erreur construction widget", e)
                statut = "Erreur : " + e.javaClass.simpleName
                v.setTextViewText(R.id.vide, (e.message ?: e.toString()).take(200))
            }

            v.setTextViewText(
                R.id.maj,
                when {
                    statut.isNotEmpty() -> statut
                    heureMaj > 0 -> "maj " + HEURE.format(Date(heureMaj))
                    else -> ""
                }
            )

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val refresh = Intent(c, RefreshActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.btnRefresh, PendingIntent.getActivity(c, 0, refresh, flags))
            val appli = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.titre, PendingIntent.getActivity(c, 1, appli, flags))

            runCatching { programmerFin(c, prochaineFin) }.onFailure { Log.e(TAG, "Erreur alarme", it) }
            return v
        }

        /** Construit une ligne (titre + 4 cases). Renvoie aussi la prochaine fin de production. */
        private fun ligne(c: Context, titre: String, cartes: JSONArray?, production: Boolean, heureMaj: Long): Pair<RemoteViews, Long> {
            val rangee = RemoteViews(c.packageName, R.layout.widget_row)
            rangee.setTextViewText(R.id.titreRangee, titre)
            var prochaineFin = Long.MAX_VALUE
            if (cartes == null) return rangee to prochaineFin

            for (i in 0 until cartes.length()) {
                val carte = cartes.getJSONObject(i)
                val cell = RemoteViews(c.packageName, R.layout.widget_cell)

                val atelier = carte.optString("atelier")
                cell.setTextViewText(R.id.atelier, NOMS_COURTS[atelier] ?: atelier)
                cell.setTextViewText(R.id.objet, carte.optString("objet").ifEmpty { carte.optString("etat").ifEmpty { "—" } })
                COULEURS[carte.optString("lv")]?.let { cell.setTextColor(R.id.objet, Color.parseColor(it)) }

                val image = Store.image(c, carte.optString("image"))
                if (image != null) cell.setImageViewBitmap(R.id.image, image)
                else cell.setViewVisibility(R.id.image, View.INVISIBLE)

                if (production) {
                    val timer = carte.optString("timer")
                    val fin = if (timer.isNotEmpty()) {
                        val (h, m, s) = timer.split(":").map { it.toLong() }
                        heureMaj + ((h * 60 + m) * 60 + s) * 1000
                    } else 0L
                    val reste = fin - System.currentTimeMillis()
                    if (reste > 0) {
                        // Compte à rebours géré par Android lui-même : il tourne sans l'appli
                        cell.setViewVisibility(R.id.valeur, View.GONE)
                        cell.setViewVisibility(R.id.chrono, View.VISIBLE)
                        cell.setChronometerCountDown(R.id.chrono, true)
                        cell.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + reste, null, true)
                        cell.setTextViewText(R.id.sous, "fin " + HEURE.format(Date(fin)))
                        prochaineFin = minOf(prochaineFin, fin)
                    } else {
                        val termine = fin > 0 || carte.optBoolean("termine")
                        cell.setTextViewText(R.id.valeur, if (termine) "Terminé ✓" else "—")
                        if (termine) cell.setTextColor(R.id.valeur, Color.parseColor("#3DDC84"))
                    }
                } else {
                    cell.setTextViewText(R.id.valeur, carte.optString("recompense").ifEmpty { "—" })
                    cell.setTextColor(R.id.valeur, Color.parseColor("#3DDC84"))
                    cell.setTextViewText(R.id.sous, "récomp./h")
                }
                rangee.addView(R.id.cellules, cell)
            }
            return rangee to prochaineFin
        }

        /** Redessine le widget quand la prochaine production se termine (pour afficher « Terminé ✓ »). */
        private fun programmerFin(c: Context, fin: Long) {
            val intent = Intent(c, SiteNoirWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, SiteNoirWidget::class.java))
                )
            val pi = PendingIntent.getBroadcast(c, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmes = c.getSystemService(AlarmManager::class.java)
            alarmes.cancel(pi)
            if (fin != Long.MAX_VALUE) alarmes.set(AlarmManager.RTC, fin + 1000, pi)
        }
    }
}
