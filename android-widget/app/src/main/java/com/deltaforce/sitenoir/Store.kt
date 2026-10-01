package com.deltaforce.sitenoir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Sauvegarde des dernières infos lues (JSON + images en petit format). */
object Store {
    private const val TAILLE_IMAGE = 120

    private fun prefs(c: Context) = c.getSharedPreferences("site_noir", Context.MODE_PRIVATE)

    fun donnees(c: Context): JSONObject? =
        prefs(c).getString("data", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun heureMaj(c: Context): Long = prefs(c).getLong("time", 0)

    fun statut(c: Context): String = prefs(c).getString("status", "") ?: ""

    fun statut(c: Context, s: String) {
        prefs(c).edit().putString("status", s).commit()
    }

    fun enregistrer(c: Context, json: String) {
        val obj = JSONObject(json)
        for (section in listOf("personal", "recommend")) {
            val cartes = obj.optJSONArray(section) ?: continue
            for (i in 0 until cartes.length()) telecharger(c, cartes.getJSONObject(i).optString("image"))
        }
        prefs(c).edit()
            .putString("data", json)
            .putLong("time", System.currentTimeMillis())
            .putString("status", "")
            .commit()
    }

    private fun fichier(c: Context, url: String) = File(c.filesDir, "img_" + Integer.toHexString(url.hashCode()) + ".png")

    private fun telecharger(c: Context, url: String) {
        if (url.isEmpty()) return
        val f = fichier(c, url)
        if (f.exists()) return
        runCatching {
            val cnx = URL(url).openConnection() as HttpURLConnection
            cnx.connectTimeout = 10_000
            cnx.readTimeout = 10_000
            val bmp = cnx.inputStream.use { BitmapFactory.decodeStream(it) } ?: return
            // Petites images : un widget a une limite de mémoire
            val echelle = TAILLE_IMAGE.toFloat() / maxOf(bmp.width, bmp.height)
            val petite = if (echelle < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * echelle).toInt().coerceAtLeast(1), (bmp.height * echelle).toInt().coerceAtLeast(1), true)
            else bmp
            f.outputStream().use { petite.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun image(c: Context, url: String): Bitmap? {
        if (url.isEmpty()) return null
        val f = fichier(c, url)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }
}
