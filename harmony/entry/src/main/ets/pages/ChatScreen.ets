import { ChatMessageItem } from '../model/ChatModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { StorageManager } from '../common/Storage';
import { DiceCube } from '../components/DiceCube';
import router from '@ohos.router';
import promptAction from '@ohos.promptAction';

/**
 * 鸿蒙原生聊天会话与群聊室 (ChatScreen ArkUI)
 * 全面集成 Web 端特性：
 * 1. 顶部悬浮「定位到未读消息」智能胶囊 (jumpToUnread)
 * 2. 往上滑动自动翻页加载更早历史聊天记录 (loadOlderHistory)
 * 3. 仿生 3D 物理旋转象牙骰子 (DiceCube)
 * 4. 右下角「回到最新」浮动按键
 */
@Entry
@Component
export struct ChatScreen {
  @State roomId: string = '';
  @State isGroupChat: boolean = false;
  @State chatTitle: string = '会话详情';
  @State messages: ChatMessageItem[] = [];
  @State inputText: string = '';
  @State isLoading: boolean = true;
  @State currentUserId: string = '';

  // 历史翻页与未读定位状态
  @State unreadAtEntry: number = 0;
  @State currentPage: number = 1;
  @State hasMoreHistory: boolean = true;
  @State isLoadingOlder: boolean = false;
  @State isJumpingToUnread: boolean = false;
  @State showScrollToBottom: boolean = false;

  private scroller: Scroller = new Scroller();

  aboutToAppear() {
    const params = router.getParams() as Record<string, Object>;
    if (params) {
      this.roomId = (params.id as string) || '';
      this.isGroupChat = !!params.isGroup;
      this.chatTitle = (params.title as string) || '会话详情';
      this.unreadAtEntry = (params.unread as number) || 0;
    }
    const currentUser = StorageManager.getInstance().getUser();
    if (currentUser) {
      this.currentUserId = currentUser.id;
    }

    this.loadInitialMessages();
  }

  async loadInitialMessages() {
    this.isLoading = true;
    try {
      const endpoint = this.isGroupChat ? `api/chat/groups/${this.roomId}/messages` : `api/chat/${this.roomId}/messages`;
      const res = await HttpUtils.get<ChatMessageItem[]>(endpoint, { page: 1, size: 50 });
      this.messages = res || [];
      this.currentPage = 1;
      this.hasMoreHistory = (res && res.length >= 50);

      // 进入后自动标记已读
      const markEndpoint = this.isGroupChat ? `api/chat/groups/${this.roomId}/read` : `api/chat/conversations/${this.roomId}/read`;
      HttpUtils.post(markEndpoint).catch((_e) => {});

      // 默认平滑滚动到底部最新消息
      setTimeout(() => {
        if (this.unreadAtEntry === 0 && this.messages.length > 0) {
          this.scroller.scrollEdge(Edge.Bottom);
        }
      }, 200);
    } catch (e) {
      console.error('Failed to load messages: ' + JSON.stringify(e));
    } finally {
      this.isLoading = false;
    }
  }

  // 往上滑动翻页加载更早的历史记录 (对齐 Web 端)
  async loadOlderHistory() {
    if (this.isLoadingOlder || !this.hasMoreHistory) return;
    this.isLoadingOlder = true;
    const nextPage = this.currentPage + 1;
    try {
      const endpoint = this.isGroupChat ? `api/chat/groups/${this.roomId}/messages` : `api/chat/${this.roomId}/messages`;
      const older = await HttpUtils.get<ChatMessageItem[]>(endpoint, { page: nextPage, size: 50 });
      if (!older || older.length === 0) {
        this.hasMoreHistory = false;
      } else {
        const existIds = new Set(this.messages.map((m) => m.id));
        const newOlder = older.filter((m) => !existIds.has(m.id));
        this.messages = [...newOlder, ...this.messages];
        this.currentPage = nextPage;
        this.hasMoreHistory = older.length >= 50;
      }
    } catch (e) {
      console.error('Failed to load older history: ' + JSON.stringify(e));
    } finally {
      this.isLoadingOlder = false;
    }
  }

