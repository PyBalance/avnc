# AVNC 剪贴板和 Win 组合键升级

## 使用

打开虚拟按键栏，向左滑到「发送文本到服务器」，输入文本，点击右侧剪贴板按钮：

- **复制并粘贴 · Omarchy（Win+V）**：先发送文本到受控电脑的剪贴板，等待 350 毫秒，再发送一次 Win+V。本机 Omarchy 的通用粘贴绑定会根据当前窗口选择普通应用的 Ctrl+V 或终端的 Shift+Insert。
- **Ctrl+V / Ctrl+Shift+V / Shift+Insert**：用于其他桌面、终端或自定义粘贴绑定。
- **仅复制到远端剪贴板**：只发送文本，不执行快捷键。

发送的内容来自当前输入框，与手机剪贴板和「自动剪贴板同步」开关无关。文本会保留，便于手动粘贴或重试；原来的键盘「发送」动作仍然通过按键发送并清空文本框。空文本不会触发剪贴板发送。

先在受控电脑中点中目标输入框。VNC 协议无法确认应用是否已经插入文本，提示表示剪贴板数据和快捷键已发送，不表示目标应用已确认粘贴。延迟用于给 Wayland 剪贴板桥接留出处理时间，慢连接可以稍后在远端手动粘贴。一次操作只尝试一种快捷键，避免重复粘贴。

轻按 Win（Super）后，左侧出现 `1 2 3 4 5 c v Space Enter`，原来的按键向右移动，宽度不足时可横向滚动。Win 在普通按键后继续保持按下；可加入 Ctrl、Shift、Alt 来发送更多组合键。再次点击 Win 即释放，新增按键隐藏。Ctrl、Shift、Alt 原有的单次/长按锁定行为继续适用。外接键盘的 Win 按下/释放也控制新增按键的显示。

如果开启「单次轻按使用超级键」，轻按仍只发送一次 Win 按下和释放；要连续使用组合键，请关闭该设置，或长按锁定 Win。

## 受控 Omarchy 电脑需要什么

这台电脑已经安装：`wayvnc 0.10.1`、`neatvnc 1.0.1`、`wl-clipboard` 和 `wtype`，也已配置 Super+V 通用粘贴，无需为新增功能补装它们。

中文、emoji 和其他非 Latin-1 字符需要 VNC 服务器启用 UTF-8 扩展剪贴板。[Neat VNC 0.9.0 开始加入此功能](https://github.com/any1/neatvnc/releases/tag/v0.9.0)。如果服务器没有协商该扩展，应用会保留文本并提示，不发送乱码或粘贴旧内容。英文等 Latin-1 文本仍可通过传统剪贴板协议发送。连接后等待剪贴板能力协商完成再发送。

其他 Omarchy 电脑可检查 `pacman -Q wayvnc neatvnc wl-clipboard`。仅在缺少时安装对应包。受控会话应允许键盘输入和剪贴板访问；AVNC 的仅查看模式不会执行复制或粘贴。

## 安装与本地构建

GitHub Actions 的 `avnc-debug` 构建产物包含可直接安装的 `app-debug.apk`，不依赖原作者的签名密钥。它使用 `com.gaurav.avnc.debug`，可以与官方版并存；官方版服务器列表可通过导出/导入迁移。

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

GitHub Actions 在 Android 35 模拟器上运行剪贴板、虚拟按键、KeyHandler 和 VncClient 的回归测试。Omarchy 的实际聚焦窗口粘贴效果仍需手机连接实际服务器验证。
