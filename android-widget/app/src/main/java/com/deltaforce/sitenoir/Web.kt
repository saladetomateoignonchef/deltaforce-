package com.deltaforce.sitenoir

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Réglages communs des WebView (page de connexion et rafraîchissement en arrière-plan). */
object Web {
    const val URL = "https://www.playdeltaforce.com/events/hq/fr/index.html"

    @SuppressLint("SetJavaScriptEnabled")
    fun configurer(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        // Garde la connexion au site entre deux lancements
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        // Les liens s'ouvrent dans la WebView, pas dans le navigateur
        web.webViewClient = WebViewClient()
    }
}
