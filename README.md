# Nudge 🐷

小鱼APP。内置MCP Server的Android应用。

小猪拱小鱼。

## 架构
RikkaHub ←127.0.0.1→ Nudge APP → 数据采集 + 命令执行

## 版本
当前 **v0.3.13**（versionCode 14），APK 发布在 GitHub Releases：
https://github.com/yuyu1838309000-cmd/Nudge/releases

## 工具（20 个）

### 数据采集
- `ping` 测试连通性
- `get_foreground_app` 前台应用包名/名称/界面/停留时长
- `screenshot_analyze` 截屏 + AI 分析
- `sensor_data` 传感器实时数据（加速度/光线/陀螺仪）
- `device_status` 锁屏状态/电量/充电/网络类型
- `get_location` GPS 定位（经纬度+地址）
- `get_notifications` 通知列表
- `get_steps` 今日步数
- `calendar_query` 日历事件

### 命令执行
- `set_alarm` 系统闹钟
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

## 构建/发布流程
1. 代码在 `/home/ubuntu/Nudge`，改完 git commit && push
2. 服务器编译：`cd /home/ubuntu/Nudge && ./gradlew assembleDebug`（编译完 `./gradlew --stop` 关 daemon）
3. 更新 GitHub Release 上传 APK
4. 小鱼从 Release 下载