  // 定位到未读消息位置 (对齐 Web 端 jumpToUnread)
  async jumpToUnread() {
    if (this.unreadAtEntry <= 0 || this.isJumpingToUnread) return;
    this.isJumpingToUnread = true;
    try {
      // 预先拉取足够的历史页数
      const pagesNeeded = Math.min(6, Math.max(1, Math.ceil(this.unreadAtEntry / 50)));
      while (this.currentPage < pagesNeeded && this.hasMoreHistory) {
        await this.loadOlderHistory();
      }

      // 计算目标索引并滚动定位
      const targetIndex = Math.max(0, this.messages.length - this.unreadAtEntry);
      this.scroller.scrollToIndex(targetIndex);
      this.unreadAtEntry = 0;
      this.showScrollToBottom = true;
    } catch (e) {
      console.error('jumpToUnread error: ' + JSON.stringify(e));
    } finally {
      this.isJumpingToUnread = false;
    }
  }

  async sendMessage(textToSend?: string) {
    const content = textToSend || this.inputText.trim();
    if (!content) return;
    this.inputText = '';

    try {
      const endpoint = this.isGroupChat ? `api/chat/groups/${this.roomId}/messages` : `api/chat/${this.roomId}/messages`;
      const newMsg = await HttpUtils.post<ChatMessageItem>(endpoint, { content, msg_type: 'text' });
      if (newMsg && newMsg.id) {
        this.messages.push(newMsg);
        this.scroller.scrollEdge(Edge.Bottom);
      }
    } catch (err) {
      promptAction.showToast({ message: err.message || '发送失败' });
    }
  }

  async rollDice() {
    if (!this.isGroupChat) return;
    try {
      const res = await HttpUtils.post<ChatMessageItem>(`api/chat/groups/${this.roomId}/dice`);
      if (res && res.id) {
        this.messages.push(res);
        this.scroller.scrollEdge(Edge.Bottom);
      }
    } catch (err) {
      promptAction.showToast({ message: err.message || '掷骰子失败' });
    }
  }

