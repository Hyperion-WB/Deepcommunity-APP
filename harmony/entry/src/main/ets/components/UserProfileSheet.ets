import { UserProfile } from '../model/UserModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import promptAction from '@ohos.promptAction';

/**
 * 鸿蒙他人个人主页半屏弹窗 (UserProfileSheet ArkUI)
 * 严格按照规范：等级 (Lv.X) 独立置于「用户昵称」与「账号唯一ID」之间，避免挤压变形
 */
@Component
export struct UserProfileSheet {
  @Prop username: string = '';
  @Prop userId: string = '';
  @State userProfile: UserProfile | null = null;
  @State isLoading: boolean = true;
  @State isFollowing: boolean = false;
  @State isFollowLoading: boolean = false;
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
      this.userProfile = res;
      this.isFollowing = !!res.isFollowing;
    } catch (e) {
      console.error('Failed to load profile: ' + JSON.stringify(e));
    } finally {
      this.isLoading = false;
    }
  }

  async toggleFollow() {
    if (this.isFollowLoading) return;
    this.isFollowLoading = true;
    try {
      const identifier = this.username || this.userId;
      if (this.isFollowing) {
        await HttpUtils.post(`api/users/${identifier}/unfollow`);
        this.isFollowing = false;
        promptAction.showToast({ message: '已取消关注' });
      } else {
        await HttpUtils.post(`api/users/${identifier}/follow`);
        this.isFollowing = true;
        promptAction.showToast({ message: '关注成功' });
      }
    } catch (err) {
      promptAction.showToast({ message: err.message || '操作失败' });
    } finally {
      this.isFollowLoading = false;
    }
  }

  build() {
    Column() {
      // 顶部手势拖拽条
      Row()
        .width(36)
        .height(4)
        .borderRadius(2)
        .backgroundColor(DeepColors.BORDER)
        .margin({ top: 12, bottom: 16 })

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
          .alt($r('sys.media.ohos_ic_public_user_head'))

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
        Text(this.userProfile.bio || '这个人很神秘，还没有填写个人简介～')
          .fontSize(13)
          .fontColor(DeepColors.TEXT_SECONDARY)
          .textAlign(TextAlign.Center)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .padding({ left: 24, right: 24 })
          .margin({ top: 12 })

        // 统计数据方块
        Row() {
          this.StatBlock('动态', `${this.userProfile.postCount || 0}`)
          Divider().vertical(true).height(20).color(DeepColors.BORDER)
          this.StatBlock('关注', `${this.userProfile.followingCount || 0}`)
          Divider().vertical(true).height(20).color(DeepColors.BORDER)
          this.StatBlock('粉丝', `${this.userProfile.followersCount || 0}`)
        }
        .width('90%')
        .padding({ top: 14, bottom: 14 })
        .borderRadius(14)
        .backgroundColor(DeepColors.SURFACE_ELEVATED)
        .border({ width: 0.8, color: DeepColors.BORDER })
        .justifyContent(FlexAlign.SpaceEvenly)
        .margin({ top: 18 })

        // 底部操作按钮
        Row({ space: 12 }) {
          Button(this.isFollowing ? '已关注' : '+ 关注')
            .layoutWeight(1)
            .height(42)
            .borderRadius(12)
            .backgroundColor(this.isFollowing ? DeepColors.SURFACE_ELEVATED : DeepColors.PRIMARY)
            .fontColor(this.isFollowing ? DeepColors.TEXT_SECONDARY : Color.White)
            .border({ width: this.isFollowing ? 0.8 : 0, color: DeepColors.BORDER })
            .onClick(() => {
              this.toggleFollow();
            })

          Button('发私信')
            .layoutWeight(1)
            .height(42)
            .borderRadius(12)
            .backgroundColor(DeepColors.SURFACE_ELEVATED)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .border({ width: 0.8, color: DeepColors.BORDER })
            .onClick(() => {
              this.onClose();
              if (this.userProfile) {
                this.onNavigateToChat(this.userProfile.id);
              }
            })
        }
        .width('90%')
        .margin({ top: 22, bottom: 20 })
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, bottom: 24 })
    .backgroundColor(DeepColors.SURFACE)
    .borderRadius({ topLeft: 24, topRight: 24 })
  }

  @Builder
  StatBlock(title: string, value: string) {
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
  }
}
