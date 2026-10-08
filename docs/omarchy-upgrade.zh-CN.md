# AVNC 剪贴板和 Win 组合键升级

## 使用

本次升级按 `Documents/AVNC修改方案-20261008/AVNC小键盘与文本发送修改方案.docx` 实施。修改前已提交当前状态：`2621f5a`。该提交保存此前待提交的 CI 配置，源码修改另作独立提交。

打开虚拟按键栏，向左滑到文本页。发送区常态为两行：第一行选择 **按键输入 / 剪贴板**，第二行是同一个草稿框与操作按钮；保留返回小键盘的入口，不增加密码控件或常驻说明。

- **按键输入 → 发送**：释放已启用的修饰键，复用 Android KeyCharacterMap → KeyHandler → Messenger 按键路径；保持大小写、符号和空格，不主动写入手机或远端剪贴板，不追加 Enter。主动输入的换行转为 Enter。空输入无操作；断线、只读或入队失败时保留草稿；全部事件入队后才清空。入队不代表远端输入框已确认接收。
- **剪贴板 → 仅复制**：无损同步当前草稿，不执行粘贴。
- **剪贴板 → 复制并粘贴**：同步草稿，等待 350 毫秒，再执行一次预设粘贴组合。默认 **Omarchy（Super+V）**；在 **设置 → 服务器 → 剪贴板粘贴方式** 可选择 Ctrl+V、Ctrl+Shift+V 或 Shift+Insert。

模式切换不发送、不清空草稿。草稿只保留在会话 ViewModel 内存，旋转时可恢复；不写入偏好设置、Activity 保存状态或文本日志。输入框默认显示一行，可容纳主动输入的换行并在框内滚动，长文本不会无限增高。

显式剪贴板传输继续使用修订号和自动同步防覆盖逻辑，会先更新手机剪贴板；旧的手机剪贴板不会覆盖本次传输。即使「自动剪贴板同步」关闭，显式发送也可用。剪贴板发送后保留草稿，传输中禁用操作按钮并拒绝重复事务。结果只用短暂提示显示；不支持 UTF-8 的服务器不会收到被替换为问号的中文/emoji，也不会自动改用按键重发。

先在受控电脑中点中目标输入框。VNC 协议无法确认应用是否已经插入文本；提示表示传输和快捷键已发送。一次操作只尝试一种快捷键，慢连接可稍后手动粘贴。

**设置 → 输入 → 自定义虚拟按键** 继续管理基础布局的增删、排序和原有 1 / 2 / 3 行设置。**Win 组合键布局** 使用独立的编辑目标，默认 `1 2 3 4 5 C V X Space Enter`，可单独增删、排序、保存、取消或恢复默认。两个组可有相同按键，同一组不重复；未知键只跳过该项，有效键保留顺序。升级不会在已保存的基础布局中强行追加按键。

轻按 Win（Super）后，Win 组合键出现在普通键左侧，普通键向右移动，可横向滚动；Win 保持开启且解除按钮留在可见区域。再次点击 Win 释放并恢复基础布局。开启 **固定修饰键** 时，只固定基础布局中已有的 Win / Ctrl / Alt / Shift，其他键保持原有相对顺序；关闭时不改写原布局。

原来的 **单次轻按使用超级键** 值不变：开启时单击仍是一次 down/up；要持续组合键可关闭该设置或长按 Win 保持。Ctrl / Alt / Shift 原有的单击及长按锁定行为保留。隐藏面板、切后台或断线会清理持有状态。

**⌫** 是退格，**Del** 是向后删除。删除键和方向键在触摸按住时只发送一次 down，由远端执行实体键盘式重复；松手、触摸取消、隐藏面板、切后台和断线会释放并清理。默认基础布局含退格；自定义布局可通过原编辑器自行添加，不自动重排已有布局。

界面样式更新：文本页使用和小键盘一致的 34 dp 最小行高、正文文字及紧凑自然宽度按钮，无边框或输入底线。文字为黑色，模式选中时文字变紫色；按键面板采用浅色局部主题，避免夜间主题下黑字无法阅读。输入框保持单行显示及内部滚动，大字体的提示文字不会撑高多行。退格改为 24 dp 图标，与方向键图标一致，点击区沿用原来的 48 dp 最小宽度和 34 dp 最小高度。

## 受控 Omarchy 电脑需要什么

