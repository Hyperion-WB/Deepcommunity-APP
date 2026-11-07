# 深度社区 Android 客户端 (DeepCommunity App)

原生 Android 客户端，面向 [深度极客社区 (deepcommunity.top)](https://deepcommunity.top/)。基于 Kotlin + Jetpack Compose 打造，专注于手机端重新设计的极致体验。

---

## 🎨 核心设计语言与特色

1. **纯黑白双色极简体系 (Black & White Minimalism)**：
   - 彻底摒弃复杂的大面积彩色背景，以极致纯粹的黑白色为基调。
   - 浅色与深色模式原生无缝适配，通过透明度、模糊和阴影构建清爽的视觉层级。
   - 语义化点缀色（点赞红 `#E84040`、收藏金 `#F0A500`、在线绿 `#34C759` 等）仅用于微状态表达。

2. **液态玻璃材质 (Liquid Glass Surface)**：
   - 封装 `GlassBox` 组件，兼容 Android 12+ 系统级 RenderEffect 高斯模糊，低版本优雅降级为高光半透明层。
   - 顶部 Header、卡片、浮层具有通透纯净的毛玻璃质感。

3. **苹果浮岛悬浮状态栏 (Floating Island Tab Bar)**：
   - 底部不贴边、圆角胶囊形态悬浮岛（`cornerRadius = 34dp`）。
   - 纯图标风格（Line/Filled 切换），无累赘文字，带微小指示圆点。
   - 中间突出发帖浮动键，具备物理挤压回弹动效（Liquid Squish）。

4. **物理弹簧动效规范 (Spring Motion)**：
   - 预设 `gentle`、`bouncy`、`snappy`、`wobble` 四种物理弹簧手感，拒绝线性死板动画。
   - 乐观点赞即时动画反馈，网络重试平滑无感。

---

## 🔐 认证与持久化策略（登录一次，永久保持）

- **账号来源**：客户端严格遵守开放 API 契约，仅保留**登录功能**（账号创建、邮箱验证由网页端完成）。
- **双重加密持久化**：
  - `access_token` 存入 Android Keystore 加密的 `EncryptedSharedPreferences`（AES-256-GCM）。
  - HttpOnly 刷新凭证由自定义 `PersistentCookieJar` 实现离线存储与跨会话常驻。
- **401 无感续签 (TokenAuthenticator)**：
  - 遇到访问令牌过期时，OkHttp 自动拦截并在后台同步调用 `POST /api/auth/refresh` 刷新令牌并重放失败请求，用户完全无感知，无需重复登录。

---

## 🏗️ 项目架构

```
app/src/main/java/top/deepcommunity/app/
├── DeepCommunityApp.kt               # Application 入口 (Hilt 初始化)
├── MainActivity.kt                   # 单 Activity，EdgeToEdge 沉浸全屏
├── core/
│   ├── common/                       # 常量、通用工具 (Constants, Resource)
│   ├── designsystem/
│   │   ├── theme/                    # Color, Theme, Type, Motion 弹簧物理规范
│   │   └── component/                # GlassBox, GlassNavigationBar, DeepButton
│   └── network/                      # TokenManager, PersistentCookieJar, AuthInterceptor, TokenAuthenticator
├── data/
│   ├── api/                          # Retrofit 接口定义 (AuthApi, PostApi, ChatApi, UserApi)
│   ├── model/                        # Kotlinx Serialization 数据模型 (DTO)
│   └── repository/                   # 仓库层 (AuthRepository, PostRepository)
└── ui/
    ├── navigation/                   # Screen 路由与 AppNavHost
    ├── auth/                         # 登录页面与 ViewModel (极简卡片 + 校验)
    ├── home/                         # 首页瀑布信息流与乐观点赞 (HomeScreen, PostCard)
    ├── discover/                     # 发现页
    ├── notification/                 # 消息提醒页
    ├── profile/                      # 个人主页与退出登录 (ProfileScreen)
    └── main/                         # 承载浮岛导航的主容器 (MainScreen)
```

---

## 🚀 编译与调试环境

- **开发工具**：Android Studio Koala / Ladybug 或更高版本
- **JDK 版本**：Java 21
- **Gradle 版本**：8.9 (AGP 8.5.2)
- **最低支持**：Android 8.0 (API 26)
- **目标版本**：Android 15 (API 35)
