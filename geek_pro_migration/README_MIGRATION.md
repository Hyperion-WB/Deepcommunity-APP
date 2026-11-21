# 深度极客社区 · 全套专业版 & 摸鱼神器前后端一键迁移包 (geek_pro_migration)

本文件夹已为您将本次所有全新研发、重构优化的**独立专业版极客聊天工作台、六大工作软件摸鱼伪装中枢、顶栏极简微型切换器、纯专业版首页、深度友链系统、以及对应的前后端完整源码**打包归拢，方便您直接一次性复制迁移到目标项目中。

> [!IMPORTANT]
> **设计规范遵循**：
> 1. **经典版 100% 保持原汁原味**：未引入任何侵入式布局或多余按钮，保留原本最熟悉的社区交互；
> 2. **专业版 100% 独立重构**：采用全新的专属极客全息工作台组件，带来震撼的暗黑曜石与科技青绿极客视觉体验。
> 3. **顶栏空间与层次极致优化**：专业版聊天采用分层双轨顶栏架构，将状态灯与「新建/全屏/摸鱼」收纳至顶部微型工具条，下方「私聊消息」与「群聊频道」双选项卡独享 100% 完整宽度，彻底根除任何挤压折行，字距舒展，呼吸感拉满！

---

## 📁 文件夹架构总览

```text
geek_pro_migration/
├── README_MIGRATION.md                     # 本迁移指南与功能清单
├── frontend/                               # 前端完整对应源码（直接覆盖 frontend 目录）
│   └── src/
│       ├── components/
│       │   ├── ProChatWorkspace.tsx        # 🌟 专业版专属全息极客聊天工作台（HUD+终端流/气泡流+命令行发射台）
│       │   ├── StealthWorkspaceCamouflage.tsx # 🌟 六大办公软件摸鱼伪装中枢 (VSCode/IDEA/飞书/Excel/Word/PPT)
│       │   ├── Navbar.tsx                  # 🌟 32px 极简微型模式切换器（Emerald/Blue 呼吸指示灯）
│       │   ├── FriendLinksCard.tsx         # 🌟 双版本自适应友链卡片（支持深情友链、极客公约）
│       │   ├── DailyNewsBlock.tsx          # 极客热点速报组件
│       │   ├── PinnedAnnouncementBanner.tsx# 置顶全站广播条
│       │   └── SearchModal.tsx             # 全局快捷搜索弹窗
│       ├── pages/
│       │   ├── Chat.tsx                    # 🌟 双模聊天页（专业版路由至 ProChatWorkspace，经典版保持原貌）
│       │   ├── ProHome.tsx                 # 🌟 纯粹专业版极客单文件全功能首页（开箱即用）
│       │   ├── Home.tsx                    # 双模一体化首页（专业版与经典版平滑过渡）
│       │   ├── Links.tsx                   # 友情链接独立页（深情友链精选特写 + 极客公约）
│       │   └── Checkin.tsx                 # 每日签到与积分页
│       ├── utils/
│       │   ├── uiMode.ts                   # 双模式持久化驱动 Hook 与全局调度
│       │   └── demoData.ts                 # 社区极客预设数据
│       ├── types/
│       │   └── index.ts                    # 完整 TypeScript 类型定义
│       └── App.tsx                            # 路由配置与全局上下文注入
│
└── backend/                                # ⚙️ 后端源码目录（直接覆盖 backend 目录）
    └── app/
        ├── routers/
        │   ├── posts.py                    # 帖子业务路由
        │   ├── chat.py                     # 聊天与 WebSocket 路由
        │   └── friend_links.py             # 友情链接与深情友链专属接口
        └── schemas/
            └── post.py                     # 数据模型校验
```

---

## 🌟 核心特性与亮点升级

