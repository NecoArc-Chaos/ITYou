package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 变更类型枚举，映射 MD3E 色彩层级
 */
enum class ChangeType(val label: String) {
    FEATURE("新特性"),
    IMPROVEMENT("优化"),
    FIX("修复"),
    DESIGN("视觉")
}

data class ChangeItem(
    val type: ChangeType,
    val description: String
)

/**
 * 版本发布记录模型
 */
@Immutable
data class ReleaseNote(
    val version: String,
    val releaseDate: String,
    val summary: String,
    val changes: List<ChangeItem>,
    val isLatest: Boolean = false
)

/**
 * 内置可视化版本演进历史列表 (从最新到早期版本)
 */
val AppReleaseHistory = listOf(
    ReleaseNote(
        version = "v1.6.0",
        releaseDate = "2026-10-03",
        summary = "新增文章评论区：表情渲染、楼中楼展开动画与按需加载剩余回复，并修复详情页标题/「相关文章」重复渲染问题。",
        isLatest = true,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "文章详情页新增评论区：展示真实评论（头像、昵称、楼层、时间、支持/反对数）"),
            ChangeItem(ChangeType.FEATURE, "评论表情渲染：`[坏笑]` 等颜文字转为内联图片，与文字同段落混排"),
            ChangeItem(ChangeType.FEATURE, "楼中楼回复：默认折叠、点击展开，展开与收起均带弹簧动画"),
            ChangeItem(ChangeType.FEATURE, "按需加载剩余回复：服务端报告仍有未内联回复时，可点击补齐并展示「回复 @某人」"),
            ChangeItem(ChangeType.FIX, "修复 PC 端页面结构导致的正文标题与「相关文章」标题重复渲染"),
            ChangeItem(ChangeType.IMPROVEMENT, "评论内容支持长按选中复制，评论项按 key/contentType 复用组合槽位")
        )
    ),
    ReleaseNote(
        version = "v1.5.0",
        releaseDate = "2026-10-02",
        summary = "全面集成默认中文字体「丸子黑体」、补齐详情页相关文章流，精简顶栏交互。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "默认启用高质感圆润中文排版字体（Maruko Gothic / 丸子黑体）"),
            ChangeItem(ChangeType.FEATURE, "设置中提供「使用系统字体」独立开关，随时自由回退系统原厂字型"),
            ChangeItem(ChangeType.FEATURE, "文章详情页完美解析并展示「相关文章」卡片流，支持点选跳转深度阅读"),
            ChangeItem(ChangeType.DESIGN, "移除首页未实装的顶栏搜索按钮，视觉更加精简纯粹"),
            ChangeItem(ChangeType.FIX, "修复 R8 混淆因 Jsoup 缺失注解抛出的构建异常，打通 CI 正式 Release APK 产出"),
            ChangeItem(ChangeType.IMPROVEMENT, "重构 README.md 文档，详述 MD3E 表现力设计与技术架构")
        )
    ),
    ReleaseNote(
        version = "v1.4.0",
        releaseDate = "2026-10-02",
        summary = "以「可预测的滚动」为目标重构首页渲染管线：统一卡片几何、收敛重组范围、补齐 Release 构建与 Baseline Profile。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.IMPROVEMENT, "骨架屏与真实卡片共用同一套布局骨架与度量，彻底消除结构错位与高度跳变"),
            ChangeItem(ChangeType.IMPROVEMENT, "数据模型全面标注 @Immutable 并移除可变的 java.util.Date，卡片恢复「跳过重组」能力"),
            ChangeItem(ChangeType.IMPROVEMENT, "Shimmer 由约 50 个独立动画时钟收敛为 1 个，且在绘制阶段读取进度，不再触发重组"),
            ChangeItem(ChangeType.IMPROVEMENT, "LazyColumn 补齐 key 与 contentType，开启条目组合槽位复用"),
            ChangeItem(ChangeType.IMPROVEMENT, "Coil 关闭 crossfade、忽略缓存头、复用同一 OkHttp 连接池；缩略图去除时间戳缓存破坏参数"),
            ChangeItem(ChangeType.FEATURE, "新增 release 变体（R8 + 资源压缩 + Baseline Profile）并由 CI 产出，用于真实性能验证"),
            ChangeItem(ChangeType.FIX, "修复分类分页游标取自过滤后结果导致「某些分类翻不动」的问题"),
            ChangeItem(ChangeType.FIX, "移除网络失败时注入硬编码示例新闻的行为，改为显式错误与重试"),
            ChangeItem(ChangeType.IMPROVEMENT, "详情页正文图片按 w/h 预留宽高比，消除图片加载后的正文回流")
        )
    ),
    ReleaseNote(
        version = "v1.3.1",
        releaseDate = "2026-10-02",
        summary = "修复 JVM 单元测试环境下的 JSON 解析依赖桩问题，版本号自动递增。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FIX, "补充 testImplementation 真实 org.json 引擎，修复单元测试桩方法未模拟报错"),
            ChangeItem(ChangeType.IMPROVEMENT, "配置 unitTests.isReturnDefaultValues 提升测试容错稳定性")
        )
    ),
    ReleaseNote(
        version = "v1.3.0",
        releaseDate = "2026-10-02",
        summary = "全面接入移动端分页无限滚动数据源，支持上拉触底静默预加载与智能导购广告过滤。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "支持无限分页流式加载（基于时间戳游标的流式翻页与历史文章按需拉取）"),
            ChangeItem(ChangeType.FEATURE, "列表触底智能预判：滑至倒数第 3 项自动无感发起预加载"),
            ChangeItem(ChangeType.DESIGN, "MD3E 胶囊式底部加载状态与全部已加载完的轻量视觉指示"),
            ChangeItem(ChangeType.IMPROVEMENT, "自动过滤导购与插播推广内容，保证 100% 纯净科技新闻阅读体验")
        )
    ),
    ReleaseNote(
        version = "v1.2.0",
        releaseDate = "2026-10-01",
        summary = "构建全新 Material 3 Expressive 主题自适应图标与 Android 13+ 莫奈取色单色层体系。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "支持 Android 13+ 动态壁纸莫奈单色图标 (Themed Icon / Monochrome Layer)"),
            ChangeItem(ChangeType.DESIGN, "基于 M3E 平滑张力圆角重构 IT之家科技徽标，100% 对齐 66dp 中心安全视口"),
            ChangeItem(ChangeType.DESIGN, "高质感深灰沉浸底色结合经典科技红 (#D32F2F) 与活力珊瑚红 (#FF5252) 层次视觉")
        )
    ),
    ReleaseNote(
        version = "v1.1.0",
        releaseDate = "2026-10-01",
        summary = "开放自定义本地字体安装与多样化预见式返回转场动效支持，重塑 PixelPlayer 级交互质感。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "支持从本地系统存储安装外部中英文字体 (.ttf / .otf) 并全局生效"),
            ChangeItem(ChangeType.FEATURE, "提供 4 种预见式预测返回与转场动效可选（弹簧滑移、容器缩放、平滑渐变、抽屉升降）"),
            ChangeItem(ChangeType.DESIGN, "PixelPlayer 风格的精致设置项、动态排版实时预览卡片与触感缩放交互")
        )
    ),
    ReleaseNote(
        version = "v1.0.0",
        releaseDate = "2026-10-01",
        summary = "Material 3 Expressive 正式架构重构，带来纯净原生阅读与视觉跃迁。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "支持 Android 14+ 预见式预测返回手势系统拦截基础"),
            ChangeItem(ChangeType.DESIGN, "1:1 精确对齐的流光微光骨架屏，解决布局回流跳动问题"),
            ChangeItem(ChangeType.DESIGN, "收敛顶栏标题空间，消除孤立占位行，阅读沉浸感提升"),
            ChangeItem(ChangeType.IMPROVEMENT, "重构 RSS 分类推断引擎与确定性关键词算法")
        )
    ),
    ReleaseNote(
        version = "v0.9.5",
        releaseDate = "2026-09-28",
        summary = "细节打磨与动效增强。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.DESIGN, "引入 24dp/28dp 平滑曲率形状系统 (ShapeCache)"),
            ChangeItem(ChangeType.IMPROVEMENT, "卡片点击引入物理微缩放反馈动效 (graphicsLayer scale)"),
            ChangeItem(ChangeType.FIX, "修复文章详情页面硬编码回退导致的文章内容错配问题")
        )
    ),
    ReleaseNote(
        version = "v0.9.0",
        releaseDate = "2026-09-27",
        summary = "ITYou 初代架构落成。",
        isLatest = false,
        changes = listOf(
            ChangeItem(ChangeType.FEATURE, "基于 Jsoup 的全原生富文本段落与图片网格 DOM 解析"),
            ChangeItem(ChangeType.FEATURE, "支持 Material You 动态壁纸 Monet 取色与本地设置持久化"),
            ChangeItem(ChangeType.IMPROVEMENT, "Clean Architecture 架构建立与 CI 自动化流水线打通")
        )
    )
)
