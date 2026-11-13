import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { Storage } from '../common/Storage';
import { UserProfile, CheckinStatus } from '../model/UserModels';
import { PostItem } from '../model/PostModels';
import { PostCard } from '../components/PostCard';
import { FollowListSheet } from '../components/FollowListSheet';
import { PrivacyDndSheet } from '../components/PrivacyDndSheet';
import { HeatmapView } from '../components/HeatmapView';
import promptAction from '@ohos.promptAction';

/**
 * 个人主页与中心设置 (ProfileScreen ArkUI)
 * - 纯粹质感，杜绝Emoji，无勋章墙
 * - 等级独立置于昵称与唯一ID之间，绝不挤压变形
 * - 签到逻辑：已签到显示静止翡翠微徽章（不展示打卡按键），未签到显示打卡按键
 * - 极客绿格子活跃热力图 (HeatmapView)
 * - 关注与粉丝列表抽屉 (FollowListSheet)
 * - 隐私与夜间免打扰设置 (PrivacyDndSheet)
 * - 鸿蒙官方沉浸光感渲染
 */
@Component
export struct ProfileScreen {
  @State currentUser: UserProfile | null = null;
  @State checkinStatus: CheckinStatus | null = null;
  @State isCheckingIn: boolean = false;
  @State selectedTab: number = 0; // 0: 我的动态, 1: 我的收藏
  @State myPosts: PostItem[] = [];
  @State myFavorites: PostItem[] = [];
  @State isLoadingPosts: boolean = false;
  @State isEditingBio: boolean = false;
  @State editNickname: string = '';
  @State editBio: string = '';

  // 抽屉状态
  @State showFollowSheet: boolean = false;
  @State followInitialTab: number = 0; // 0: 关注, 1: 粉丝
  @State showPrivacySheet: boolean = false;

  onLogout?: () => void;
  onPostClick?: (postId: string) => void;

  aboutToAppear() {
    this.loadUserData();
    this.loadCheckinStatus();
    this.loadPosts();
  }

  async loadUserData() {
    const cached = await Storage.getUser();
    if (cached) {
      this.currentUser = cached;
      this.editNickname = cached.nickname || cached.username || '';
      this.editBio = cached.bio || '';
    }
    const res = await HttpUtils.get<UserProfile>('api/users/me');
    if (res.isSuccess && res.data) {
      this.currentUser = res.data;
      this.editNickname = res.data.nickname || res.data.username || '';
      this.editBio = res.data.bio || '';
      await Storage.setUser(res.data);
    }
  }

  async loadCheckinStatus() {
    const res = await HttpUtils.get<CheckinStatus>('api/users/checkin/status');
    if (res.isSuccess && res.data) {
      this.checkinStatus = res.data;
    }
  }

  async loadPosts() {
    this.isLoadingPosts = true;
    if (this.selectedTab === 0) {
      const res = await HttpUtils.get<PostItem[]>('api/posts/mine?page=1&limit=20');
      if (res.isSuccess && res.data) {
        this.myPosts = res.data;
      }
    } else {
      const res = await HttpUtils.get<PostItem[]>('api/posts/favorites?page=1&limit=20');
      if (res.isSuccess && res.data) {
        this.myFavorites = res.data;
      }
    }
    this.isLoadingPosts = false;
  }

  async handleCheckin() {
    if (this.isCheckingIn || this.checkinStatus?.has_checked_in) return;
    this.isCheckingIn = true;
    const res = await HttpUtils.post<CheckinStatus>('api/users/checkin');
    this.isCheckingIn = false;
    if (res.isSuccess) {
      promptAction.showToast({ message: '签到成功，已获得社区积分奖励' });
      this.loadCheckinStatus();
      this.loadUserData();
    } else {
      promptAction.showToast({ message: res.errorMsg || '签到失败' });
    }
  }

  async handleSaveProfile() {
    const res = await HttpUtils.put<UserProfile>('api/users/me', {
      nickname: this.editNickname.trim(),
      bio: this.editBio.trim()
    });
    if (res.isSuccess && res.data) {
      this.currentUser = res.data;
      await Storage.setUser(res.data);
      this.isEditingBio = false;
      promptAction.showToast({ message: '个人资料已更新' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '更新失败' });
    }
  }

