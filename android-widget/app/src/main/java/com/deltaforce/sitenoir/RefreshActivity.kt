package com.deltaforce.sitenoir

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Lancée par le bouton ⟳ du widget. Écran transparent : charge le site dans une WebView
 * invisible (avec les cookies de connexion), injecte extract.js, récupère le JSON,
 * met à jour le widget puis se ferme.
 */
class RefreshActivity : Activity() {
    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var fini = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "Site noir : rafraîchissement…", Toast.LENGTH_SHORT).show()
        Store.statut(this, "Chargement…")
        SiteNoirWidget.majTous(this)

        val script = assets.open("extract.js").bufferedReader().use { it.readText() }

        web = WebView(this)
        web.alpha = 0f // invisible, mais la page tourne normalement
        setContentView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        Web.configurer(web)
        web.addJavascriptInterface(Pont(), "SiteNoir")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // extract.js se protège lui-même contre une double exécution
                view.evaluateJavascript(script, null)
            }
        }
        web.loadUrl(Web.URL)

        handler.postDelayed({ terminer(null, "Délai dépassé") }, 35_000)
    }

    /** Appelé depuis extract.js (sur un thread à part, d'où le passage par le handler). */
    inner class Pont {
        @JavascriptInterface
        fun resultat(json: String) {
            handler.post { terminer(json, null) }
        }

        @JavascriptInterface
        fun erreur(message: String) {
            handler.post { terminer(null, message) }
        }
    }

    private fun terminer(json: String?, erreur: String?) {
        if (fini) return
        fini = true
        handler.removeCallbacksAndMessages(null)
        val app = applicationContext
        // Le téléchargement des images se fait hors du thread principal
        Thread {
            try {
                if (json != null) Store.enregistrer(app, json) else Store.statut(app, erreur ?: "Erreur")
            } catch (e: Exception) {
                Log.e("SiteNoir", "Erreur enregistrement", e)
                Store.statut(app, "Erreur : " + e.javaClass.simpleName)
            }
            SiteNoirWidget.majTous(app)
        }.start()
        if (erreur != null) Toast.makeText(app, erreur, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
