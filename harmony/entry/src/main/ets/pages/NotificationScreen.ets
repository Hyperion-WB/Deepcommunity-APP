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
 * 鸿蒙原生通知中心与入群申请审批 (NotificationScreen)
 * - 纯粹质感，沉浸卡片
 * - 双 Tab：消息通知 / 入群申请审批 (Requests Management)
 * - 全部标为已读
 * - 一键同意 / 拒绝入群申请
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
      const res = await HttpUtils.get<AppNotification[]>('/notifications');
      if (res.isSuccess && res.data) {
        this.notifications = res.data;
      }
    } else {
      const res = await HttpUtils.get<GroupJoinRequest[]>('/chat/rooms/requests');
      if (res.isSuccess && res.data) {
        this.groupRequests = res.data;
      }
    }
    this.isLoading = false;
  }

  async handleMarkAllAsRead() {
    const res = await HttpUtils.post<{ success: boolean }>('/notifications/read-all');
    if (res.isSuccess) {
      this.notifications.forEach((item) => {
        item.is_read = true;
      });
      promptAction.showToast({ message: '已全部标为已读' });
    }
  }

  async handleApproveRequest(req: GroupJoinRequest) {
    const res = await HttpUtils.post<{ success: boolean }>(`/chat/rooms/requests/${req.id}/approve`);
    if (res.isSuccess) {
      req.status = 'approved';
      promptAction.showToast({ message: `已同意 ${req.username} 加入群聊` });
    } else {
      promptAction.showToast({ message: res.errorMsg || '审批失败' });
    }
  }

  async handleRejectRequest(req: GroupJoinRequest) {
    const res = await HttpUtils.post<{ success: boolean }>(`/chat/rooms/requests/${req.id}/reject`);
    if (res.isSuccess) {
      req.status = 'rejected';
      promptAction.showToast({ message: `已拒绝 ${req.username} 入群申请` });
    } else {
      promptAction.showToast({ message: res.errorMsg || '操作失败' });
    }
  }

  build() {
    Column() {
      // 沉浸顶栏
      Row() {
        Text('通知中心')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(DeepColors.TextPrimary)

        Blank()

        if (this.selectedTab === 0 && this.notifications.length > 0) {
          Row() {
            Text('全部已读')
              .fontSize(12)
              .fontColor(DeepColors.Primary)
          }
          .padding({ left: 10, right: 10, top: 4, bottom: 4 })
          .borderRadius(6)
          .backgroundColor('#1A3B82F6')
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
            .fontColor(this.selectedTab === 0 ? DeepColors.TextPrimary : DeepColors.TextSecondary)
        }
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .borderRadius(6)
        .backgroundColor(this.selectedTab === 0 ? DeepColors.SurfaceRaised : Color.Transparent)
        .onClick(() => {
          this.selectedTab = 0;
          this.loadData();
        })

        Row() {
          Text('入群申请审批')
            .fontSize(13)
            .fontWeight(this.selectedTab === 1 ? FontWeight.Bold : FontWeight.Normal)
            .fontColor(this.selectedTab === 1 ? DeepColors.TextPrimary : DeepColors.TextSecondary)
        }
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .borderRadius(6)
        .backgroundColor(this.selectedTab === 1 ? DeepColors.SurfaceRaised : Color.Transparent)
        .onClick(() => {
          this.selectedTab = 1;
          this.loadData();
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 10 })

      // 列表内容
      if (this.selectedTab === 0) {
        if (this.notifications.length === 0 && !this.isLoading) {
          Column() {
            Text('暂无新通知')
              .fontSize(13)
              .fontColor(DeepColors.TextTertiary)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List() {
            ForEach(this.notifications, (item: AppNotification) => {
              ListItem() {
                Row() {
                  // 未读红点
                  if (!item.is_read) {
                    Circle({ width: 7, height: 7 })
                      .fill(DeepColors.Primary)
                      .margin({ right: 10 })
                  }

                  Column() {
                    Text(item.title || (item.type === 'like' ? '点赞提醒' : '新互动提醒'))
                      .fontSize(14)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DeepColors.TextPrimary)

                    Text(item.content)
                      .fontSize(13)
                      .fontColor(DeepColors.TextSecondary)
                      .lineHeight(18)
                      .margin({ top: 4, bottom: 4 })

                    Text(item.created_at || '刚刚')
                      .fontSize(11)
                      .fontColor(DeepColors.TextTertiary)
                  }
                  .alignItems(HorizontalAlign.Start)
                  .layoutWeight(1)
                }
                .width('100%')
                .padding(14)
                .borderRadius(12)
                .backgroundColor(item.is_read ? DeepColors.Surface : DeepColors.SurfaceRaised)
                .border({ width: 0.6, color: DeepColors.GlassBorder, radius: 12 })
              }
              .padding({ left: 16, right: 16, bottom: 10 })
            }, (item: AppNotification) => item.id)

            ListItem() {
              Column().height(60)
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
              .fontColor(DeepColors.TextTertiary)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List() {
            ForEach(this.groupRequests, (req: GroupJoinRequest) => {
              ListItem() {
                Column() {
                  Row() {
                    Image(req.avatar_url || '')
                      .width(40)
                      .height(40)
                      .borderRadius(20)
                      .backgroundColor(DeepColors.SurfaceRaised)
                      .alt('')

                    Column() {
                      Text(req.username)
                        .fontSize(14)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(DeepColors.TextPrimary)

                      Text(`申请加入群聊: ${req.room_name || '社区讨论组'}`)
                        .fontSize(11)
                        .fontColor(DeepColors.TextTertiary)
                        .margin({ top: 2 })
                    }
                    .alignItems(HorizontalAlign.Start)
                    .margin({ left: 10 })
                    .layoutWeight(1)

                    if (req.status === 'pending') {
                      Row() {
                        Button('拒绝')
                          .fontSize(11)
                          .fontColor(DeepColors.TextSecondary)
                          .backgroundColor(DeepColors.SurfaceRaised)
                          .height(28)
                          .padding({ left: 10, right: 10 })
                          .onClick(() => this.handleRejectRequest(req))

                        Button('同意')
                          .fontSize(11)
                          .fontColor(DeepColors.TextPrimary)
                          .backgroundColor(DeepColors.Primary)
                          .height(28)
                          .padding({ left: 10, right: 10 })
                          .margin({ left: 6 })
                          .onClick(() => this.handleApproveRequest(req))
                      }
                    } else {
                      Text(req.status === 'approved' ? '已同意' : '已拒绝')
                        .fontSize(12)
                        .fontColor(req.status === 'approved' ? DeepColors.Success : DeepColors.TextTertiary)
                    }
                  }
                  .width('100%')
                  .alignItems(VerticalAlign.Center)

                  if (req.reason) {
                    Text(`申请理由: ${req.reason}`)
                      .fontSize(12)
                      .fontColor(DeepColors.TextSecondary)
                      .margin({ left: 50, top: 8 })
                  }
                }
                .width('100%')
                .padding(14)
                .borderRadius(12)
                .backgroundColor(DeepColors.Surface)
                .border({ width: 0.6, color: DeepColors.GlassBorder, radius: 12 })
              }
              .padding({ left: 16, right: 16, bottom: 10 })
            }, (req: GroupJoinRequest) => req.id)

            ListItem() {
              Column().height(60)
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.Background)
  }
}
