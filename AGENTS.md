# MarkMap 开发约定

## 调试设备
- **优先使用真机 `S200110007D`**（Android 9 / 4 KB 页 / arm64）。设备在线时所有 adb 命令都带 `-s S200110007D`。
  - 例：`adb -s S200110007D install -r app/build/outputs/apk/debug/app-debug.apk`
- 模拟器 `emulator-5554`（`sdk_gphone16k_arm64`）是 **16 KB 页**镜像：
  - 安装需 `zipalign -P 16` + 重新签名，否则 `INSTALL_FAILED_INVALID_APK`；
  - 百度地图 SDK 的 native 库在 16 KB 上 `dlopen` 失败，**地图相关页面不可用**。
  - 因此地图功能务必在真机或 4 KB 页模拟器上验证。

## 构建与测试
- 需要 JDK 17：`export JAVA_HOME=/Users/android/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home`
- 构建：`./gradlew assembleDebug --offline`
- 单测：`./gradlew testDebugUnitTest --offline`

## adb 传参约定
- 实体 `id`（groupId/lineId/pointId）在代码里用 `getLongExtra` 读取，**必须用 `--el` 传 Long**；用 `--ei` 会取到 -1 导致页面判空退出（非 bug）。
- 字符串用 `--es`，Int 用 `--ei`。
- 非导出的 Activity 无法直接 `am start`（`run-as am` 也无权限）。需要单独验证时，临时把该 Activity 的 `android:exported` 设为 `true`，验证后务必改回 `false` 并重新构建。

## 开发流程（强制：UX → UI → 编码）
任何功能/页面改动都必须依次经过三个阶段，禁止跳过设计直接写代码：
1. **UX**：先输出信息架构与交互流程（线框/流程图），明确每个操作、状态与异常态（空态、失败、离线）。
2. **UI**：输出与现有风格一致的高保真设计稿（`docs/design/*.html` + 渲染的 `.png`）；涉及多方案时先给用户选择，确认后再定稿。
3. **编码**：按定稿设计落地，保持**操作与视觉一致**（组件、间距、色板、图标、动效），并在真机验证。

- 设计产物与规范位置：`docs/design/`、`docs/设计规范.md`；新增页面/交互须同步补充对应设计稿。
- 复用既有 token 与组件，不要另起一套：`bg_sheet`、`bg_seg_on`/`bg_seg_track`、`bg_node_start/mid/end`、`bg_btn_primary`/`bg_btn_ghost`、`bg_chip`/`bg_pill`、`bg_round_btn`、`Widget.MarkMap.*`、`TextAppearance.MarkMap.*`。
- 地图页统一模式：沉浸地图 + 顶部浮动药丸导航（back/标题/更多）+ 底部抽屉（抓手 → 摘要 → 分段开关 → 时间轴列表）。

## 关键约定
- 术语统一：行程 / 路段 / 地点。
- Room 版本升级必须写 `Migration`，禁止 `fallbackToDestructiveMigration` 丢数据。
- 删除交互：列表左滑 + Snackbar 撤销；回收站可恢复。