这台电脑已经安装：`wayvnc 0.10.1`、`neatvnc 1.0.1`、`wl-clipboard` 和 `wtype`，也已配置 Super+V 通用粘贴，无需为新增功能补装它们。

中文、emoji 和其他非 Latin-1 字符需要 VNC 服务器启用 UTF-8 扩展剪贴板。[Neat VNC 0.9.0 开始加入此功能](https://github.com/any1/neatvnc/releases/tag/v0.9.0)。如果服务器没有协商该扩展，应用会保留文本并提示，不发送乱码或粘贴旧内容。英文等 Latin-1 文本仍可通过传统剪贴板协议发送。连接后等待剪贴板能力协商完成再发送。

其他 Omarchy 电脑可检查 `pacman -Q wayvnc neatvnc wl-clipboard`。仅在缺少时安装对应包。受控会话应允许键盘输入和剪贴板访问；AVNC 的仅查看模式不会执行复制或粘贴。

## 安装与本地构建

本地 `assembleDebug` 生成可直接安装的 `app-debug.apk`，不依赖原作者的签名密钥。它使用 `com.gaurav.avnc.debug`，可以与官方版并存；官方版服务器列表可通过导出/导入迁移。

当前修复版的应用名称为 **AVNC Omarchy**，版本为 `3.3.1-omarchy.4 (debug)`（versionCode 56）。覆盖安装原调试版即可保留服务器配置；测试时请打开 AVNC Omarchy。

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

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，方案升级版复制到 `~/Downloads/AVNC-Omarchy-3.3.1-style-fix.apk`，已通过签名校验、安装到本机 Waydroid，并通过 Taildrop 发送到 x200。

验证结果（2026-10-08）：

- 本机 Waydroid（Android 13 / API 33）：ClipboardPasteTest、VirtualKeysTest、KeyHandlerTest、VncClientTest、VirtualKeysEditorTest 共 **97 项**通过。覆盖两个布局互不影响的增删/排序/保存/取消/恢复默认、未知键跳过、已有布局不改写、固定修饰键与 Win 解除可见、草稿模式切换/旋转/断线/队列拒绝/只读、空文本不回车、符号与显式换行、剪贴板防覆盖/防连点/不支持 Unicode 时不重发，以及长按取消/隐藏/后台/断线释放。
- 输入面板在英文/简体中文、320 / 360 / 640 dp 宽度、1 / 1.3 / 2 倍字体下完成实际 Android View 测量：输入框保留可用宽度，按钮可点击且文字不截断。
- 本机 Omarchy / WayVNC：另 **1 项**实际桌面集成测试通过。中文、emoji、多行文本完整进入剪贴板；仅复制不改变文本框；Super+V 恰好粘贴一次。经 Android KeyCharacterMap / KeyHandler 发送 ` Abc@123 #! ` 的大小写、符号、空格及显式换行，实际文本框收到预期内容，剪贴板不变。长按退格连续删除，释放后停止。
- Android 13 的系统剪贴板浮层会遮挡测试底部按键。UI 测试在每项前后关闭浮层并临时关闭动画，结束后恢复；未改变 APK 运行行为。真实桌面验证使用临时回环服务与专用文本框，结束后关闭服务、移除转发并恢复原剪贴板及窗口焦点。
- ARM64、ARM32、x86、x86_64 APK 已构建；x200 的实体设备横竖屏、触摸和实际应用粘贴仍需用户安装测试。
- `lintDebug` 已执行，仍有 **254 项 MissingTranslation** 错误：234 项上游已有字符串，20 项本项目新增字符串缺少其他语言翻译。英文/简体中文已提供，其他语言回退英文；没有其他 Error/Fatal。完整 lint 未通过，APK 与测试 APK 构建通过。
- 样式更新（`omarchy.4`）：VirtualKeysTest 与 VirtualKeysEditorTest **40 项**回归通过；核对中文 360 dp 和英文 320 dp / 2 倍字体的 Android 原生渲染图，文本面板无边框、黑色字、选中紫色字，常态两行高度与小键盘一致。样式回归报告为 `app/build/reports/style-fix-regression.txt`，界面图为 `app/build/reports/style-previews/`。
- 本机报告：`app/build/reports/docx-regression.txt`、`docx-live-test.txt`、`docx-build.log` 和 `lint-results-debug.html`。

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
