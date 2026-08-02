package com.nudge.app

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * 闹钟响铃全屏页。锁屏/亮屏都会弹出来，带大关闭按钮。
 */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏亮屏 + 解锁显示
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val title = intent.getStringExtra("title") ?: "闹钟"
        val note = intent.getStringExtra("note") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = androidx.core.content.ContextCompat.getDrawable(this@RingingActivity, R.drawable.bg_ringing)
            setPadding(40, 40, 40, 40)
        }

        val alarmIcon = TextView(this).apply {
            text = "⏰"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        root.addView(alarmIcon)

        val titleTv = TextView(this).apply {
            text = title
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        root.addView(titleTv)

        val noteTv = TextView(this).apply {
            text = if (note.isNotBlank()) note else "时间到啦"
            textSize = 16f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }
        root.addView(noteTv)

        // 关闭按钮
        val stopBtn = TextView(this).apply {
            text = "关 闭"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
            background = androidx.core.content.ContextCompat.getDrawable(this@RingingActivity, R.drawable.btn_primary)
            setOnClickListener {
                // 停止铃声
                AlarmSoundService.stop(this@RingingActivity)
                finish()
            }
        }
        root.addView(stopBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 40 })

        setContentView(root)

        // 尝试直接解锁(如果设置了锁屏)
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                @Suppress("DEPRECATION")
                km.requestDismissKeyguard(this, null)
            }
        } catch (_: Exception) {}
    }

    override fun onBackPressed() {
        // 禁用返回键，必须点关闭
    }
}
