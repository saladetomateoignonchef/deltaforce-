package com.deltaforce.sitenoir

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button

/** Affiche le site : sert à se connecter une fois, la connexion est ensuite gardée. */
class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        Web.configurer(web)

        findViewById<Button>(R.id.btnMaj).setOnClickListener {
            CookieManager.getInstance().flush()
            startActivity(Intent(this, RefreshActivity::class.java))
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(Web.URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
