import { ConversationItem, GroupRoomItem } from '../model/ChatModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import router from '@ohos.router';

/**
 * 鸿蒙原生会话大厅 (ConversationListScreen ArkUI)
 * 私聊会话 / 社区群聊 双列表切换，支持独立未读角标红点统计与快速接入
 */
@Component
export struct ConversationListScreen {
  @State selectedTab: number = 0; // 0: 私聊, 1: 群聊
  @State conversations: ConversationItem[] = [];
  @State groups: GroupRoomItem[] = [];
  @State isLoading: boolean = true;
  @State privateUnreadCount: number = 0;
  @State groupUnreadCount: number = 0;
  onConversationClick: (id: string, isGroup: boolean, title: string) => void = () => {};

  aboutToAppear() {
    this.refreshAll();
  }

  async refreshAll() {
    this.isLoading = true;
    await Promise.all([this.loadConversations(), this.loadGroups()]);
    this.isLoading = false;
  }

  async loadConversations() {
    try {
      const res = await HttpUtils.get<ConversationItem[]>('api/chat/conversations');
      this.conversations = res || [];
      this.privateUnreadCount = this.conversations.reduce((sum, item) => sum + (item.unread_count || 0), 0);
    } catch (e) {
      console.error('Failed to load conversations: ' + JSON.stringify(e));
    }
  }

  async loadGroups() {
    try {
      const res = await HttpUtils.get<GroupRoomItem[]>('api/chat/groups');
      this.groups = res || [];
      this.groupUnreadCount = this.groups.reduce((sum, item) => sum + (item.unread_count || 0), 0);
    } catch (e) {
      console.error('Failed to load groups: ' + JSON.stringify(e));
    }
  }

  build() {
    Column() {
      // 顶部标题
      Row() {
        Text('会话大厅')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor(DeepColors.TEXT_PRIMARY)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 16, bottom: 12 })

      // 顶部胶囊药丸双 Tab (含未读徽标)
      Row() {
        Row({ space: 4 }) {
          this.TabPill('私聊会话', 0, this.privateUnreadCount)
          this.TabPill('社区群聊', 1, this.groupUnreadCount)
        }
        .padding(4)
        .borderRadius(20)
        .backgroundColor(DeepColors.SURFACE_ELEVATED)
        .border({ width: 0.8, color: DeepColors.BORDER })
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 12 })
      .justifyContent(FlexAlign.Center)

      // 内容列表
      if (this.isLoading && this.conversations.length === 0 && this.groups.length === 0) {
        Column() {
          LoadingProgress().width(36).height(36).color(DeepColors.PRIMARY)
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.selectedTab === 0) {
        this.PrivateChatListView()
      } else {
        this.GroupChatListView()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.BACKGROUND)
  }

  @Builder
  TabPill(label: string, index: number, badgeCount: number) {
    Row({ space: 6 }) {
      Text(label)
        .fontSize(13)
        .fontWeight(this.selectedTab === index ? FontWeight.Bold : FontWeight.Normal)
        .fontColor(this.selectedTab === index ? Color.White : DeepColors.TEXT_TERTIARY)

      if (badgeCount > 0) {
        Text(badgeCount > 99 ? '99+' : `${badgeCount}`)
          .fontSize(9)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
          .padding({ left: 5, right: 5, top: 1, bottom: 1 })
          .borderRadius(8)
          .backgroundColor(DeepColors.ACCENT_ERROR)
      }
    }
    .padding({ left: 18, right: 18, top: 8, bottom: 8 })
    .borderRadius(16)
    .backgroundColor(this.selectedTab === index ? DeepColors.PRIMARY : Color.Transparent)
    .onClick(() => {
      this.selectedTab = index;
    })
  }

