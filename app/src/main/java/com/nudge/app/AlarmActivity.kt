package com.nudge.app

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startTicker() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                val items = AlarmStore.load(this@AlarmActivity)
                if (items.any { it.type == "countdown" && it.enabled }) refreshList()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun showAddDialog() {
        val options = arrayOf("定时闹钟", "倒计时")
        android.app.AlertDialog.Builder(this)
            .setTitle("添加")
            .setItems(options) { _, which ->
                if (which == 0) showAlarmDialog() else showCountdownDialog()
            }
            .show()
    }

    // ============ 定时闹钟 ============
    private var pickHour = -1
    private var pickMinute = -1
    private var repeatMode = "once"
    private val weekCheck = mutableMapOf<Int, CheckBox>()

    private fun showAlarmDialog() {
        val now = java.util.Calendar.getInstance()
        pickHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        pickMinute = now.get(java.util.Calendar.MINUTE)
        repeatMode = "once"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
        }

        // 时间
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val timeLabel = TextView(this).apply {
            text = "时间"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val timeValue = TextView(this).apply {
            text = String.format("%02d:%02d", pickHour, pickMinute)
            textSize = 18f
            setTextColor(Color.parseColor("#8B9EFF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(12, 6, 12, 6)
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmActivity, R.drawable.btn_outline)
        }
        timeValue.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                pickHour = h; pickMinute = m
                timeValue.text = String.format("%02d:%02d", h, m)
            }, pickHour, pickMinute, true).show()
        }
        timeRow.addView(timeLabel)
        timeRow.addView(timeValue)
        container.addView(timeRow)

        // 标题
        val titleInput = EditText(this).apply {
            hint = "标题（必填）"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmActivity, R.drawable.edittext_bg)
            setPadding(16, 12, 16, 12)
        }
        container.addView(titleInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })

        // 备注
        val noteInput = EditText(this).apply {
            hint = "备注（可选）"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmActivity, R.drawable.edittext_bg)
            setPadding(16, 12, 16, 12)
        }
        container.addView(noteInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8 })

        // 重复模式
        val repeatLabel = TextView(this).apply {
            text = "重复"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        container.addView(repeatLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14 })

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val rbOnce = RadioButton(this).apply { text = "响一次"; setTextColor(Color.WHITE) }
        val rbDaily = RadioButton(this).apply { text = "每天"; setTextColor(Color.WHITE) }
        val rbWeekly = RadioButton(this).apply { text = "按周几"; setTextColor(Color.WHITE) }
        rbOnce.isChecked = true
        radioGroup.addView(rbOnce)
        radioGroup.addView(rbDaily)
        radioGroup.addView(rbWeekly)
        container.addView(radioGroup)

        // 周几选择 (weekly 时显示)
        val weekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        weekCheck.clear()
        val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0 until 7) {
            val cb = CheckBox(this).apply {
                text = dayNames[i]
                textSize = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#8B9EFF"))
            }
            weekCheck[i + 1] = cb
            weekRow.addView(cb)
        }
        container.addView(weekRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 4 })

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            repeatMode = when (checkedId) {
                rbDaily.id -> "daily"
                rbWeekly.id -> "weekly"
                else -> "once"
            }
            weekRow.visibility = if (repeatMode == "weekly") View.VISIBLE else View.GONE
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("定时闹钟")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val note = noteInput.text.toString().trim()
                val weekdays = weekCheck.filterValues { it.isChecked }.keys.sorted()
                if (repeatMode == "weekly" && weekdays.isEmpty()) {
                    Toast.makeText(this, "请至少选一个周几", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AlarmStore.add(this, "alarm", pickHour, pickMinute, title, note,
                    repeatMode, weekdays, 0)
                Toast.makeText(this, "已设置 ${String.format("%02d:%02d", pickHour, pickMinute)}", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============ 倒计时 ============
    private fun showCountdownDialog() {
        val input = EditText(this).apply {
            hint = "输入分钟数，如 5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
        }
        val pad = LinearLayout(this).apply {
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
                    Toast.makeText(this@AlarmActivity, "已选择 $preset 分钟", Toast.LENGTH_SHORT).show()
                    input.setText(preset.toString())
                }
            }
            pad.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 })
        }
        val titleInput = EditText(this).apply {
            hint = "标题（可选，默认：倒计时）"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
        }
        val noteInput = EditText(this).apply {
            hint = "备注（可选）"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        container.addView(input)
        container.addView(pad)
        container.addView(titleInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })
        container.addView(noteInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8 })

        android.app.AlertDialog.Builder(this)
            .setTitle("倒计时")
            .setView(container)
            .setPositiveButton("开始") { _, _ ->
                val mins = input.text.toString().trim().toIntOrNull() ?: 0
                if (mins <= 0) {
                    Toast.makeText(this, "请输入有效分钟数", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val title = titleInput.text.toString().trim().ifEmpty { "倒计时 $mins 分钟" }
                val note = noteInput.text.toString().trim()
                AlarmStore.add(this, "countdown", 0, 0, title, note, "once", emptyList(), mins * 60L)
                Toast.makeText(this, "倒计时 $mins 分钟已开始", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============ 列表 ============
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
        val titleTv = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#E5E7EB"))
            setPadding(0, 2, 0, 0)
        }
        val subTv = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 1, 0, 0)
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val weekNames = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

        if (item.type == "countdown") {
            val remain = item.triggerAt - System.currentTimeMillis()
            if (remain > 0 && item.enabled) {
                timeTv.text = String.format("%02d:%02d", remain / 60000, (remain % 60000) / 1000)
                subTv.text = "剩余中"
            } else {
                timeTv.text = "00:00"
                subTv.text = "已结束"
            }
            titleTv.text = item.title
            if (item.note.isNotBlank()) {
                subTv.text = "${subTv.text} · ${item.note}"
            }
        } else {
            timeTv.text = String.format("%02d:%02d", item.hour, item.minute)
            titleTv.text = item.title
            val repeatStr = when (item.repeat) {
                "daily" -> "每天"
                "weekly" -> item.weekdays.joinToString(" ") { weekNames[it] }
                else -> "响一次"
            }
            val nextStr = sdf.format(Date(item.triggerAt))
            subTv.text = "$repeatStr · 下次 $nextStr"
            if (item.note.isNotBlank()) {
                subTv.text = "${subTv.text} · ${item.note}"
            }
        }
        if (!item.enabled) {
            timeTv.alpha = 0.35f
            titleTv.alpha = 0.35f
            subTv.alpha = 0.35f
        }
        textCol.addView(timeTv)
        textCol.addView(titleTv)
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
