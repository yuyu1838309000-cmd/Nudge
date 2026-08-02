package com.nudge.app

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    private lateinit var alarmList: LinearLayout
    private lateinit var alarmCountText: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        alarmList = findViewById(R.id.alarmList)
        alarmCountText = findViewById(R.id.alarmCountText)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<TextView>(R.id.addBtn).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        startTicker()
    }

    private fun startTicker() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                // 只刷新倒计时项的时间文本
                val items = AlarmStore.load(this@AlarmActivity)
                var changed = false
                for (item in items) {
                    if (item.type == "countdown" && item.enabled) {
                        changed = true
                    }
                }
                if (changed) refreshList()
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun showAddDialog() {
        val options = arrayOf("定时闹钟", "倒计时")
        android.app.AlertDialog.Builder(this)
            .setTitle("添加闹钟")
            .setItems(options) { _, which ->
                if (which == 0) showTimePicker() else showCountdownDialog()
            }
            .show()
    }

    private fun showTimePicker() {
        val now = java.util.Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val message = "提醒时间 ${String.format("%02d:%02d", h, m)}"
            AlarmStore.add(this, "alarm", h, m, message, 0)
            Toast.makeText(this, "已设置 ${String.format("%02d:%02d", h, m)}", Toast.LENGTH_SHORT).show()
            refreshList()
        }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true)
            .show()
    }

    private fun showCountdownDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "输入分钟数，如 5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
        }
        val pad = android.widget.LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(20, 8, 20, 0)
        }
        for (preset in intArrayOf(1, 5, 10, 15, 30)) {
            val btn = TextView(this).apply {
                text = "${preset}分"
                textSize = 13f
                setTextColor(Color.parseColor("#8B9EFF"))
                gravity = Gravity.CENTER
                setPadding(16, 10, 16, 10)
                background = androidx.core.content.ContextCompat.getDrawable(this@AlarmActivity, R.drawable.btn_outline)
                setOnClickListener {
                    val t = android.widget.Toast.makeText(this@AlarmActivity, "已选择 $preset 分钟", Toast.LENGTH_SHORT)
                    t.show()
                    input.setText(preset.toString())
                }
            }
            pad.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 })
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        container.addView(input)
        container.addView(pad)

        android.app.AlertDialog.Builder(this)
            .setTitle("倒计时")
            .setView(container)
            .setPositiveButton("开始") { _, _ ->
                val mins = input.text.toString().trim().toIntOrNull() ?: 0
                if (mins <= 0) {
                    Toast.makeText(this, "请输入有效分钟数", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val seconds = mins * 60L
                AlarmStore.add(this, "countdown", 0, 0, "倒计时 $mins 分钟", seconds)
                Toast.makeText(this, "倒计时 $mins 分钟已开始", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshList() {
        val items = AlarmStore.load(this).sortedBy { it.triggerAt }
        alarmList.removeAllViews()
        alarmCountText.text = "${items.size} 个闹钟"

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "还没有闹钟\n点击右上角「添加」"
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                setPadding(0, 60, 0, 0)
            }
            alarmList.addView(empty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            return
        }

        for (item in items) {
            alarmList.addView(buildItemView(item), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 })
        }
    }

    private fun buildItemView(item: AlarmStore.AlarmItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmActivity, R.drawable.card_bg)
            setPadding(16, 14, 10, 14)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val timeTv = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 2, 0, 0)
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        if (item.type == "countdown") {
            val remain = item.triggerAt - System.currentTimeMillis()
            if (remain > 0 && item.enabled) {
                timeTv.text = String.format("%02d:%02d", remain / 60000, (remain % 60000) / 1000)
            } else {
                timeTv.text = "00:00"
            }
            subTv.text = "⏱️ ${item.message} · 已结束"
        } else {
            timeTv.text = String.format("%02d:%02d", item.hour, item.minute)
            val nextStr = sdf.format(Date(item.triggerAt))
            subTv.text = "⏰ ${item.message} · 下次 $nextStr"
        }
        if (!item.enabled) {
            timeTv.alpha = 0.35f
            subTv.alpha = 0.35f
        }
        textCol.addView(timeTv)
        textCol.addView(subTv)
        card.addView(textCol)

        val delBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#F87171"))
            setPadding(10, 8, 10, 8)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#33F87171")),
                null, null
            )
            setOnClickListener {
                AlarmStore.remove(this@AlarmActivity, item.id)
                refreshList()
            }
        }

        val switch = Switch(this).apply {
            isChecked = item.enabled
            setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
                AlarmStore.toggle(this@AlarmActivity, item.id, checked)
                refreshList()
            }
        }
        card.addView(delBtn)
        card.addView(switch)
        return card
    }
}
