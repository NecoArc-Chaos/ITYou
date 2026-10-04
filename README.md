<p align="center">
  <img src="art/logo.svg" width="108" height="108" alt="ITYou Logo" />
</p>

<h1 align="center">ITYou</h1>

<p align="center">
  <b>遵循 Material Design 3 Expressive (MD3E) 设计规范的 IT 之家现代 Android 客户端</b>
</p>

<p align="center">
  <a href="https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml">
    <img src="https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml/badge.svg" alt="Android CI" />
  </a>
  <a href="https://github.com/NecoArc-Chaos/ITYou/releases">
    <img src="https://img.shields.io/github/v/release/NecoArc-Chaos/ITYou?include_prereleases&label=Pre-Release&color=orange" alt="GitHub Pre-Release" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android_14%2B-brightgreen.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/Design-Material_3_Expressive-EA4335.svg" alt="MD3E" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" />
</p>

---

## ✨ 核心特性

### 🎨 Material Design 3 Expressive 表现力视觉
- **大曲率连续平滑形状体系**：采用 24dp/28dp 张力圆角卡片、胶囊药丸（Pill）指示器与多层级 SurfaceContainer 容器色差构建视觉层级，100% 无传统硬质分割线。
- **物理弹簧微交互动效**：卡片按压与预见式预测返回转场均采用低反弹弹性弹簧曲线（Spring Spec: `DampingRatioLowBouncy`, `StiffnessMediumLow`）。
- **Android 13+ 主题自适应图标**：中心对齐 66dp 安全视口，内置 Android 13+ Monochrome 单色层，无缝支持系统壁纸 Monet 动态色彩提取。

### 🔤 雅致排版与个性化字体
- **内置默认字体**：默认随包集成圆润高质感的「**馬路口圓體 (Maruko Gothic)**」，赋予全界面独特而舒适的中文阅读体验。
- **系统字体回退**：设置中提供「使用系统字体」开关，一键停用内置字体并回退至设备出厂默认字型。
- **外部字体导入**：支持从本地文件选择器安装第三方 `.ttf` 或 `.otf` 字体，即刻动态渲染并全局生效。

### 📰 智能资讯流与流畅滑动
- **无限流式加载**：基于服务端时间戳游标（Cursor）向上流式翻页，列表滑至倒数第 3 项无感静默预加载。
- **下拉刷新**：首页下拉即拉取服务端最新内容，指示器采用 Material 官方的容器化加载指示器，形状随下拉距离形变；完成或失败均以系统 Toast 反馈，不打断阅读。
- **回到顶部并刷新**：下翻一段距离后，右下角浮现浮动按钮，单击即平滑回到列表顶部并自动刷新，省去「滚回顶部再下拉」的两步操作。
- **纯净阅读过滤**：自动剥离 `lapin.ithome.com` 导购、纯商业推广与标记广告内容，保证 100% 极客科技资讯体验。
- **等高卡片与绝对对齐骨架屏**：统一卡片文本几何推导，卡片高度完全一致，彻底消除内容装载时的视觉跳动。

### 📖 原生结构化正文与相关推荐
- **原生 Compose 段落排版**：基于 Jsoup 将 HTML 转换为原生 ContentBlock 树，包含自适应等比高清大图、引用快、等宽代码块等。
- **相关文章流**：文章末尾自动提取并展示相关联的扩展文章条目，支持点选无缝连贯跳转阅读。

### 💬 完整评论区与楼中楼讨论
- **真实评论流**：展示 IT 之家真实评论，含头像、昵称、地区、楼层、时间与支持/反对数，正文支持长按选中复制。
- **表情渲染**：`[坏笑]`、`[捂脸笑哭]` 等颜文字自动转为内联图片，与文字同段落混排，而非显示为方括号文本。
- **楼中楼回复**：回复默认折叠、一键展开，展开/收起带 MD3E 弹簧动效；当服务端仍有未内联回复时，可点击「展开另外 N 条」按需补齐，并展示「回复 @某人」的引用关系。

