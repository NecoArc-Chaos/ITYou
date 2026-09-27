# AGENTS.md — ITYou (IT之家 MD3 Expressive 客户端)

本项目是 **ITYou**：一款遵循 **Material Design 3 Expressive (MD3E)** 风格的 IT之家第三方 Android 阅读客户端。
主要面向 Vibe Coding 与 AI Agent 协作开发，请严格遵循以下指引与约束。

---

## ⚠️ 核心操作约束与规则（重要）

1. **严禁在本地执行 Gradle 构建与测试**：
   - 本地沙箱环境不提供完整的 Android SDK / NDK / Gradle 编译运行支持。
   - **绝对不要**在本地运行 `./gradlew build`、`./gradlew test`、`./gradlew assembleDebug` 等任何编译/构建/测试命令。
   - 所有单元测试、Lint 检查以及 APK / AAB 产品构建，**完全委托 GitHub Actions CI/CD Workflow** 执行。
   - 代码变动后，通过 Git Commit & Push 到远程触发 Workflow 跑流水线并检查结果。

2. **代码产出要求**：
   - 保证代码的静态正确性、包名导入完整、类型匹配与语法准确。
   - 新增功能需同步补充单元测试代码（供 CI 运行）。

3. **风格基准**：
   - 界面严格遵循 **Material Design 3 Expressive** 规范。

---

## 架构与技术栈规范

- **开发语言**: Kotlin (Kotlin 2.0+, 启用 Compose 编译器插件)
- **UI 框架**: Jetpack Compose
- **设计系统**: `androidx.compose.material3:material3` (Expressive API 系列, 如 `MaterialExpressiveTheme`, `LargeFlexibleTopBar`, 大圆角系统)
- **架构模式**: MVVM + Clean Architecture (Presentation, Domain, Data)
- **依赖注入**: Hilt
- **异步处理**: Kotlin Coroutines + Flow
- **网络与解析**:
  - OkHttp + Retrofit / Ktor Client
  - Kotlinx Serialization
  - Jsoup (用于网页文章正文抽取与 HTML DOM 转换)
- **图片加载**: Coil 3 (Compose 专用)
- **本地持久化**: Room (文章离线缓存) + DataStore Preferences (用户偏好)
- **导航**: Navigation Compose (类型安全路由 Type-safe navigation)

---

## 模块划分结构

```text
ITYou/
├── app/                  # 应用装配、入口 Application、MainActivity、全局路由导航
├── core/
│   ├── common/           # 工具类、通用扩展、Dispatchers
│   ├── model/            # 核心业务实体 (Article, ArticleDetail, Category 等)
│   ├── network/          # HTTP 请求、Jsoup 解析器、RSS 解析
│   ├── database/         # Room 数据库、DAO、Entity
│   └── designsystem/     # MD3 Expressive 主题、Color、Shape、Typography 及公共组件
└── feature/
    ├── home/             # 首页流 (头条轮播、分类 Chip、文章列表卡片)
    ├── detail/           # 文章详情 (富文本渲染、图片手势浏览、分享)
    ├── search/           # 搜索页
    └── settings/         # 偏好设置 (主题模式、动态取色、缓存管理)
```

---

## MD3 Expressive 设计规范要点

1. **形状 (Shapes)**:
   - 采用 Expressive 的大圆角系统（卡片一般为 24dp ~ 28dp 圆角，按钮与输入框采用全圆角胶囊形态）。
2. **色彩 (Color)**:
   - 默认以 IT 之家红 (`#D32F2F`) 作为 Seed Color。
   - 完整支持 Android 12+ Dynamic Color (Monet) 动态壁纸取色。
   - 支持浅色模式与深色模式，色彩对比鲜明。
3. **排版 (Typography)**:
   - 使用 Expressive 比例的大胆字阶，强化中文新闻阅读的段落节奏与行高。
4. **动效与交互**:
   - 融入 Expressive Spring 物理回弹动效。
   - 页面间支持 Predictive Back（预测性返回）与共享元素转场。

---

## CI / GitHub Workflow 规范

- CI 配置文件路径: `.github/workflows/ci.yml`
- 触发机制: `push` 到主分支或 `pull_request`
- 主要阶段:
  1. **Lint & Static Check**: 静态检查。
  2. **Unit Test**: 运行 ViewModel、Parser 及 Repository 的单元测试。
  3. **Build Artifact**: 打包 Debug APK，上传作为 Workflow Artifact。
