package com.deltaforce.sitenoir

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Le widget d'écran d'accueil : affiche les dernières infos sauvegardées. */
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

        fun majTous(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            val ids = manager.getAppWidgetIds(ComponentName(c, SiteNoirWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, construire(c))
        }

        fun construire(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_site_noir)
            val heureMaj = Store.heureMaj(c)
            val statut = Store.statut(c)
            v.setTextViewText(
                R.id.maj,
                when {
                    statut.isNotEmpty() -> statut
                    heureMaj > 0 -> "maj " + HEURE.format(Date(heureMaj))
                    else -> "Appuie sur ⟳"
                }
            )

            v.removeAllViews(R.id.rangeeProd)
            v.removeAllViews(R.id.rangeeReco)
            Store.donnees(c)?.let { d ->
                remplir(c, v, R.id.rangeeProd, d.optJSONArray("personal"), true, heureMaj)
                remplir(c, v, R.id.rangeeReco, d.optJSONArray("recommend"), false, heureMaj)
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val refresh = Intent(c, RefreshActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.btnRefresh, PendingIntent.getActivity(c, 0, refresh, flags))
            val appli = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.titre, PendingIntent.getActivity(c, 1, appli, flags))
            return v
        }

        private fun remplir(c: Context, v: RemoteViews, rangee: Int, cartes: JSONArray?, production: Boolean, heureMaj: Long) {
            if (cartes == null) return
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
                    if (timer.isNotEmpty()) {
                        // Pas de temps réel : on affiche l'heure de fin calculée au moment du rafraîchissement
                        val (h, m, s) = timer.split(":").map { it.toLong() }
                        val fin = heureMaj + ((h * 60 + m) * 60 + s) * 1000
                        if (fin <= System.currentTimeMillis()) {
                            cell.setTextViewText(R.id.valeur, "Terminé ✓")
                        } else {
                            cell.setTextViewText(R.id.valeur, "fin " + HEURE.format(Date(fin)))
                            cell.setTextViewText(R.id.sous, "en production")
                        }
                    } else {
                        cell.setTextViewText(R.id.valeur, if (carte.optBoolean("termine")) "Terminé ✓" else "—")
                    }
                } else {
                    cell.setTextViewText(R.id.valeur, carte.optString("recompense").ifEmpty { "—" })
                    cell.setTextColor(R.id.valeur, Color.parseColor("#3DDC84"))
                    cell.setTextViewText(R.id.sous, "récomp./h")
                }
                v.addView(rangee, cell)
            }
        }
    }
}
