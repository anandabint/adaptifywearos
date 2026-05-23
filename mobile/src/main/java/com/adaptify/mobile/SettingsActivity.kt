package com.adaptify.mobile

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings)
        setContentView(buildRootView())
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildRootView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            val p = dp(16)
            setPadding(p, p, p, p)
        }

        // ── SECTION: Appearance ──
        root.addView(sectionHeader("Appearance / Tampilan"))
        root.addView(buildDarkModeRow())
        root.addView(divider())

        // ── SECTION: Language ──
        root.addView(sectionHeader(getString(R.string.language)))
        root.addView(buildLanguageRow())
        root.addView(divider())

        // ── SECTION: App Info ──
        root.addView(sectionHeader(getString(R.string.app_info)))
        root.addView(buildAppInfoSection())

        scroll.addView(root)
        return scroll
    }

    // ── Dark Mode ──
    private fun buildDarkModeRow(): View {
        val prefs = getSharedPreferences("adaptify_prefs", Context.MODE_PRIVATE)
        val isDark = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) ==
                AppCompatDelegate.MODE_NIGHT_YES

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            }
        }
        val label = TextView(this).apply {
            text = getString(R.string.dark_mode)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val switch = SwitchMaterial(this).apply {
            isChecked = isDark
            setOnCheckedChangeListener { _, isChecked ->
                val mode = if (isChecked)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.setDefaultNightMode(mode)
                prefs.edit().putInt("night_mode", mode).apply()
            }
        }
        row.addView(label)
        row.addView(switch)
        return row
    }

    // ── Language ──
    private fun buildLanguageRow(): View {
        val prefs = getSharedPreferences("adaptify_prefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "en") ?: "en"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            }
        }

        listOf("en" to getString(R.string.language_en),
               "in" to getString(R.string.language_id)).forEach { (code, label) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val radioIndicator = TextView(this).apply {
                text = if (currentLang == code) "●" else "○"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(if (currentLang == code)
                    Color.parseColor("#4CAF50") else Color.GRAY)
                setPadding(0, 0, dp(12), 0)
            }
            val langLabel = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            row.addView(radioIndicator)
            row.addView(langLabel)
            row.setOnClickListener {
                if (currentLang != code) {
                    prefs.edit().putString("language", code).apply()
                    applyLanguage(code)
                }
            }
            container.addView(row)
        }
        return container
    }

    private fun applyLanguage(langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        // Restart all activities to apply language
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // ── App Info ──
    private fun buildAppInfoSection(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }

        // App name + logo area
        val headerArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(16); bottomMargin = dp(24)
            }
        }
        headerArea.addView(TextView(this).apply {
            text = "🎵"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            gravity = Gravity.CENTER
        })
        headerArea.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })

        // Get version from BuildConfig
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }

        headerArea.addView(TextView(this).apply {
            text = "${getString(R.string.version)} $versionName"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
        container.addView(headerArea)

        // Info rows
        fun infoRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(dp(120), WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            container.addView(row)
            container.addView(divider())
        }

        infoRow(getString(R.string.version), versionName)
        infoRow(getString(R.string.developer), getString(R.string.developer_name))
        infoRow("Program Studi", "D4 Teknik Informatika")
        infoRow("Platform", "Wear OS + Android")

        // Description
        container.addView(TextView(this).apply {
            text = getString(R.string.app_description)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.GRAY)
            setPadding(0, dp(16), 0, dp(8))
        })

        return container
    }

    // ── Helpers ──
    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.parseColor("#4CAF50"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(16); bottomMargin = dp(4)
        }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1).apply {
            topMargin = dp(2); bottomMargin = dp(2)
        }
        val tv = TypedValue()
        if (theme.resolveAttribute(android.R.attr.listDivider, tv, true)) {
            setBackgroundResource(tv.resourceId)
        } else {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
