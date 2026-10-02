# ITYou (IT之家第三方客户端)

[![Android CI](https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml/badge.svg)](https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml)
![Platform](https://img.shields.io/badge/Platform-Android_14%2B-brightgreen.svg)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg)
![Design](https://img.shields.io/badge/Design-Material_3_Expressive-EA4335.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**ITYou** 是一款 100% 基于纯 **Jetpack Compose** 打造、深度遵循 **Material Design 3 Expressive (MD3E)** 设计语言的 IT 之家现代第三方 Android 客户端。

---

## ✨ 核心特性

### 🎨 Material Design 3 Expressive 表现力视觉
- **大曲率连续平滑形状体系**：采用 24dp/28dp 张力圆角卡片、胶囊药丸（Pill）指示器与多层级 SurfaceContainer 容器色差构建视觉层级，100% 无传统硬质分割线。
- **物理弹簧微交互动效**：卡片按压与预见式预测返回转场均采用低反弹弹性弹簧曲线（Spring Spec: `DampingRatioLowBouncy`, `StiffnessMediumLow`）。
- **Android 13+ 主题单色自适应图标**：完美对齐 66dp 安全视口中心，无缝融合系统壁纸 Monet 动态色彩。

### 🔤 雅致排版与个性化字体
- **内置默认字体**：默认随包集成圆润高质感的「**丸子黑体 (Maruko Gothic)**」，赋予全界面独特而舒适的中文阅读体验。
- **系统字体回退**：设置中提供「使用系统字体」开关，一键停用内置字体并回退至设备出厂默认字型。
- **外部字体导入**：支持从本地文件选择器安装第三方 `.ttf` 或 `.otf` 字体，即刻动态渲染并全局生效。

### 📰 智能资讯流与流畅滑动
- **无限流式加载**：基于服务端时间戳游标（Cursor）向上流式翻页，列表滑至倒数第 3 项无感静默预加载。
- **纯净阅读过滤**：自动剥离 `lapin.ithome.com` 导购、纯商业推广与标记广告内容，保证 100% 极客科技资讯体验。
- **等高卡片与绝对对齐骨架屏**：统一卡片文本几何推导，卡片高度完全一致，彻底消除内容装载时的视觉跳动。

### 📖 原生结构化正文与相关推荐
- **原生 Compose 段落排版**：基于 Jsoup 将 HTML 转换为原生 ContentBlock 树，包含自适应等比高清大图、引用快、等宽代码块等。
- **相关文章流**：文章末尾自动提取并展示相关联的扩展文章条目，支持点选无缝连贯跳转阅读。

---

## 🛠️ 技术架构

```
ITYou/
├── app/
│   ├── src/main/java/com/necoarc/ityou/
│   │   ├── ITYouApplication.kt          # 全局基础设施、网络单例与 Coil 调优配置
│   │   ├── MainActivity.kt               # Edge-to-Edge 系统栏与预见式导航路由调度
│   │   ├── data/
│   │   │   ├── model/                   # 纯不可变数据契约 (Article, ContentBlock, ReleaseNote 等)
│   │   │   ├── parser/                  # Jsoup / JSON 结构化流式解析引擎
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

## 📦 构建与体验

- **运行要求**：Android 8.0 (API 26) 及以上，推荐 Android 14+ 以获得最佳预见式返回动效。
- **编译产物**：每次代码提交都会由 GitHub Actions 自动执行单元测试，并分别产出 **Debug APK** 与开启 R8 / Baseline Profile 优化的 **Release APK**。
- **下载体验**：前往仓库的 [Actions](https://github.com/NecoArc-Chaos/ITYou/actions) 页面，点击最新通过的流水线并在下方 **Artifacts** 处下载 `ITYou-release-apk`。

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。
