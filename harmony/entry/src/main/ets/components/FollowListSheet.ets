import { UserProfile } from '../model/UserModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import promptAction from '@ohos.promptAction';

export interface FollowUserItem {
  id: string;
  username: string;
  nickname?: string;
  avatar_url?: string;
  level: number;
  bio?: string;
  is_following: boolean;
}

/**
 * 关注与粉丝列表抽屉 (FollowListSheet)
 * - 纯净科技感排版，无 Emoji
 * - 关注列表 (Following) / 粉丝列表 (Followers) 双 Tab
 * - 实时互关与取关操作
 * - 鸿蒙官方沉浸光感背景
 */
@Component
export struct FollowListSheet {
  @Prop targetUsername: string = '';
  @Prop initialTab: number = 0; // 0: 关注, 1: 粉丝
  @State selectedTab: number = 0;
  @State users: FollowUserItem[] = [];
  @State isLoading: boolean = true;
  onUserClick: (username: string, userId: string) => void = () => {};

  aboutToAppear() {
    this.selectedTab = this.initialTab;
    this.loadUsers();
  }

  async loadUsers() {
    this.isLoading = true;
    const endpoint = this.selectedTab === 0
      ? `api/users/${this.targetUsername}/following`
      : `api/users/${this.targetUsername}/followers`;

    const res = await HttpUtils.get<FollowUserItem[]>(endpoint);
    this.isLoading = false;
    if (res.isSuccess && res.data) {
      this.users = res.data;
    } else {
      this.users = [];
    }
  }

  async toggleFollow(user: FollowUserItem) {
    if (user.is_following) {
      const res = await HttpUtils.delete<{ success: boolean }>(`api/users/${user.username}/follow`);
      if (res.isSuccess) {
        user.is_following = false;
        promptAction.showToast({ message: `已取消关注 @${user.username}` });
      }
    } else {
      const res = await HttpUtils.post<{ success: boolean }>(`api/users/${user.username}/follow`);
      if (res.isSuccess) {
        user.is_following = true;
        promptAction.showToast({ message: `已关注 @${user.username}` });
      }
    }
  }

  build() {
    Column() {
      // 顶部拖拽把手
      Row()
        .width(36)
        .height(4)
        .borderRadius(2)
        .backgroundColor(DeepColors.BORDER)
        .margin({ top: 12, bottom: 12 })

      // 双 Tab 胶囊切换
      Row({ space: 8 }) {
        Row() {
          Text('关注列表')
            .fontSize(13)
            .fontWeight(this.selectedTab === 0 ? FontWeight.Bold : FontWeight.Normal)
            .fontColor(this.selectedTab === 0 ? DeepColors.TEXT_PRIMARY : DeepColors.TEXT_SECONDARY)
        }
        .padding({ left: 18, right: 18, top: 6, bottom: 6 })
        .borderRadius(8)
        .backgroundColor(this.selectedTab === 0 ? 'rgba(59, 130, 246, 0.15)' : Color.Transparent)
        .border({ width: this.selectedTab === 0 ? 0.8 : 0, color: 'rgba(59, 130, 246, 0.4)', radius: 8 })
        .onClick(() => {
          this.selectedTab = 0;
          this.loadUsers();
        })

        Row() {
          Text('粉丝列表')
            .fontSize(13)
            .fontWeight(this.selectedTab === 1 ? FontWeight.Bold : FontWeight.Normal)
            .fontColor(this.selectedTab === 1 ? DeepColors.TEXT_PRIMARY : DeepColors.TEXT_SECONDARY)
        }
        .padding({ left: 18, right: 18, top: 6, bottom: 6 })
        .borderRadius(8)
        .backgroundColor(this.selectedTab === 1 ? 'rgba(59, 130, 246, 0.15)' : Color.Transparent)
        .border({ width: this.selectedTab === 1 ? 0.8 : 0, color: 'rgba(59, 130, 246, 0.4)', radius: 8 })
        .onClick(() => {
          this.selectedTab = 1;
          this.loadUsers();
        })
      }
      .margin({ bottom: 14 })

      // 用户列表
      if (this.isLoading) {
        LoadingProgress()
          .width(32)
          .height(32)
          .color(DeepColors.PRIMARY)
          .margin({ top: 40, bottom: 40 })
      } else if (this.users.length === 0) {
        Column({ space: 8 }) {
          Text('暂无相关用户')
            .fontSize(13)
            .fontColor(DeepColors.TEXT_TERTIARY)
        }
        .width('100%')
        .padding({ top: 40, bottom: 40 })
        .alignItems(HorizontalAlign.Center)
      } else {
        List({ space: 8 }) {
          ForEach(this.users, (user: FollowUserItem) => {
            ListItem() {
              Row() {
                // 头像
                Image(Constants.formatImageUrl(user.avatar_url))
                  .width(42)
                  .height(42)
                  .borderRadius(21)
                  .backgroundColor(DeepColors.SURFACE_ELEVATED)
                  .alt('')
                  .onClick(() => {
                    this.onUserClick(user.username, user.id);
                  })

                Column({ space: 3 }) {
                  Row({ space: 6 }) {
                    Text(user.nickname || user.username)
                      .fontSize(14)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DeepColors.TEXT_PRIMARY)

                    // 防变形等级徽章
                    Row() {
                      Text(`Lv.${user.level || 1}`)
                        .fontSize(9)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(DeepColors.PRIMARY)
                    }
                    .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                    .borderRadius(4)
                    .backgroundColor('rgba(59, 130, 246, 0.12)')
                  }
                  .alignItems(VerticalAlign.Center)

                  Text(`@${user.username}`)
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)

                  if (user.bio) {
                    Text(user.bio)
                      .fontSize(11)
                      .fontColor(DeepColors.TEXT_SECONDARY)
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                  }
                }
                .alignItems(HorizontalAlign.Start)
                .margin({ left: 12 })
                .layoutWeight(1)

                // 关注/取关微按键
                Button(user.is_following ? '已关注' : '+ 关注')
                  .fontSize(11)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(user.is_following ? DeepColors.TEXT_SECONDARY : DeepColors.TEXT_PRIMARY)
                  .height(28)
                  .padding({ left: 12, right: 12 })
                  .borderRadius(14)
                  .backgroundColor(user.is_following ? 'rgba(255, 255, 255, 0.06)' : DeepColors.PRIMARY)
                  .border({ width: user.is_following ? 0.6 : 0, color: 'rgba(255, 255, 255, 0.12)' })
                  .onClick(() => this.toggleFollow(user))
              }
              .width('100%')
              .padding({ left: 14, right: 14, top: 10, bottom: 10 })
              .borderRadius(12)
              .backgroundColor('rgba(28, 28, 30, 0.55)')
              .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
            }
          }, (user: FollowUserItem) => user.id)
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: 16, right: 16, bottom: 20 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundBlurStyle(BlurStyle.Regular, { colorMode: ThemeColorMode.DARK })
  }
}
