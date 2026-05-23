package com.example.macromax

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

object BottomNavHelper {

    fun setup(activity: AppCompatActivity, activeTabId: Int) {
        highlight(activity, activeTabId)

        activity.findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            if (activeTabId == R.id.navHome) return@setOnClickListener
            val intent = Intent(activity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
        }
        activity.findViewById<LinearLayout>(R.id.navHistory).setOnClickListener {
            if (activeTabId == R.id.navHistory) return@setOnClickListener
            activity.startActivity(Intent(activity, HistoryActivity::class.java))
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
        }
        activity.findViewById<LinearLayout>(R.id.navReports).setOnClickListener {
            if (activeTabId == R.id.navReports) return@setOnClickListener
            activity.startActivity(Intent(activity, ReportsActivity::class.java))
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
        }
    }

    private fun highlight(activity: AppCompatActivity, activeTabId: Int) {
        data class Tab(val rootId: Int, val iconId: Int, val labelId: Int)
        val tabs = listOf(
            Tab(R.id.navHome,    R.id.navHomeIcon,    R.id.navHomeLabel),
            Tab(R.id.navHistory, R.id.navHistoryIcon, R.id.navHistoryLabel),
            Tab(R.id.navReports, R.id.navReportsIcon, R.id.navReportsLabel),
        )
        val tv = TypedValue()
        activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        val activeColor   = tv.data
        val inactiveColor = Color.argb(153, 255, 255, 255) // white 60%

        for (tab in tabs) {
            val root  = activity.findViewById<LinearLayout>(tab.rootId)
            val icon  = activity.findViewById<ImageView>(tab.iconId)
            val label = activity.findViewById<TextView>(tab.labelId)
            val isActive = tab.rootId == activeTabId
            root.background = if (isActive)
                ContextCompat.getDrawable(activity, R.drawable.nav_tab_selected_bg) else null
            val color = if (isActive) activeColor else inactiveColor
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            label.setTextColor(color)
        }
    }
}
