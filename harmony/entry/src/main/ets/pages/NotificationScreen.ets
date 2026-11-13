import promptAction from '@ohos.promptAction';
import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';

export interface AppNotification {
  id: string;
  type: string;
  title?: string;
  content: string;
  target_id?: string;
  is_read: boolean;
  created_at: string;
  sender_username?: string;
  sender_avatar?: string;
}

export interface GroupJoinRequest {
  id: string;
  room_id: string;
  room_name?: string;
  user_id: string;
  username: string;
  avatar_url?: string;
  reason?: string;
  status: string;
  created_at: string;
}

/**
 * 鸿蒙原生通知中心与入群申请审批 (NotificationScreen ArkUI)
 * - 纯粹质感，沉浸卡片，无任何 Emoji
 * - 双 Tab：互动通知 / 入群申请审批 (Requests Management)
 * - 一键全部标为已读
 * - 一键同意 / 拒绝入群申请
 * - 鸿蒙官方沉浸光感
 */
@Component
export struct NotificationScreen {
  @State selectedTab: number = 0; // 0: 消息通知, 1: 入群审批
  @State notifications: AppNotification[] = [];
  @State groupRequests: GroupJoinRequest[] = [];
  @State isLoading: boolean = false;

  aboutToAppear() {
    this.loadData();
  }

  async loadData() {
    this.isLoading = true;
    if (this.selectedTab === 0) {
      const res = await HttpUtils.get<AppNotification[]>('api/notifications');
      if (res.isSuccess && res.data) {
        this.notifications = res.data;
      }
    } else {
      const res = await HttpUtils.get<GroupJoinRequest[]>('api/chat/rooms/requests');
      if (res.isSuccess && res.data) {
        this.groupRequests = res.data;
      }
    }
    this.isLoading = false;
  }

  async handleMarkAllAsRead() {
    const res = await HttpUtils.post<{ success: boolean }>('api/notifications/read-all');
    if (res.isSuccess) {
      this.notifications.forEach((item) => {
        item.is_read = true;
      });
      promptAction.showToast({ message: '已全部标为已读' });
    }
  }

  async handleApproveRequest(req: GroupJoinRequest) {
    const res = await HttpUtils.post<{ success: boolean }>(`api/chat/rooms/requests/${req.id}/approve`);
    if (res.isSuccess) {
      req.status = 'approved';
      promptAction.showToast({ message: `已同意 ${req.username} 加入群聊` });
    } else {
      promptAction.showToast({ message: res.errorMsg || '审批失败' });
    }
  }

  async handleRejectRequest(req: GroupJoinRequest) {
    const res = await HttpUtils.post<{ success: boolean }>(`api/chat/rooms/requests/${req.id}/reject`);
    if (res.isSuccess) {
      req.status = 'rejected';
      promptAction.showToast({ message: `已拒绝 ${req.username} 入群申请` });
    } else {
      promptAction.showToast({ message: res.errorMsg || '操作失败' });
    }
  }

