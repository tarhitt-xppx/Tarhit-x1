package com.imaginstudio.imagetools.pixellab

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.imaginstudio.imagetools.pixellab.databinding.ActivityQuotesBinding
import com.imaginstudio.imagetools.pixellab.model.AppDefaults

class QuotesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUOTE = "extra_quote"
    }

    private lateinit var binding: ActivityQuotesBinding
    private var currentLang = 0 // 0 = English, 1 = French

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        binding = ActivityQuotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            v.setBackgroundColor(Color.BLACK)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.btnBackQuotes.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        val langs = listOf("English", "Français")
        binding.spinnerQuoteLang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        binding.spinnerQuoteLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLang = position
                populateCategories()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        populateCategories()
    }

    private fun currentMap(): Map<String, List<String>> {
        val code = if (currentLang == 0) "en" else "fr"
        return com.imaginstudio.imagetools.pixellab.model.AssetRepository.loadQuotes(this, code)
    }

    private fun populateCategories() {
        val categories = currentMap().keys.toList()
        binding.spinnerQuoteCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerQuoteCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cat = categories.getOrNull(position) ?: return
                renderQuotes(currentMap()[cat].orEmpty())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (categories.isNotEmpty()) {
            renderQuotes(currentMap()[categories.first()].orEmpty())
        }
    }

    private fun renderQuotes(quotes: List<String>) {
        binding.quotesListContainer.removeAllViews()
        for (q in quotes) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(28, 24, 28, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 16
                layoutParams = lp
                elevation = 2f
            }
            val tv = TextView(this).apply {
                text = q
                textSize = 15f
                setTextColor(Color.parseColor("#0F172A"))
            }
            card.addView(tv)
            card.setOnClickListener {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_QUOTE, q))
                finish()
            }
            binding.quotesListContainer.addView(card)
        }
    }
}