  build() {
    Stack({ alignContent: Alignment.TopCenter }) {
      Column() {
        // 1. 沉浸式顶栏
        Row() {
          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(22)
              .fontColor([DeepColors.TEXT_PRIMARY])
          }
          .width(38)
          .height(38)
          .backgroundColor(Color.Transparent)
          .onClick(() => {
            router.back();
          })

          Text(this.chatTitle)
            .fontSize(17)
            .fontWeight(FontWeight.Bold)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .layoutWeight(1)
            .margin({ left: 6 })

          // 右侧互动快捷键
          if (this.isGroupChat) {
            Button({ type: ButtonType.Capsule }) {
              Row({ space: 4 }) {
                SymbolGlyph($r('sys.symbol.dice_fill'))
                  .fontSize(15)
                  .fontColor([DeepColors.PRIMARY])
                Text('摇骰子')
                  .fontSize(11)
                  .fontWeight(FontWeight.SemiBold)
                  .fontColor(DeepColors.PRIMARY)
              }
            }
            .height(30)
            .padding({ left: 10, right: 10 })
            .backgroundColor('rgba(59, 130, 246, 0.12)')
            .onClick(() => {
              this.rollDice();
            })
          }
        }
        .width('100%')
        .padding({ left: 12, right: 16, top: 44, bottom: 10 })
        .backgroundColor(DeepColors.SURFACE)
        .border({ width: { bottom: 0.6 }, color: DeepColors.BORDER })

        // 2. 消息流视口
        Stack({ alignContent: Alignment.BottomEnd }) {
          List({ scroller: this.scroller, space: 12 }) {
            // 顶部历史消息加载提示
            if (this.isLoadingOlder) {
              ListItem() {
                Row({ space: 6 }) {
                  LoadingProgress().width(16).height(16).color(DeepColors.PRIMARY)
                  Text('正在加载更早的消息…').fontSize(12).fontColor(DeepColors.TEXT_TERTIARY)
                }
                .width('100%')
                .justifyContent(FlexAlign.Center)
                .padding({ top: 10, bottom: 6 })
              }
            } else if (this.hasMoreHistory && this.messages.length > 0) {
              ListItem() {
                Row() {
                  Text('加载更早的消息')
                    .fontSize(12)
                    .fontColor(DeepColors.PRIMARY)
                    .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                    .borderRadius(12)
                    .backgroundColor('rgba(59, 130, 246, 0.08)')
                    .border({ width: 0.6, color: 'rgba(59, 130, 246, 0.2)' })
                    .onClick(() => {
                      this.loadOlderHistory();
                    })
                }
                .width('100%')
                .justifyContent(FlexAlign.Center)
                .padding({ top: 8, bottom: 4 })
              }
            }

            // 消息气泡项
            ForEach(this.messages, (msg: ChatMessageItem) => {
              ListItem() {
                this.ChatMessageBubble(msg)
              }
            }, (msg: ChatMessageItem) => msg.id)
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ left: 14, right: 14, top: 10, bottom: 16 })
          // 往上滑到顶部时自动触发加载更早历史
          .onReachStart(() => {
            if (this.hasMoreHistory && !this.isLoadingOlder && this.messages.length > 0) {
              this.loadOlderHistory();
            }
          })
          .onScrollIndex((first: number) => {
            const total = this.messages.length;
            this.showScrollToBottom = (total > 8 && first < total - 6);
          })

          // 右下角「回到最新」浮动按键
          if (this.showScrollToBottom) {
            Button({ type: ButtonType.Capsule }) {
              Row({ space: 4 }) {
                SymbolGlyph($r('sys.symbol.chevron_down'))
                  .fontSize(13)
                  .fontColor([DeepColors.PRIMARY])
                Text('回到最新')
                  .fontSize(11)
                  .fontColor(DeepColors.TEXT_PRIMARY)
              }
            }
            .height(32)
            .padding({ left: 10, right: 10 })
            .borderRadius(16)
            .backgroundColor(DeepColors.SURFACE_ELEVATED)
            .border({ width: 0.8, color: DeepColors.BORDER })
            .margin({ right: 16, bottom: 16 })
            .shadow({ radius: 8, color: 'rgba(0,0,0,0.3)', offsetY: 4 })
            .onClick(() => {
              this.scroller.scrollEdge(Edge.Bottom);
              this.showScrollToBottom = false;
            })
          }
        }
        .layoutWeight(1)
        .width('100%')

        // 3. 底部输入控制条
        Row({ space: 8 }) {
          TextInput({ text: this.inputText, placeholder: '发送消息…' })
            .layoutWeight(1)
            .height(40)
            .borderRadius(14)
            .backgroundColor(DeepColors.SURFACE_ELEVATED)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .placeholderColor(DeepColors.TEXT_TERTIARY)
            .onChange((val) => {
              this.inputText = val;
            })
            .onSubmit(() => {
              this.sendMessage();
            })

          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.paperplane_fill'))
              .fontSize(18)
              .fontColor([Color.White])
          }
          .width(40)
          .height(40)
          .backgroundColor(DeepColors.PRIMARY)
          .onClick(() => {
            this.sendMessage();
          })
        }
        .width('100%')
        .padding({ left: 14, right: 14, top: 8, bottom: 20 })
        .backgroundColor(DeepColors.SURFACE)
        .border({ width: { top: 0.6 }, color: DeepColors.BORDER })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(DeepColors.BACKGROUND)