  build() {
    Column() {
      // 沉浸顶栏 (backgroundBlurStyle)
      Row() {
        Text('通知中心')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(DeepColors.TEXT_PRIMARY)

        Blank()

        if (this.selectedTab === 0 && this.notifications.length > 0) {
          Row() {
            Text('全部已读')
              .fontSize(12)
              .fontColor(DeepColors.PRIMARY)
          }
          .padding({ left: 10, right: 10, top: 5, bottom: 5 })
          .borderRadius(8)
          .backgroundColor('rgba(59, 130, 246, 0.12)')
          .border({ width: 0.6, color: 'rgba(59, 130, 246, 0.3)' })
          .onClick(() => this.handleMarkAllAsRead())
        }
      }
      .width('100%')
      .height(52)
      .padding({ left: 16, right: 16 })
      .alignItems(VerticalAlign.Center)

      // Tab 分类切换
      Row() {
        Row() {
          Text('互动通知')
            .fontSize(13)
            .fontWeight(this.selectedTab === 0 ? FontWeight.Bold : FontWeight.Normal)
            .fontColor(this.selectedTab === 0 ? Color.White : DeepColors.TEXT_SECONDARY)
        }
        .padding({ left: 16, right: 16, top: 7, bottom: 7 })
        .borderRadius(8)
        .backgroundColor(this.selectedTab === 0 ? DeepColors.PRIMARY : Color.Transparent)
        .onClick(() => {
          this.selectedTab = 0;
          this.loadData();
        })

        Row() {
          Text('入群申请审批')
            .fontSize(13)
            .fontWeight(this.selectedTab === 1 ? FontWeight.Bold : FontWeight.Normal)
            .fontColor(this.selectedTab === 1 ? Color.White : DeepColors.TEXT_SECONDARY)
        }
        .padding({ left: 16, right: 16, top: 7, bottom: 7 })
        .borderRadius(8)
        .backgroundColor(this.selectedTab === 1 ? DeepColors.PRIMARY : Color.Transparent)
        .onClick(() => {
          this.selectedTab = 1;
          this.loadData();
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 12 })

      // 列表内容
      if (this.selectedTab === 0) {
        if (this.notifications.length === 0 && !this.isLoading) {
          Column() {
            Text('暂无新通知')
              .fontSize(13)
              .fontColor(DeepColors.TEXT_TERTIARY)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List({ space: 10 }) {
            ForEach(this.notifications, (item: AppNotification) => {
              ListItem() {
                Row() {
                  // 未读高亮光点
                  if (!item.is_read) {
                    Circle({ width: 6, height: 6 })
                      .fill(DeepColors.PRIMARY)
                      .margin({ right: 10 })
                  }

                  Column({ space: 3 }) {
                    Text(item.title || (item.type === 'like' ? '点赞提醒' : '互动提醒'))
                      .fontSize(14)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DeepColors.TEXT_PRIMARY)

                    Text(item.content)
                      .fontSize(13)
                      .fontColor(DeepColors.TEXT_SECONDARY)
                      .lineHeight(18)
                      .margin({ top: 2, bottom: 2 })

                    Text(item.created_at || '刚刚')
                      .fontSize(10)
                      .fontColor(DeepColors.TEXT_TERTIARY)
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                }
                .width('100%')
                .padding(14)
                .borderRadius(12)
                .backgroundColor(item.is_read ? 'rgba(28, 28, 30, 0.45)' : 'rgba(28, 28, 30, 0.75)')
                .border({ width: 0.6, color: item.is_read ? 'rgba(255, 255, 255, 0.05)' : 'rgba(59, 130, 246, 0.25)', radius: 12 })
              }
              .padding({ left: 16, right: 16 })
            }, (item: AppNotification) => item.id)

            ListItem() {
              Column().height(80)
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
      } else {
        if (this.groupRequests.length === 0 && !this.isLoading) {
          Column() {
            Text('暂无待审批的入群申请')
              .fontSize(13)
              .fontColor(DeepColors.TEXT_TERTIARY)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List({ space: 10 }) {
            ForEach(this.groupRequests, (req: GroupJoinRequest) => {
              ListItem() {
                Column({ space: 8 }) {
                  Row() {
                    Image(req.avatar_url || '')
                      .width(40)
                      .height(40)
                      .borderRadius(20)
                      .backgroundColor('rgba(255, 255, 255, 0.05)')
                      .border({ width: 1, color: 'rgba(255, 255, 255, 0.12)' })
                      .alt('')

                    Column({ space: 2 }) {
                      Text(req.username)
                        .fontSize(14)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(DeepColors.TEXT_PRIMARY)

                      Text(`申请加入群聊: ${req.room_name || '社区讨论组'}`)
                        .fontSize(11)
                        .fontColor(DeepColors.TEXT_TERTIARY)
                    }
                    .alignItems(HorizontalAlign.Start)
                    .margin({ left: 10 })
                    .layoutWeight(1)

                    if (req.status === 'pending') {
                      Row({ space: 6 }) {
                        Button('拒绝')
                          .fontSize(11)
                          .fontColor(DeepColors.TEXT_SECONDARY)
                          .backgroundColor('rgba(255, 255, 255, 0.06)')
                          .height(28)
                          .padding({ left: 10, right: 10 })
                          .borderRadius(6)
                          .onClick(() => this.handleRejectRequest(req))

                        Button('同意')
                          .fontSize(11)
                          .fontColor(Color.White)
                          .backgroundColor(DeepColors.PRIMARY)
                          .height(28)
                          .padding({ left: 10, right: 10 })
                          .borderRadius(6)
                          .onClick(() => this.handleApproveRequest(req))
                      }
                    } else {
                      Text(req.status === 'approved' ? '已同意' : '已拒绝')
                        .fontSize(12)
                        .fontColor(req.status === 'approved' ? DeepColors.ACCENT_SUCCESS : DeepColors.TEXT_TERTIARY)
                    }
                  }
                  .width('100%')
                  .alignItems(VerticalAlign.Center)

                  if (req.reason) {
                    Text(`申请理由: ${req.reason}`)
                      .fontSize(12)
                      .fontColor(DeepColors.TEXT_SECONDARY)
                      .margin({ left: 50 })
                  }
                }
                .width('100%')
                .padding(14)
                .borderRadius(12)
                .backgroundColor('rgba(28, 28, 30, 0.55)')
                .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
              }
              .padding({ left: 16, right: 16 })
            }, (req: GroupJoinRequest) => req.id)

            ListItem() {
              Column().height(80)
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.BACKGROUND)
  }
}