  @Builder
  PrivateChatListView() {
    if (this.conversations.length === 0) {
      Column({ space: 8 }) {
        Text('暂无私聊会话')
          .fontSize(13)
          .fontColor(DeepColors.TEXT_TERTIARY)
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    } else {
      List({ space: 10 }) {
        ForEach(this.conversations, (conv: ConversationItem) => {
          ListItem() {
            Row({ space: 12 }) {
              Image(Constants.formatImageUrl(conv.peer_avatar || conv.target_user?.avatarUrl))
                .width(46)
                .height(46)
                .borderRadius(23)
                .objectFit(ImageFit.Cover)
                .alt($r('sys.media.ohos_ic_public_user_head'))

              Column({ space: 4 }) {
                Row() {
                  Text(conv.peer_nickname || conv.peer_username || conv.target_user?.nickname || '用户')
                    .fontSize(15)
                    .fontWeight(FontWeight.SemiBold)
                    .fontColor(DeepColors.TEXT_PRIMARY)

                  Text((conv.last_message_at || conv.updated_at || '').slice(0, 10))
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                }
                .width('100%')
                .justifyContent(FlexAlign.SpaceBetween)

                Row() {
                  Text(conv.last_message_preview || '暂无消息')
                    .fontSize(13)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)

                  if (conv.unread_count > 0) {
                    Text(conv.unread_count > 99 ? '99+' : `${conv.unread_count}`)
                      .fontSize(9)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(Color.White)
                      .padding({ left: 6, right: 6, top: 1, bottom: 1 })
                      .borderRadius(8)
                      .backgroundColor(DeepColors.ACCENT_ERROR)
                  }
                }
                .width('100%')
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
            }
            .width('100%')
            .padding(14)
            .borderRadius(16)
            .backgroundColor(DeepColors.SURFACE)
            .border({ width: 0.6, color: DeepColors.BORDER })
            .onClick(() => {
              const title = conv.peer_nickname || conv.peer_username || '私信会话';
              this.onConversationClick(conv.id, false, title);
            })
          }
        })
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, bottom: 90 })
    }
  }

  @Builder
  GroupChatListView() {
    if (this.groups.length === 0) {
      Column({ space: 8 }) {
        Text('暂无群聊，快去加入吧')
          .fontSize(13)
          .fontColor(DeepColors.TEXT_TERTIARY)
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    } else {
      List({ space: 10 }) {
        ForEach(this.groups, (group: GroupRoomItem) => {
          ListItem() {
            Row({ space: 12 }) {
              Image(Constants.formatImageUrl(group.avatar_url))
                .width(46)
                .height(46)
                .borderRadius(23)
                .objectFit(ImageFit.Cover)
                .alt($r('sys.media.ohos_ic_public_user_head'))

              Column({ space: 4 }) {
                Row() {
                  Text(group.name)
                    .fontSize(15)
                    .fontWeight(FontWeight.SemiBold)
                    .fontColor(DeepColors.TEXT_PRIMARY)

                  Text(`${group.memberCount || 1} 人`)
                    .fontSize(10)
                    .fontColor(DeepColors.PRIMARY)
                    .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                    .borderRadius(4)
                    .backgroundColor('rgba(59, 130, 246, 0.12)')
                }
                .width('100%')
                .justifyContent(FlexAlign.SpaceBetween)

                Row() {
                  Text(group.last_message_preview || group.description || '极客交流专属群')
                    .fontSize(13)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)

                  if (group.unread_count > 0) {
                    Text(group.unread_count > 99 ? '99+' : `${group.unread_count}`)
                      .fontSize(9)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(Color.White)
                      .padding({ left: 6, right: 6, top: 1, bottom: 1 })
                      .borderRadius(8)
                      .backgroundColor(DeepColors.ACCENT_ERROR)
                  }
                }
                .width('100%')
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
            }
            .width('100%')
            .padding(14)
            .borderRadius(16)
            .backgroundColor(DeepColors.SURFACE)
            .border({ width: 0.6, color: DeepColors.BORDER })
            .onClick(() => {
              this.onConversationClick(group.id, true, group.name);
            })
          }
        })
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, bottom: 90 })
    }
  }
}