  async handleLogout() {
    await Storage.clearToken();
    promptAction.showToast({ message: '已安全退出登录' });
    if (this.onLogout) {
      this.onLogout();
    }
  }

  build() {
    Stack({ alignContent: Alignment.Top }) {
      List() {
        // 顶部空白安全留白与设置按键
        ListItem() {
          Row() {
            Blank()
            // 隐私与免打扰设置
            Button({ type: ButtonType.Circle }) {
              SymbolGlyph($r('sys.symbol.gearshape'))
                .fontSize(18)
                .fontColor([DeepColors.TEXT_SECONDARY])
            }
            .width(36)
            .height(36)
            .backgroundColor('rgba(28, 28, 30, 0.65)')
            .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
            .onClick(() => {
              this.showPrivacySheet = true;
            })
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 12, bottom: 4 })
        }

        // 个人主卡片 (沉浸光感)
        ListItem() {
          Column() {
            Row() {
              // 头像
              Image(this.currentUser?.avatarUrl || '')
                .width(64)
                .height(64)
                .borderRadius(32)
                .backgroundColor('rgba(255, 255, 255, 0.05)')
                .border({ width: 1.5, color: 'rgba(255, 255, 255, 0.12)' })
                .alt('')

              Column() {
                // 用户昵称
                Text(this.currentUser?.nickname || this.currentUser?.username || '社区居民')
                  .fontSize(17)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })

                Blank().height(4)

                // 等级独立置于用户名和唯一ID之间，绝不挤压变形
                Row() {
                  Text(`Lv.${this.currentUser?.level || 1}`)
                    .fontSize(10)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DeepColors.PRIMARY)
                }
                .padding({ left: 7, right: 7, top: 2, bottom: 2 })
                .borderRadius(6)
                .backgroundColor('rgba(59, 130, 246, 0.12)')
                .border({ width: 0.6, color: 'rgba(59, 130, 246, 0.3)', radius: 6 })

                Blank().height(4)

                // 唯一账号
                Text(`@${this.currentUser?.username || 'user'}`)
                  .fontSize(12)
                  .fontColor(DeepColors.TEXT_TERTIARY)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })

                Blank().height(4)

                // 编辑资料微触发键
                Row() {
                  Text('修改资料')
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                }
                .padding({ left: 6, right: 6, top: 2, bottom: 2 })
                .borderRadius(4)
                .backgroundColor('rgba(255, 255, 255, 0.04)')
                .onClick(() => {
                  this.isEditingBio = !this.isEditingBio;
                })
              }
              .alignItems(HorizontalAlign.Start)
              .margin({ left: 14 })
              .layoutWeight(1)

