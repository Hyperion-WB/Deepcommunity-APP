import { UserProfile } from '../model/UserModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { FollowListSheet } from './FollowListSheet';
import promptAction from '@ohos.promptAction';

/**
 * 鸿蒙他人个人主页半屏弹窗 (UserProfileSheet ArkUI)
 * - 等级 (Lv.X) 独立居中排版，绝不挤压变形
 * - 纯净科技质感，无任何 Emoji
 * - 点击关注/粉丝唤起列表抽屉
 * - 用户拉黑/解除拉黑通道
 * - 鸿蒙官方沉浸光感
 */
@Component
export struct UserProfileSheet {
  @Prop username: string = '';
  @Prop userId: string = '';
  @State userProfile: UserProfile | null = null;
  @State isLoading: boolean = true;
  @State isFollowing: boolean = false;
  @State isFollowLoading: boolean = false;
  @State isBlocked: boolean = false;
  @State showFollowSheet: boolean = false;
  @State followInitialTab: number = 0;

  onClose: () => void = () => {};
  onNavigateToChat: (userId: string) => void = () => {};

  aboutToAppear() {
    this.loadProfile();
  }

  async loadProfile() {
    this.isLoading = true;
    try {
      const identifier = this.username || this.userId;
      const res = await HttpUtils.get<UserProfile>(`api/users/profile/${identifier}`);
      if (res.isSuccess && res.data) {
        this.userProfile = res.data;
        this.isFollowing = !!res.data.isFollowing;
      }
    } catch (e) {
      console.error('Failed to load profile: ' + JSON.stringify(e));
    } finally {
      this.isLoading = false;
    }
  }

  async toggleFollow() {
    if (this.isFollowLoading || !this.userProfile) return;
    this.isFollowLoading = true;
    try {
      const identifier = this.userProfile.username;
      if (this.isFollowing) {
        const res = await HttpUtils.delete<{ success: boolean }>(`api/users/${identifier}/follow`);
        if (res.isSuccess) {
          this.isFollowing = false;
          promptAction.showToast({ message: '已取消关注' });
        }
      } else {
        const res = await HttpUtils.post<{ success: boolean }>(`api/users/${identifier}/follow`);
        if (res.isSuccess) {
          this.isFollowing = true;
          promptAction.showToast({ message: '关注成功' });
        }
      }
    } catch (err) {
      promptAction.showToast({ message: err.message || '操作失败' });
    } finally {
      this.isFollowLoading = false;
    }
  }

