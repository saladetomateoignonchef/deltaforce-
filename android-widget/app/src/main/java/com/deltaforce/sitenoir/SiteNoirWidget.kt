package com.deltaforce.sitenoir

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le widget d'écran d'accueil. Une liste défilante de 2 lignes :
 * production en cours (compte à rebours en direct + comparaison avec la recommandation),
 * puis recommandations (meilleur gain mis en avant).
 */
class SiteNoirWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, construire(context))
    }

    companion object {
        private const val TAG = "SiteNoir"
        // Couleur de rareté selon la classe lv2…lv6 du site (vert, bleu, violet, or, rouge)
        private val COULEURS = mapOf(
            "lv2" to "#5FC77A", "lv3" to "#4AA3E8", "lv4" to "#A07DE0", "lv5" to "#E08A3C", "lv6" to "#E5534B"
        )
        private val NOMS_COURTS = mapOf(
            "Div. de la cyberguerre" to "Cyberguerre", "Établi d'armure" to "Armure"
        )
        private val VERT = Color.parseColor("#3DDC84")
        private val ORANGE = Color.parseColor("#F0A040")
        private val HEURE = SimpleDateFormat("HH:mm", Locale.FRANCE)

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
            var productionEnCours = false
            try {
                val liste = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
                Store.donnees(c)?.let { d ->
                    val prod = d.optJSONArray("personal") ?: JSONArray()
                    val reco = d.optJSONArray("recommend") ?: JSONArray()
                    val (ligneProd, enCours) = ligne(c, "Production en cours", prod, reco, true, heureMaj)
                    liste.addItem(0, ligneProd)
                    liste.addItem(1, ligne(c, "Recommandations", reco, prod, false, heureMaj).first)
                    productionEnCours = enCours
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
            // Connexion expirée : message en orange, et un appui ouvre l'appli pour se reconnecter
            v.setTextColor(R.id.maj, if (statut == Store.DECONNECTE) ORANGE else Color.parseColor("#99B0B0"))

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val refresh = Intent(c, RefreshActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(R.id.btnRefresh, PendingIntent.getActivity(c, 0, refresh, flags))
            val appli = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            v.setOnClickPendingIntent(R.id.titre, appli)
            v.setOnClickPendingIntent(R.id.maj, appli)

            // Mise à jour du temps restant chaque minute, seulement s'il reste une production en cours
            runCatching { programmerMinute(c, productionEnCours) }.onFailure { Log.e(TAG, "Erreur alarme minute", it) }
            return v
        }

        private fun norm(s: String) = s.replace(Regex("[’`´]"), "'").replace(Regex("\\s+"), " ").trim().lowercase()

        /** "41,556" → 41556 (le site sépare les milliers par une virgule). */
        private fun gain(carte: JSONObject) = carte.optString("recompense").filter { it.isDigit() }.toLongOrNull() ?: -1

        /** La carte du même atelier dans l'autre section (production ↔ recommandation). */
        private fun pendant(carte: JSONObject, autres: JSONArray): JSONObject? {
            for (i in 0 until autres.length()) {
                val o = autres.getJSONObject(i)
                val memeId = carte.optString("id").isNotEmpty() && carte.optString("id") == o.optString("id")
                if (memeId || carte.optString("atelier") == o.optString("atelier")) return o
            }
            return null
        }

        /** Construit une ligne (titre + une case par atelier). */
        private fun ligne(c: Context, titre: String, cartes: JSONArray, autres: JSONArray, production: Boolean, heureMaj: Long): Pair<RemoteViews, Boolean> {
            val rangee = RemoteViews(c.packageName, R.layout.widget_row)
            rangee.setTextViewText(R.id.titreRangee, titre)
            // La liste réutilise ses lignes : on vide les anciennes cases avant d'ajouter les nouvelles
            rangee.removeAllViews(R.id.cellules)

            val meilleurGain = (0 until cartes.length()).maxOfOrNull { gain(cartes.getJSONObject(it)) } ?: -1
            var enCours = false

            for (i in 0 until cartes.length()) {
                val carte = cartes.getJSONObject(i)
                val cell = RemoteViews(c.packageName, R.layout.widget_cell)

                val atelier = carte.optString("atelier")
                val objet = carte.optString("objet")
                cell.setTextViewText(R.id.atelier, NOMS_COURTS[atelier] ?: atelier)
                cell.setTextViewText(R.id.objet, objet.ifEmpty { carte.optString("etat").ifEmpty { "—" } })
                COULEURS[carte.optString("lv")]?.let { cell.setTextColor(R.id.objet, Color.parseColor(it)) }

                val image = Store.image(c, carte.optString("image"))
                if (image != null) cell.setImageViewBitmap(R.id.image, image)
                else cell.setViewVisibility(R.id.image, View.INVISIBLE)

                val autre = pendant(carte, autres)
                val memeObjet = autre != null && objet.isNotEmpty() && norm(objet) == norm(autre.optString("objet"))

                if (production) {
                    val fin = Store.fin(carte, heureMaj)
                    val reste = fin - System.currentTimeMillis()
                    if (reste > 0) {
                        // Temps restant recalculé à chaque redessin (une fois par minute, sans contacter le site)
                        cell.setTextViewText(R.id.valeur, dureeCourte(reste))
                        cell.setTextViewText(R.id.sous, "fin " + HEURE.format(Date(fin)))
                        enCours = true
                    } else {
                        val termine = fin > 0 || carte.optBoolean("termine")
                        cell.setTextViewText(R.id.valeur, if (termine) "Terminé ✓" else "—")
                        if (termine) cell.setTextColor(R.id.valeur, VERT)
                    }
                    // Comparaison avec la recommandation du même atelier
                    if (autre != null && autre.optString("objet").isNotEmpty()) {
                        if (memeObjet) badge(cell, "✓ c'est la reco", VERT)
                        else badge(cell, "⚠ reco : " + autre.optString("objet"), ORANGE)
                    }
                } else {
                    cell.setTextViewText(R.id.valeur, carte.optString("recompense").ifEmpty { "—" })
                    cell.setTextColor(R.id.valeur, VERT)
                    cell.setTextViewText(R.id.sous, "récomp./h")
                    if (meilleurGain > 0 && gain(carte) == meilleurGain) {
                        cell.setInt(R.id.case_, "setBackgroundResource", R.drawable.cell_bg_best)
                        badge(cell, "★ meilleur gain", Color.parseColor("#FFD54F"))
                    }
                }
                rangee.addView(R.id.cellules, cell)
            }
            return rangee to enCours
        }

        /** 6h12 / 25 min / < 1 min (arrondi à la minute supérieure pour ne jamais annoncer trop tôt). */
        private fun dureeCourte(ms: Long): String {
            val minutes = (ms + 59_999) / 60_000
            return when {
                minutes >= 60 -> "%dh%02d".format(minutes / 60, minutes % 60)
                minutes > 1 -> "$minutes min"
                else -> "< 1 min"
            }
        }

        /**
         * Redessine le widget à la minute suivante. Alarme RTC (non réveillante) : écran éteint,
         * elle attend que le téléphone se réveille, donc aucune batterie consommée pendant la veille.
         */
        private fun programmerMinute(c: Context, actif: Boolean) {
            val intent = Intent(c, SiteNoirWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, SiteNoirWidget::class.java))
                )
            val pi = PendingIntent.getBroadcast(c, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmes = c.getSystemService(AlarmManager::class.java)
            alarmes.cancel(pi)
            if (!actif) return
            val minuteSuivante = (System.currentTimeMillis() / 60_000 + 1) * 60_000
            alarmes.setWindow(AlarmManager.RTC, minuteSuivante, 10_000, pi)
        }

        private fun badge(cell: RemoteViews, texte: String, couleur: Int) {
            cell.setViewVisibility(R.id.badge, View.VISIBLE)
            cell.setTextViewText(R.id.badge, texte)
            cell.setTextColor(R.id.badge, couleur)
        }
    }
}
