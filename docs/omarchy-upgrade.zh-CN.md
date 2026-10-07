# AVNC 剪贴板和 Win 组合键升级

## 使用

打开虚拟按键栏，向左滑到「发送文本到服务器」，输入文本，点击右侧剪贴板按钮：

- **复制并粘贴 · Omarchy（Win+V）**：先发送文本到受控电脑的剪贴板，等待 350 毫秒，再发送一次 Win+V。本机 Omarchy 的通用粘贴绑定会根据当前窗口选择普通应用的 Ctrl+V 或终端的 Shift+Insert。
- **Ctrl+V / Ctrl+Shift+V / Shift+Insert**：用于其他桌面、终端或自定义粘贴绑定。
- **仅复制到远端剪贴板**：只发送文本，不执行快捷键。

发送的内容来自当前输入框，「自动剪贴板同步」关闭时也可发送。显式发送时会先更新手机剪贴板，再发送到受控电脑；旧的自动同步读取不会覆盖这次发送的新文本。文本会保留，便于手动粘贴或重试；原来的键盘「发送」动作仍然通过按键发送并清空文本框。空文本不会触发剪贴板发送。

先在受控电脑中点中目标输入框。VNC 协议无法确认应用是否已经插入文本，提示表示剪贴板数据和快捷键已发送，不表示目标应用已确认粘贴。延迟用于给 Wayland 剪贴板桥接留出处理时间，慢连接可以稍后在远端手动粘贴。一次操作只尝试一种快捷键，避免重复粘贴。

轻按 Win（Super）后，左侧出现 `1 2 3 4 5 c v Space Enter`，原来的按键向右移动，宽度不足时可横向滚动。Win 在普通按键后继续保持按下；可加入 Ctrl、Shift、Alt 来发送更多组合键。再次点击 Win 即释放，新增按键隐藏。Ctrl、Shift、Alt 原有的单次/长按锁定行为继续适用。外接键盘的 Win 按下/释放也控制新增按键的显示。

如果开启「单次轻按使用超级键」，轻按仍只发送一次 Win 按下和释放；要连续使用组合键，请关闭该设置，或长按锁定 Win。隐藏按键栏或应用进入后台也会释放虚拟 Win，避免按键卡住。

按键区左侧常驻 **⌫ 退格键**：单击删除光标前的字符，按住时向服务器保持按下状态，由受控电脑执行与实体键盘相同的连续退格。松手、触摸取消、隐藏按键栏或进入后台会释放按键；松手不会多发送一次删除。「全部按键」中的 Delete 是向前删除，也支持这种按住/释放行为。已有自定义布局会在升级时添加一次退格键，之后仍可自行调整布局。

## 受控 Omarchy 电脑需要什么

这台电脑已经安装：`wayvnc 0.10.1`、`neatvnc 1.0.1`、`wl-clipboard` 和 `wtype`，也已配置 Super+V 通用粘贴，无需为新增功能补装它们。

