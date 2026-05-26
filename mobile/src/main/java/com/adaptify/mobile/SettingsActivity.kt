package com.adaptify.mobile

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rootView = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setupBackButton()
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}