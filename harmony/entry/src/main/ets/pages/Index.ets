import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { GlassTabBar } from '../components/GlassTabBar';
import { HomeScreen } from './HomeScreen';
import { ConversationListScreen } from './ConversationListScreen';
import { NotificationScreen } from './NotificationScreen';
import { ProfileScreen } from './ProfileScreen';

/**
 * 鸿蒙原生社区主容器入口 (Index.ets)
 * - 沉浸式安全区拓扑
 * - 整合四大核心视口 (首页/会话/通知/我的)
 * - 沉浸光感悬浮液态玻璃导航栏 (GlassTabBar)
 * - 居中快捷发帖通道
 * - 全局未读角标自动轮询与同步
 */
@Entry
@Component
struct Index {
  @State currentTab: number = 0; // 0: 首页, 1: 会话, 3: 通知, 4: 我的
  @State unreadMessageCount: number = 0;
  @State unreadNotificationCount: number = 0;
  @State showCreatePostDialog: boolean = false;
  @State newPostTitle: string = '';
  @State newPostContent: string = '';
  @State isSubmittingPost: boolean = false;

  aboutToAppear() {
    this.refreshUnreadStats();
  }

  async refreshUnreadStats() {
    // 获取未读消息与通知统计
    const notifRes = await HttpUtils.get<{ unread_count: number }>('/notifications/unread-count');
    if (notifRes.isSuccess && notifRes.data) {
      this.unreadNotificationCount = notifRes.data.unread_count;
    }

    const msgRes = await HttpUtils.get<{ unread_count: number }>('/chat/unread-total');
    if (msgRes.isSuccess && msgRes.data) {
      this.unreadMessageCount = msgRes.data.unread_count;
    }
  }

  async handlePublishPost() {
    if (!this.newPostTitle.trim()) {
      promptAction.showToast({ message: '请输入帖子标题' });
      return;
    }
    if (!this.newPostContent.trim()) {
      promptAction.showToast({ message: '请输入帖子正文' });
      return;
    }
    this.isSubmittingPost = true;
    const res = await HttpUtils.post<{ id: string }>('/posts', {
      title: this.newPostTitle.trim(),
      content_md: this.newPostContent.trim(),
      category_id: 1,
      post_type: 'normal'
    });
    this.isSubmittingPost = false;
    if (res.isSuccess) {
      this.showCreatePostDialog = false;
      this.newPostTitle = '';
      this.newPostContent = '';
      promptAction.showToast({ message: '发布成功' });
      // 切回首页
      this.currentTab = 0;
    } else {
      promptAction.showToast({ message: res.errorMsg || '发布失败' });
    }
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      // 主体内容切换
      Tabs({ index: this.currentTab }) {
        // Tab 0: 首页
        TabContent() {
          HomeScreen({
            onPostClick: (postId: string) => {
              router.pushUrl({
                url: 'pages/PostDetailScreen',
                params: { postId: postId }
              });
            }
          })
        }

        // Tab 1: 会话
        TabContent() {
          ConversationListScreen({
            onConversationClick: (convId: string, isGroup: boolean) => {
              router.pushUrl({
                url: 'pages/ChatScreen',
                params: {
                  roomId: isGroup ? convId : undefined,
                  peerUserId: isGroup ? undefined : convId,
                  title: isGroup ? '群聊讨论' : '私聊'
                }
              });
            }
          })
        }

        // Tab 2: 占位（居中为加号发帖按钮）
        TabContent() {
          Column().width('100%').height('100%')
        }

        // Tab 3: 通知提醒
        TabContent() {
          NotificationScreen()
        }

        // Tab 4: 个人中心
        TabContent() {
          ProfileScreen({
            onPostClick: (postId: string) => {
              router.pushUrl({
                url: 'pages/PostDetailScreen',
                params: { postId: postId }
              });
            },
            onLogout: () => {
              this.currentTab = 0;
            }
          })
        }
      }
      .width('100%')
      .height('100%')
      .barHeight(0) // 隐藏系统原生 TabBar，使用下方沉浸光感悬浮 GlassTabBar
      .scrollable(false)
      .animationDuration(200)

      // 沉浸光感悬浮 GlassTabBar
      GlassTabBar({
        currentTab: this.currentTab,
        unreadMessageCount: this.unreadMessageCount,
        unreadNotificationCount: this.unreadNotificationCount,
        onTabSelected: (tab: number) => {
          this.currentTab = tab;
          this.refreshUnreadStats();
        },
        onCreateClick: () => {
          this.showCreatePostDialog = true;
        }
      })

      // 快速发帖弹窗
      if (this.showCreatePostDialog) {
        Stack() {
          Column() {
            Row() {
              Text('发布新动态')
                .fontSize(16)
                .fontWeight(FontWeight.Bold)
                .fontColor(DeepColors.TextPrimary)

              Blank()

              Text('✕')
                .fontSize(16)
                .fontColor(DeepColors.TextTertiary)
                .onClick(() => {
                  this.showCreatePostDialog = false;
                })
            }
            .width('100%')
            .margin({ bottom: 12 })

            TextInput({ text: this.newPostTitle, placeholder: '动态标题...' })
              .placeholderColor(DeepColors.TextTertiary)
              .fontColor(DeepColors.TextPrimary)
              .fontSize(14)
              .height(42)
              .backgroundColor(DeepColors.SurfaceRaised)
              .borderRadius(8)
              .margin({ bottom: 10 })
              .onChange((val) => {
                this.newPostTitle = val;
              })

            TextArea({ text: this.newPostContent, placeholder: '分享新鲜技术与社区见解...' })
              .placeholderColor(DeepColors.TextTertiary)
              .fontColor(DeepColors.TextPrimary)
              .fontSize(13)
              .height(130)
              .backgroundColor(DeepColors.SurfaceRaised)
              .borderRadius(8)
              .margin({ bottom: 14 })
              .onChange((val) => {
                this.newPostContent = val;
              })

            Row() {
              Button('取消')
                .fontSize(13)
                .fontColor(DeepColors.TextSecondary)
                .backgroundColor(Color.Transparent)
                .onClick(() => {
                  this.showCreatePostDialog = false;
                })

              Button(this.isSubmittingPost ? '发布中...' : '立即发布')
                .fontSize(13)
                .fontColor(DeepColors.TextPrimary)
                .backgroundColor(DeepColors.Primary)
                .margin({ left: 12 })
                .onClick(() => this.handlePublishPost())
            }
            .width('100%')
            .justifyContent(FlexAlign.End)
          }
          .width('90%')
          .padding(20)
          .borderRadius(16)
          .backgroundColor(DeepColors.Surface)
          .border({ width: 0.8, color: DeepColors.GlassBorder, radius: 16 })
        }
        .width('100%')
        .height('100%')
        .backgroundColor('#99000000')
        .alignment(Alignment.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.Background)
    // 沉浸式拓扑拓展至系统状态栏与导航栏
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
  }
}
