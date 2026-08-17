# OxGuard Offline Video Player

一个纯本地离线视频播放器 Android App 子工程。

## 功能

- 扫描手机本地视频库并按最近修改时间排序
- 支持 Android 13+ `READ_MEDIA_VIDEO` 权限
- 支持 Android 12 及以下 `READ_EXTERNAL_STORAGE` 权限
- 支持通过系统文件选择器打开单个本地视频
- 内置播放控制、搜索过滤、横竖屏自适应
- 支持适应、填充、拉伸、原始比例四种画面模式
- 支持应用内独立音量调节，不改变系统全局音量
- 支持清晰增强播放模式，用于优化缩放观感
- 支持注册为系统视频打开方式，可手动设为默认视频播放器
- 无 `INTERNET` 权限，播放器本身不联网
- 自动记住上次播放的视频和进度

## 在线构建

进入 GitHub 仓库：

1. 打开 `Actions`
2. 选择 `Build Offline Video Player APK`
3. 点击 `Run workflow`
4. 构建完成后下载 `OxGuardOfflineVideoPlayer-debug-apk`

APK 输出路径：

```text
local-video-player/app/build/outputs/apk/debug/app-debug.apk
```

## 本地构建

需要 JDK 17、Android SDK 35 和 Gradle 8.10.2：

```bash
gradle -p local-video-player :app:assembleDebug
```