              // 每日签到区：已签到显示静止微徽章，未签到显示操作按钮
              if (this.checkinStatus?.has_checked_in) {
                Row() {
                  Text('已签到')
                    .fontSize(11)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DeepColors.ACCENT_SUCCESS)
                }
                .padding({ left: 10, right: 10, top: 6, bottom: 6 })
                .borderRadius(8)
                .backgroundColor('rgba(16, 185, 129, 0.12)')
                .border({ width: 0.8, color: 'rgba(16, 185, 129, 0.35)', radius: 8 })
              } else {
                Button(this.isCheckingIn ? '...' : '签到')
                  .fontSize(12)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(Color.White)
                  .height(30)
                  .padding({ left: 14, right: 14 })
                  .borderRadius(8)
                  .backgroundColor(DeepColors.PRIMARY)
                  .onClick(() => this.handleCheckin())
              }
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)

            // 个性签名
            Text(this.currentUser?.bio || '生活很安静，代码很专注。')
              .fontSize(13)
              .fontColor(DeepColors.TEXT_SECONDARY)
              .width('100%')
              .margin({ top: 12 })
              .lineHeight(19)

            // 关注数与粉丝数 (点击展开抽屉)
            Row({ space: 16 }) {
              Row({ space: 4 }) {
                Text(`${this.currentUser?.followingCount || 0}`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                Text('关注')
                  .fontSize(12)
                  .fontColor(DeepColors.TEXT_TERTIARY)
              }
              .onClick(() => {
                this.followInitialTab = 0;
                this.showFollowSheet = true;
              })

              Row({ space: 4 }) {
                Text(`${this.currentUser?.followersCount || 0}`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                Text('粉丝')
                  .fontSize(12)
                  .fontColor(DeepColors.TEXT_TERTIARY)
              }
              .onClick(() => {
                this.followInitialTab = 1;
                this.showFollowSheet = true;
              })
            }
            .width('100%')
            .margin({ top: 12 })

            // 签到战绩横条（连续打卡天数）
            if (this.checkinStatus) {
              Row() {
                Column() {
                  Text(`${this.checkinStatus.streak}`)
                    .fontSize(16)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DeepColors.PRIMARY)
                  Text('连续签到')
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                    .margin({ top: 2 })
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Center)

                Divider()
                  .vertical(true)
                  .height(24)
                  .color(DeepColors.BORDER)

                Column() {
                  Text(`${this.checkinStatus.bestStreak}`)
                    .fontSize(16)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DeepColors.TEXT_PRIMARY)
                  Text('历史最佳')
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                    .margin({ top: 2 })
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Center)

                Divider()
                  .vertical(true)
                  .height(24)
                  .color(DeepColors.BORDER)

                Column() {
                  Text(`${this.checkinStatus.makeupLeft}`)
                    .fontSize(16)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                  Text('剩余补签')
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                    .margin({ top: 2 })
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Center)
              }
              .width('100%')
              .padding({ top: 10, bottom: 10 })
              .margin({ top: 14 })
              .borderRadius(10)
              .backgroundColor('rgba(255, 255, 255, 0.03)')
              .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 10 })
            }

            // 编辑签名输入区（折叠）
            if (this.isEditingBio) {
              Column() {
                TextInput({ text: this.editNickname, placeholder: '修改昵称' })
                  .fontSize(13)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                  .placeholderColor(DeepColors.TEXT_TERTIARY)
                  .backgroundColor('rgba(28, 28, 30, 0.8)')
                  .borderRadius(8)
                  .height(38)
                  .margin({ bottom: 8 })
                  .onChange((val) => { this.editNickname = val; })

                TextArea({ text: this.editBio, placeholder: '修改签名' })
                  .fontSize(13)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                  .placeholderColor(DeepColors.TEXT_TERTIARY)
                  .backgroundColor('rgba(28, 28, 30, 0.8)')
                  .borderRadius(8)
                  .height(60)
                  .onChange((val) => { this.editBio = val; })

                Row() {
                  Button('取消')
                    .fontSize(12)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                    .backgroundColor(Color.Transparent)
                    .onClick(() => { this.isEditingBio = false; })

                  Button('保存更新')
                    .fontSize(12)
                    .fontColor(Color.White)
                    .backgroundColor(DeepColors.PRIMARY)
                    .margin({ left: 10 })
                    .onClick(() => this.handleSaveProfile())
                }
                .width('100%')
                .justifyContent(FlexAlign.End)
                .margin({ top: 8 })
              }
              .width('100%')
              .margin({ top: 12 })
              .padding(10)
              .borderRadius(10)
              .backgroundColor('rgba(20, 20, 22, 0.9)')
              .border({ width: 0.6, color: DeepColors.BORDER, radius: 10 })
            }
          }
          .width('100%')
          .padding(16)
          .borderRadius(16)
          .backgroundColor('rgba(28, 28, 30, 0.65)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)', radius: 16 })
        }
        .padding({ left: 16, right: 16, bottom: 12 })

        // 极客活跃度绿格子热力图 (Heatmap)
        ListItem() {
          HeatmapView({
            totalActiveDays: this.checkinStatus?.streak || 14,
            totalContributions: 68
          })
        }
        .padding({ left: 16, right: 16, bottom: 12 })

        // Tab 分类栏：我的动态 / 我的收藏 (无勋章墙)
        ListItem() {
          Row() {
            Row() {
              Text('我的动态')
                .fontSize(14)
                .fontWeight(this.selectedTab === 0 ? FontWeight.Bold : FontWeight.Normal)
                .fontColor(this.selectedTab === 0 ? Color.White : DeepColors.TEXT_SECONDARY)
            }
            .padding({ left: 18, right: 18, top: 7, bottom: 7 })
            .borderRadius(8)
            .backgroundColor(this.selectedTab === 0 ? DeepColors.PRIMARY : Color.Transparent)
            .onClick(() => {
              this.selectedTab = 0;
              this.loadPosts();
            })

            Row() {
              Text('我的收藏')
                .fontSize(14)
                .fontWeight(this.selectedTab === 1 ? FontWeight.Bold : FontWeight.Normal)
                .fontColor(this.selectedTab === 1 ? Color.White : DeepColors.TEXT_SECONDARY)
            }
            .padding({ left: 18, right: 18, top: 7, bottom: 7 })
            .borderRadius(8)
            .backgroundColor(this.selectedTab === 1 ? DeepColors.PRIMARY : Color.Transparent)
            .onClick(() => {
              this.selectedTab = 1;
              this.loadPosts();
            })
          }
          .width('100%')
          .padding(4)
          .borderRadius(10)
          .backgroundColor('rgba(28, 28, 30, 0.65)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)', radius: 10 })
        }
        .padding({ left: 16, right: 16, bottom: 12 })

        // 帖子内容列表
        if (this.selectedTab === 0) {
          if (this.myPosts.length === 0 && !this.isLoadingPosts) {
            ListItem() {
              Column() {
                Text('暂无发布的帖子')
                  .fontSize(13)
                  .fontColor(DeepColors.TEXT_TERTIARY)
              }
              .width('100%')
              .padding({ top: 40, bottom: 40 })
              .alignItems(HorizontalAlign.Center)
            }
          } else {
            ForEach(this.myPosts, (item: PostItem) => {
              ListItem() {
                PostCard({
                  post: item,
                  onPostClick: () => {
                    if (this.onPostClick) this.onPostClick(item.id);
                  }
                })
              }
              .padding({ left: 16, right: 16, bottom: 10 })
            }, (item: PostItem) => item.id)
          }
        } else {
          if (this.myFavorites.length === 0 && !this.isLoadingPosts) {
            ListItem() {
              Column() {
                Text('暂无收藏的内容')
                  .fontSize(13)
                  .fontColor(DeepColors.TEXT_TERTIARY)
              }
              .width('100%')
              .padding({ top: 40, bottom: 40 })
              .alignItems(HorizontalAlign.Center)
            }
          } else {
            ForEach(this.myFavorites, (item: PostItem) => {
              ListItem() {
                PostCard({
                  post: item,
                  onPostClick: () => {
                    if (this.onPostClick) this.onPostClick(item.id);
                  }
                })
              }
              .padding({ left: 16, right: 16, bottom: 10 })
            }, (item: PostItem) => item.id)
          }
        }

        // 底部退出登录操作条
        ListItem() {
          Row() {
            Button('退出当前账号')
              .fontSize(13)
              .fontColor(DeepColors.ACCENT_ERROR)
              .backgroundColor('rgba(239, 68, 68, 0.12)')
              .border({ width: 0.6, color: 'rgba(239, 68, 68, 0.3)', radius: 8 })
              .width('100%')
              .height(42)
              .onClick(() => this.handleLogout())
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 20, bottom: 90 })
        }
      }
      .width('100%')
      .height('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.BACKGROUND)
    // 关注/粉丝抽屉
    .bindSheet($$this.showFollowSheet, this.FollowSheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
    // 隐私设置抽屉
    .bindSheet($$this.showPrivacySheet, this.PrivacySheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
  }

  @Builder
  FollowSheetBuilder() {
    FollowListSheet({
      targetUsername: this.currentUser?.username || '',
      initialTab: this.followInitialTab
    })
  }

  @Builder
  PrivacySheetBuilder() {
    PrivacyDndSheet({
      onClose: () => {
        this.showPrivacySheet = false;
      }
    })
  }
}
