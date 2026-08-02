# Nudge 🐷

小鱼APP。内置 MCP Server 的 Android 应用。

小猪拱小鱼。

## 架构
RikkaHub ←127.0.0.1:8809→ Nudge APP → 数据采集 + 命令执行 + 闹钟

## 版本
当前 **v0.3.14**（versionCode 15），APK 发布在 GitHub Releases：
https://github.com/yuyu1838309000-cmd/Nudge/releases

## UI 结构
- **概览 Tab**：MCP 运行状态、自检、权限进度（使用情况/无障碍/通知监听）
- **工具 Tab**：入口页
  - 🧰 全部工具 → 20 个 MCP 工具列表（搜索 + 逐个测试）
  - ⏰ 闹钟 → 闹钟管理页
  - 其余留白待扩展
- **设置 Tab**：API Key / 模型 / 接口地址 / 截图提示词

## 闹钟功能
- **定时闹钟**：时间选择，支持重复模式（响一次 / 每天 / 自定义周几）
- **倒计时**：1/5/10/15/30 分钟预设或自定义，实时跳动，响完可拨开关重新计时
- 每个闹钟：标题（必填）+ 备注（可选）+ 独立开关 + 删除
- 响铃：前台服务 `AlarmSoundService` 用 MediaPlayer 直接播放内置铃声（`res/raw/alarm.wav`，柔和正弦波门铃声，走 USAGE_ALARM 闹钟音量），不依赖通知渠道声音
- 关闭：响铃时全屏弹出 `RingingActivity`（锁屏/息屏也会显示），大「关闭」按钮停止铃声；通知上也有「停止」按钮；最长响 60 秒自动停
- 存储：`AlarmStore`（SharedPreferences JSON），一次性闹钟响完自动关，daily/weekly 自动排下次

## 工具（20 个）

### 数据采集
- `ping` 测试连通性
- `get_foreground_app` 前台应用包名/名称/界面/停留时长
- `screenshot_analyze` 截屏 + AI 分析
- `sensor_data` 传感器实时数据（加速度/光线/陀螺仪）
- `device_status` 锁屏状态/电量/充电/网络类型
- `get_location` GPS 定位（经纬度+地址）
- `get_notifications` 通知列表（按时间倒序）
- `get_steps` 今日步数
- `calendar_query` 日历事件

### 命令执行
- `set_alarm` 系统闹钟（参数：hour/minute 必填，title/note/repeat[once|daily|weekly] 可选）
- `lock_screen` 锁屏
- `media_play_pause` / `media_next` / `media_previous` 媒体控制
- `press_back` / `press_home` 按键
- `open_app` 打开应用
- `wake_up` 亮屏唤醒
- `read_screen` 读取界面文字
- `switch_to_rikkahub` 切回 RikkaHub 前台

## 开发阶段
- [x] 阶段零：最小可验证版
- [x] 阶段一：数据采集
- [x] 阶段二：命令执行
- [x] 阶段三：媒体控制
- [x] 阶段四：扩展（UI 三Tab 重构）
- [x] 阶段五：闹钟页 + 工具入口页 + 全屏响铃

## 代码结构
```
app/src/main/java/com/nudge/app/
├── MainActivity.kt            # 三Tab主界面
├── McpService.kt              # MCP Server (HTTP :8809, JSON-RPC 2.0)
├── NudgeAccessibilityService.kt # 无障碍: 前台应用/截图/读屏/按键
├── NudgeNotificationService.kt  # 通知监听
├── AlarmStore.kt              # 闹钟数据存储 + AlarmManager 调度
├── AlarmReceiver.kt           # 闹钟广播接收（含 STOP 停止广播）
├── AlarmSoundService.kt       # 前台服务播放铃声+震动
├── RingingActivity.kt         # 全屏响铃关闭页
├── ToolsActivity.kt           # 全部工具列表页
└── AlarmActivity.kt           # 闹钟管理页
```

## 构建/发布流程
1. 代码在 `/home/ubuntu/Nudge`，改完 git commit && push
2. 服务器编译：`cd /home/ubuntu/Nudge && ./gradlew assembleDebug`（编译完 `./gradlew --stop` 关 daemon）
3. 更新 GitHub Release 上传 APK（token 在 git remote URL 里）
4. 小鱼从 Release 下载：https://github.com/yuyu1838309000-cmd/Nudge/releases

## 已知要点
- 通知渠道声音在 Android 8+ 创建后锁死，所以铃声必须用前台服务直接播放，不能依赖渠道
- `RingingActivity` 用 fullScreenIntent 触发，系统闹钟通道，锁屏/息屏都能弹
- 小米系统需允许 Nudge 后台运行/自启动，否则闹钟广播可能被延迟
