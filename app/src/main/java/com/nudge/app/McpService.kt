    private fun getForegroundApp(): String {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 60000 // 最近1分钟
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            var lastPkg = ""
            var lastTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > lastTime) {
                        lastTime = event.timeStamp
                        lastPkg = event.packageName
                    }
                }
            }
            if (lastPkg.isEmpty()) {
                return "最近没有检测到前台切换事件，请切换一下应用后再试"
            }
            val pm = context.packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(lastPkg, 0)).toString()
            } catch (_: Exception) {
                lastPkg
            }
            "{\"package\":\"$lastPkg\",\"app_name\":\"$appName\",\"last_foreground_time\":$lastTime}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }
