# ITYou (IT之家第三方客户端)

[![Android CI](https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml/badge.svg)](https://github.com/NecoArc-Chaos/ITYou/actions/workflows/ci.yml)

**ITYou** 是一款遵循 **Material Design 3 Expressive (MD3E)** 设计规范的 IT 之家第三方 Android 阅读客户端，基于现代 Android 开发技术栈构建。

---

## ✨ 核心特性

- 🎨 **Material Design 3 Expressive 风格**：
  - 24dp ~ 28dp 大圆角卡片流与沉浸式圆润布局。
  - 动态取色支持（Material You Dynamic Color）。
  - 精心调教的中文新闻阅读排版与行高。
- 📰 **文章流浏览**：
  - 首页支持头条新闻热点轮播卡片。
  - 分类胶囊标签切换（数码、手机、电脑、AI、汽车、游戏等）。
- 📖 **文章详情阅读**：
  - 支持 HTML 正文结构化解析渲染（段落、二级/三级标题、高清图片、引用块）。
  - 顶部沉浸式大图及平滑页面导航。
- ⚙️ **个性化偏好**：
  - 支持 Material You 动态主题色彩开关与高清图片加载设置。

---

## 🛠️ 技术栈

- **语言**: Kotlin 2.0+
- **UI**: Jetpack Compose + Compose Material 3 Expressive
- **网络 & 解析**: OkHttp 4 + Jsoup 1.18
- **图片加载**: Coil Compose
- **导航**: Navigation Compose (Type-Safe Navigation)
- **CI/CD**: GitHub Actions (单元测试与 APK 自动构建)

---

## 📦 构建与运行

本工程遵循严格的 CI/CD 流程：
- 提交代码至 `main` 分支将自动触发 GitHub Actions 流水线，运行自动化单元测试并编译输出 Debug APK。
- 编译产物可前往 GitHub 仓库的 **Actions -> Artifacts** 下载体验。