中文、emoji 和其他非 Latin-1 字符需要 VNC 服务器启用 UTF-8 扩展剪贴板。[Neat VNC 0.9.0 开始加入此功能](https://github.com/any1/neatvnc/releases/tag/v0.9.0)。如果服务器没有协商该扩展，应用会保留文本并提示，不发送乱码或粘贴旧内容。英文等 Latin-1 文本仍可通过传统剪贴板协议发送。连接后等待剪贴板能力协商完成再发送。

其他 Omarchy 电脑可检查 `pacman -Q wayvnc neatvnc wl-clipboard`。仅在缺少时安装对应包。受控会话应允许键盘输入和剪贴板访问；AVNC 的仅查看模式不会执行复制或粘贴。

## 安装与本地构建

GitHub Actions 的 `avnc-debug` 构建产物包含可直接安装的 `app-debug.apk`，不依赖原作者的签名密钥。它使用 `com.gaurav.avnc.debug`，可以与官方版并存；官方版服务器列表可通过导出/导入迁移。

当前修复版的应用名称为 **AVNC Omarchy**，版本为 `3.3.1-omarchy.2 (debug)`（versionCode 54）。覆盖安装原调试版即可保留服务器配置；测试时请打开 AVNC Omarchy。

如需本地构建，安装以下组件（版本来自项目配置）：

- JDK 17（本次已通过 mise 下载到用户目录）。
- Android SDK Command-line Tools、SDK Platform 36、Build Tools 36.0.0。
- NDK 28.2.13676358、CMake 3.22.1。
- Git、curl、zip、unzip、tar、pkg-config 和 C/C++ 编译器。

也可安装 Android Studio，并在 SDK Manager 中选择以上 Android 组件。命令行安装方式见 [Android SDK Manager 官方文档](https://developer.android.com/tools/sdkmanager)。SDK 安装时需要接受 Android SDK 许可协议。

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "ndk;28.2.13676358" "cmake;3.22.1"
# 在项目根目录的 local.properties 写入 sdk.dir=/你的/Android/SDK/路径
mise exec java@temurin-17 -- ./gradlew assembleDebug assembleDebugAndroidTest lintDebug
```

首次构建会下载并编译原生依赖，耗时较长。

## 验证

新增测试覆盖 UTF-8 中文/emoji/多行文本、先剪贴板后快捷键的消息顺序、重复发送相同内容、仅复制、传统服务器的无损保护、仅查看和断线，以及 Win 连续组合键和关闭自动同步后使用输入框发送。

准备好的自动构建配置会在 Android 35 模拟器上运行剪贴板、虚拟按键、KeyHandler 和 VncClient 的回归测试。该配置目前留在本地 `.github/workflows/main.yml`，GitHub 凭据需要补充 `workflow` 权限后才能推送：`gh auth refresh -h github.com -s workflow`。

已完成本地 SDK 配置和构建：SDK 位于 `~/Android/Sdk`，Android Studio 的项目 Gradle JDK 已设为用户目录中的 Temurin 17。本机可以执行：

```bash
cd ~/avnc
mise exec java@temurin-17 -- ./gradlew assembleDebug assembleDebugAndroidTest
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，修复版复制到 `~/Downloads/AVNC-Omarchy-3.3.1-fix2.apk`，已通过签名校验、安装到本机 Waydroid，并通过 Taildrop 发送到 x200。

验证结果：

- 2026-10-08，本机 Waydroid（Android 13 / API 33）：剪贴板、虚拟按键、KeyHandler、VncClient 共 79 项回归测试全部通过，包括默认自动同步下新文本保留、中文/emoji/多行输入、退格按下/释放/取消、隐藏按键栏释放和旧布局迁移。
- 本机 Omarchy / WayVNC：另 1 项实机测试通过。中文、emoji、多行文本完整进入远端剪贴板；「仅复制」不改变文本框；再次发送同一段文本并执行 Win+V，文本恰好粘贴一次。持续按下退格键会删除多个字符，释放后停止。
- Android 13 的系统剪贴板浮层会遮挡底部按键。UI 测试在每项测试前后关闭系统浮层，且临时关闭动画；这仅用于测试环境，不修改手机应用行为。
- 已编译 ARM64、ARM32、x86、x86_64 原生库。尚未在实体 Android 手机上验证。
- `lintDebug` 已执行，报告 245 项 `MissingTranslation` 错误：234 项涉及上游已有字符串，11 项涉及本次新增字符串的其他语言翻译。新增界面已提供英文和简体中文，其他语言使用英文回退。报告位于 `app/build/reports/lint-results-debug.html`。因此完整的 lint 检查仍未通过，但 APK 和测试 APK 构建成功。

`LiveClipboardPasteTest` 是需主动指定参数的实机集成测试，普通测试运行会跳过它。辅助脚本 `scripts/clipboard-paste-probe.py` 会打开临时 GTK 文本框，并在本机回环地址 `127.0.0.1:18081` 提供剪贴板和文本框读取接口。运行它需要 GTK 4 和 Python GObject；测试结束应关闭脚本并恢复剪贴板。

在临时 WayVNC 服务监听 `127.0.0.1:15900`、辅助脚本运行且专用文本框聚焦的情况下，可通过 ADB 反向转发执行：

```bash
adb reverse tcp:15900 tcp:15900
adb reverse tcp:18081 tcp:18081
adb shell am instrument -w \
  -e class com.gaurav.avnc.session.LiveClipboardPasteTest \
  -e liveVncHost 127.0.0.1 -e liveVncPort 15900 \
  -e liveClipboardProbe http://127.0.0.1:18081 \
  com.gaurav.avnc.debug.test/androidx.test.runner.AndroidJUnitRunner
adb reverse --remove tcp:15900
adb reverse --remove tcp:18081
```