      // 4. 顶部未读消息定位胶囊 (jumpToUnread，对齐 Web 端)
      if (this.unreadAtEntry > 0) {
        Row({ space: 8 }) {
          Row({ space: 6 }) {
            SymbolGlyph($r('sys.symbol.arrow_up'))
              .fontSize(14)
              .fontColor([Color.White])

            Text(this.isJumpingToUnread ? '正在定位未读…' : `${this.unreadAtEntry} 条未读 · 回到上次位置`)
              .fontSize(12)
              .fontWeight(FontWeight.SemiBold)
              .fontColor(Color.White)
          }
          .onClick(() => {
            this.jumpToUnread();
          })

          // 关闭胶囊按键
          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.xmark'))
              .fontSize(10)
              .fontColor([Color.White])
          }
          .width(20)
          .height(20)
          .backgroundColor('rgba(255, 255, 255, 0.25)')
          .onClick(() => {
            this.unreadAtEntry = 0;
          })
        }
        .padding({ left: 14, right: 10, top: 7, bottom: 7 })
        .borderRadius(20)
        .backgroundColor(DeepColors.PRIMARY)
        .shadow({ radius: 12, color: 'rgba(0, 0, 0, 0.35)', offsetY: 4 })
        .margin({ top: 96 })
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  ChatMessageBubble(msg: ChatMessageItem) {
    Row() {
      if (msg.sender_id === this.currentUserId) {
        // 我发送的消息：右侧靠齐
        Row({ space: 8 }) {
          Blank()
          Column({ space: 4 }) {
            if (msg.content.includes('[骰子]')) {
              // 提取骰子点数
              DiceCube({ targetPoint: 6 })
            } else {
              Text(msg.content)
                .fontSize(14)
                .fontColor(Color.White)
                .lineHeight(20)
                .padding({ left: 14, right: 14, top: 10, bottom: 10 })
                .borderRadius(16)
                .backgroundColor(DeepColors.PRIMARY)
            }
          }
          .alignItems(HorizontalAlign.End)

          Image(Constants.formatImageUrl(msg.sender_avatar))
            .width(36)
            .height(36)
            .borderRadius(18)
            .objectFit(ImageFit.Cover)
            .alt($r('sys.media.ohos_ic_public_user_head'))
        }
        .width('100%')
        .justifyContent(FlexAlign.End)
      } else {
        // 他人发送的消息：左侧靠齐
        Row({ space: 8 }) {
          Image(Constants.formatImageUrl(msg.sender_avatar))
            .width(36)
            .height(36)
            .borderRadius(18)
            .objectFit(ImageFit.Cover)
            .alt($r('sys.media.ohos_ic_public_user_head'))

          Column({ space: 4 }) {
            Row({ space: 6 }) {
              Text(msg.sender_nickname || msg.sender_username)
                .fontSize(12)
                .fontColor(DeepColors.TEXT_TERTIARY)
              Text(`Lv.${msg.sender_level || 1}`)
                .fontSize(8)
                .fontWeight(FontWeight.Bold)
                .fontColor(DeepColors.PRIMARY)
                .padding({ left: 4, right: 4, top: 1, bottom: 1 })
                .borderRadius(4)
                .backgroundColor('rgba(59, 130, 246, 0.12)')
            }

            if (msg.content.includes('[骰子]')) {
              DiceCube({ targetPoint: 6 })
            } else {
              Text(msg.content)
                .fontSize(14)
                .fontColor(DeepColors.TEXT_PRIMARY)
                .lineHeight(20)
                .padding({ left: 14, right: 14, top: 10, bottom: 10 })
                .borderRadius(16)
                .backgroundColor(DeepColors.SURFACE_ELEVATED)
                .border({ width: 0.6, color: DeepColors.BORDER })
            }
          }
          .alignItems(HorizontalAlign.Start)

          Blank()
        }
        .width('100%')
        .justifyContent(FlexAlign.Start)
      }
    }
    .width('100%')
  }
}