### 1. 专业版专属全息极客聊天工作台 (`ProChatWorkspace.tsx`)
彻底告别传统悬浮卡片样式，专为专业版用户打造：
- **顶部极客全息 HUD 状态栏**：
  - 节点状态 `[NODE: PRO-GEEK-01]`、实时延迟指示 `[PING: 18ms]`、传输加密标志 `[TLS 1.3 / E2EE VERIFIED]`；
  - 实时当前会话极客路径（如 `~/shendu/group/#tech-discuss`）；
  - **双视图流一键切换 (`Alt+B`)**：可自由在「科技气泡流 (Cyber Bubble)」与「紧凑终端代码日志流 (Terminal Log)」之间切换；
  - 集成**六大办公软件摸鱼伪装中枢下拉菜单**。
- **左侧极客三级频道树 (Channel Explorer)**：
  - 包含 `# 群组频道` 与 `@ 私聊终端` 独立分区，支持未读呼吸绿灯与数量徽章；
  - 命令行式搜索框 `> grep channel / user...` 快速过滤。
- **底部极客指令发射台 (Geek Command Terminal)**：
  - 动态发光命令行提示符 `geek@shendu:~$ `；
  - 快捷插入代码块 `</>`、极客标记 `⚡`、本地图片上传；
  - 右侧高亮 `EXEC` 执行发送按钮，支持 `Enter` 发送、`Shift+Enter` 换行、`Alt+1~6` 摸鱼。
- **右侧极客监视控制台看板 (Geek Inspector Panel)**：
  - 展示频道核心元数据、在线成员终端矩阵（带在线状态点与角色标签），支持点击快速跳转主页。

### 2. 聊天界面六大主流工作软件摸鱼伪装 (`StealthWorkspaceCamouflage.tsx`)
在专业版顶部右上角可随时一键进入全屏仿真工作软件，支持真实发送与接收消息：
- **VS Code 经典编辑器** (`Alt+1`)：活动栏、Explorer 树、代码行号、底部终端命令行真实回车发送消息；
- **IntelliJ IDEA 工程开发** (`Alt+2`)：Project 树、Kotlin/Java 高亮、底部 Run 控制台输入与交互；
- **飞书 (Feishu / Lark)** (`Alt+3`)：商务蓝白/深灰风格、工作消息列表、文档与审批栏、标准办公会话气泡；
- **Excel 电子表格** (`Alt+4`)：真实网格线、A/B/C/D 列与 1/2/3 行标、上方 `fx` 公式编辑栏输入回车直接发消息；
- **Word 方案文档** (`Alt+5`)：经典 Ribbon 顶部功能区、水平文档标尺、A4 标准排版、以“审阅批注”模式输入并发送；
- **PowerPoint (PPT 演示文稿)** (`Alt+6`)：左侧幻灯片缩略图、主画板投影片、底部“演讲者备注”输入即时发送；
- **一键极速避险**：按下键盘 `Esc` 键立即可退回原专业工作台。

### 3. 经典版 100% 纯净保持原貌
- 经典版聊天页面完全保留原汁原味的居中悬浮圆角卡片布局；
- 没有任何多余的摸鱼按钮、没有多余的 HUD 栏或空状态改动，做到彻底的物理级代码解耦。

### 4. 顶栏 32px 极简微型模式切换器 (`Navbar.tsx`)
- 占用空间缩减 80% 以上，专业版呈现翡翠绿终端图标，经典版呈现科技蓝双栏图标。

---

## 🚀 一次性迁移操作指引

打开终端，直接将本文件夹内容覆盖到目标工程：

**Windows PowerShell:**
```powershell
# 1. 复制前端文件
Copy-Item -Path "H:\code\shendu\geek_pro_migration\frontend\*" -Destination "你的前端根目录\" -Recurse -Force

# 2. 复制后端文件
Copy-Item -Path "H:\code\shendu\geek_pro_migration\backend\*" -Destination "你的后端根目录\" -Recurse -Force
```

---

## 验证与测试
迁移包中所有代码已通过 **TypeScript 严格类型检查 (`tsc -b`)** 与 **Vite 生产构建打包 (`npm run build`)**，并在真实浏览器环境中完成了经典版与专业版的比对视效验证。