### 📤 分享与收藏
- **系统级分享**：详情页一键拉起系统分享面板，分享内容为文章标题与链接，可发送至设备上任意已安装的应用，无需额外权限。
- **本地收藏**：书签状态持久化到本地，退出应用也不丢失；首页顶栏提供「我的收藏」入口，可集中查看与取消收藏。
- **状态实时同步**：详情页与收藏页共享同一数据源，在任一处取消收藏，另一处会立即同步。

---

## 🛠️ 技术架构

```
ITYou/
├── art/
│   └── logo.svg                         # 表现型应用矢量徽标
├── app/
│   ├── src/main/java/com/necoarc/ityou/
│   │   ├── ITYouApplication.kt          # 全局基础设施、网络单例与 Coil 调优配置
│   │   ├── MainActivity.kt               # Edge-to-Edge 系统栏与预见式导航路由调度
│   │   ├── data/
│   │   │   ├── model/                   # 纯不可变数据契约 (Article, ContentBlock, ArticleComment, ReleaseNote 等)
│   │   │   ├── parser/                  # Jsoup / JSON 结构化流式解析引擎 (正文 / 列表 / 评论)
│   │   │   ├── remote/                  # 统一单例 OkHttp 线程池、连接池与短期响应缓存
│   │   │   └── repository/              # 数据仓库 (分页收集器与设置持久化)
│   │   └── ui/
│   │       ├── components/              # 核心卡片 (ArticleCard, Hero, Skeleton, 尺寸契约)
│   │       ├── screens/                 # 页面层 (HomeScreen, DetailScreen, SettingsScreen)
│   │       └── theme/                   # MD3E 色彩、排版、圆角令牌与字体解析器
│   └── src/main/assets/fonts/           # 内置 MarukoGothicCJKsc-Medium 表现型中文字体
```

- **架构模式**：Clean Architecture + MVI (UDF) 单向数据流，包含 `@Immutable` UIState、响应式 StateFlow 与 SharedFlow。
- **渲染治理**：严格遵循不可变类型白名单、`LazyColumn` 显式 `key` 与 `contentType` 槽位复用、`collectAsStateWithLifecycle`。
- **构建优化**：启用 R8 代码混淆、无用资源缩减与 Baseline Profile 预编译。

---

## 📦 下载与体验

- **运行要求**：Android 8.0 (API 26) 及以上，推荐 Android 14+ 以获得最佳预见式返回动效。
- **下载安装**：前往仓库的 [**Releases 页面**](https://github.com/NecoArc-Chaos/ITYou/releases) 获取安装包（`ITYou-v2.0.0-release.apk`，内置 R8 深度混淆与 Baseline Profile 优化）。v2.0.0 起提供正式版，此前的预览构建仍在 Releases 页面保留。

---

## 🙏 致谢

### 字体

本项目内置的默认中文字体为 **馬路口圓體 (Maruko Gothic)**，在此向作者 **Max** 致谢。

- **项目地址**：<https://github.com/max32002/maruko-gothic>
- **衍生来源**：馬路口圓體是 [ZenMaruGothic](https://github.com/googlefonts/zen-marugothic) 的补字计划，调整了部件写法并新增约三万余中文字，同时补充了部分符号。
- **授权方式**：采用 [SIL Open Font License 1.1](https://github.com/max32002/maruko-gothic/blob/main/SIL_Open_Font_License_1.1.txt) 授权，**允许免费商用**。
- **本应用使用版本**：`CJK SC`（简体中文）的 `Medium` 字重。

圓體温润的笔画形态与本项目的 M3E 视觉语言契合度很高，如果你也喜欢这款字体，欢迎前往上述仓库支持作者。

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。

> 注意：内置字体 **馬路口圓體** 以 SIL OFL 1.1 授权分发，**不受本项目 MIT 协议约束**，其使用请遵循 OFL 1.1 的条款。