  async toggleBlock() {
    if (!this.userProfile) return;
    if (this.isBlocked) {
      const res = await HttpUtils.delete<{ success: boolean }>(`api/users/blocks/${this.userProfile.id}`);
      if (res.isSuccess) {
        this.isBlocked = false;
        promptAction.showToast({ message: '已解除屏蔽' });
      }
    } else {
      const res = await HttpUtils.post<{ success: boolean }>('api/users/blocks', {
        user_id: this.userProfile.id
      });
      if (res.isSuccess) {
        this.isBlocked = true;
        this.isFollowing = false;
        promptAction.showToast({ message: '已将该用户加入黑名单' });
      }
    }
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Column() {
        // 顶部把手与操作按键
        Row() {
          Blank()
          Row()
            .width(36)
            .height(4)
            .borderRadius(2)
            .backgroundColor(DeepColors.BORDER)

          Blank()
          // 屏蔽微按键
          Button(this.isBlocked ? '已屏蔽' : '屏蔽')
            .fontSize(10)
            .fontColor(this.isBlocked ? DeepColors.PRIMARY : DeepColors.TEXT_TERTIARY)
            .backgroundColor(Color.Transparent)
            .onClick(() => {
              this.toggleBlock();
            })
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 12, bottom: 8 })

        if (this.isLoading) {
          LoadingProgress()
            .width(36)
            .height(36)
            .color(DeepColors.PRIMARY)
            .margin({ top: 60, bottom: 60 })
        } else if (this.userProfile) {
          // 头像
          Image(Constants.formatImageUrl(this.userProfile.avatarUrl))
            .width(76)
            .height(76)
            .borderRadius(38)
            .border({ width: 2, color: 'rgba(255, 255, 255, 0.15)' })
            .objectFit(ImageFit.Cover)
            .alt('')

          // 1. 用户名 / 昵称
          Text(this.userProfile.nickname || this.userProfile.username || '社区居民')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .margin({ top: 10 })

          // 2. 等级 (Lv.X) 独立居中排版，避免横向挤压变形
          Row() {
            Text(`Lv.${this.userProfile.level || 1}`)
              .fontSize(11)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.PRIMARY)
              .padding({ left: 8, right: 8, top: 2, bottom: 2 })
              .borderRadius(6)
              .backgroundColor('rgba(59, 130, 246, 0.14)')
              .border({ width: 0.6, color: 'rgba(59, 130, 246, 0.3)' })
          }
          .margin({ top: 6, bottom: 4 })

          // 3. 唯一账号 ID
          Text(`@${this.userProfile.username}`)
            .fontSize(12)
            .fontColor(DeepColors.TEXT_TERTIARY)

          // 简介
          Text(this.userProfile.bio || '代码改变世界，探索原生边界。')
            .fontSize(13)
            .fontColor(DeepColors.TEXT_SECONDARY)
            .textAlign(TextAlign.Center)
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .padding({ left: 24, right: 24 })
            .margin({ top: 12 })

          // 统计数据方块 (支持点击查看关注/粉丝列表)
          Row() {
            this.StatBlock('动态', `${this.userProfile.postCount || 0}`, null)
            Divider().vertical(true).height(20).color(DeepColors.BORDER)
            this.StatBlock('关注', `${this.userProfile.followingCount || 0}`, 0)
            Divider().vertical(true).height(20).color(DeepColors.BORDER)
            this.StatBlock('粉丝', `${this.userProfile.followersCount || 0}`, 1)
          }
          .width('90%')
          .padding({ top: 14, bottom: 14 })
          .borderRadius(14)
          .backgroundColor('rgba(28, 28, 30, 0.55)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
          .justifyContent(FlexAlign.SpaceEvenly)
          .margin({ top: 16 })

          // 底部操作按钮
          Row({ space: 12 }) {
            Button(this.isFollowing ? '已关注' : '+ 关注')
              .layoutWeight(1)
              .height(42)
              .borderRadius(12)
              .backgroundColor(this.isFollowing ? 'rgba(255, 255, 255, 0.08)' : DeepColors.PRIMARY)
              .fontColor(this.isFollowing ? DeepColors.TEXT_SECONDARY : Color.White)
              .border({ width: this.isFollowing ? 0.8 : 0, color: 'rgba(255, 255, 255, 0.12)' })
              .onClick(() => {
                this.toggleFollow();
              })

            Button('发私信')
              .layoutWeight(1)
              .height(42)
              .borderRadius(12)
              .backgroundColor('rgba(28, 28, 30, 0.65)')
              .fontColor(DeepColors.TEXT_PRIMARY)
              .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
              .onClick(() => {
                this.onClose();
                if (this.userProfile) {
                  this.onNavigateToChat(this.userProfile.id);
                }
              })
          }
          .width('90%')
          .margin({ top: 20, bottom: 20 })
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 24 })
    }
    .width('100%')
    .height('75%')
    .backgroundBlurStyle(BlurStyle.Regular, { colorMode: ThemeColorMode.DARK })
    .borderRadius({ topLeft: 24, topRight: 24 })
    .bindSheet($$this.showFollowSheet, this.FollowSheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
  }

  @Builder
  StatBlock(title: string, value: string, tabIndex: number | null) {
    Column({ space: 3 }) {
      Text(value)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(DeepColors.TEXT_PRIMARY)

      Text(title)
        .fontSize(11)
        .fontColor(DeepColors.TEXT_TERTIARY)
    }
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      if (tabIndex !== null && this.userProfile) {
        this.followInitialTab = tabIndex;
        this.showFollowSheet = true;
      }
    })
  }

  @Builder
  FollowSheetBuilder() {
    FollowListSheet({
      targetUsername: this.userProfile?.username || '',
      initialTab: this.followInitialTab
    })
  }
}
