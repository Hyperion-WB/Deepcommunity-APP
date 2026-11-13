import { LeaderboardItem } from '../model/PostModels';
import { Constants, DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { HeatmapView } from './HeatmapView';

/**
 * 鸿蒙全站活跃榜与签到热力图抽屉 (LeaderboardSheet ArkUI)
 * 金属微徽章排印 (金/银/铜)，支持连续签到、早鸟与活跃排行榜，以及社区热力图
 */
@Component
export struct LeaderboardSheet {
  @State selectedTab: number = 0;
  @State streakList: LeaderboardItem[] = [];
  @State earlyList: LeaderboardItem[] = [];
  @State activeList: LeaderboardItem[] = [];
  @State isLoading: boolean = true;
  onUserClick: (username: string, userId: string) => void = () => {};

  aboutToAppear() {
    this.loadLeaderboards();
  }

  async loadLeaderboards() {
    this.isLoading = true;
    try {
      const res = await HttpUtils.get<Record<string, LeaderboardItem[]>>('api/posts/leaderboards');
      if (res.isSuccess && res.data) {
        this.streakList = res.data.streak || [];
        this.earlyList = res.data.early || [];
        this.activeList = res.data.active || [];
      }
    } catch (e) {
      console.error('Failed to load leaderboards: ' + JSON.stringify(e));
    } finally {
      this.isLoading = false;
    }
  }

  build() {
    Column() {
      // 顶部把手
      Row()
        .width(36)
        .height(4)
        .borderRadius(2)
        .backgroundColor(DeepColors.BORDER)
        .margin({ top: 12, bottom: 12 })

      // 标题
      Text('全站排行榜与热力图')
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(DeepColors.TEXT_PRIMARY)
        .margin({ bottom: 12 })

      // 极客活跃绿格子热力图
      HeatmapView({
        totalActiveDays: 86,
        totalContributions: 312
      })
      .margin({ left: 16, right: 16, bottom: 14 })

      // 榜单 Tab 药丸切换
      Row({ space: 8 }) {
        this.TabPill('连续签到', 0)
        this.TabPill('抢早排行', 1)
        this.TabPill('7日活跃', 2)
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .margin({ bottom: 14 })

      if (this.isLoading) {
        LoadingProgress()
          .width(32)
          .height(32)
          .color(DeepColors.PRIMARY)
          .margin({ top: 40, bottom: 40 })
      } else {
        List({ space: 10 }) {
          ForEach(this.getCurrentList(), (item: LeaderboardItem, index: number) => {
            ListItem() {
              Row() {
                // 金属徽章 / 序号
                this.RenderRankBadge(index + 1)

                // 用户头像
                Image(Constants.formatImageUrl(item.avatar_url))
                  .width(40)
                  .height(40)
                  .borderRadius(20)
                  .margin({ left: 12, right: 10 })
                  .alt($r('sys.media.ohos_ic_public_user_head'))

                // 用户名与等级
                Column({ space: 2 }) {
                  Row({ space: 6 }) {
                    Text(item.nickname || item.username)
                      .fontSize(14)
                      .fontWeight(FontWeight.SemiBold)
                      .fontColor(DeepColors.TEXT_PRIMARY)

                    Text(`Lv.${item.level}`)
                      .fontSize(9)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DeepColors.PRIMARY)
                      .padding({ left: 4, right: 4, top: 1, bottom: 1 })
                      .borderRadius(4)
                      .backgroundColor('rgba(59, 130, 246, 0.12)')
                  }
                  Text(`@${item.username}`)
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Start)

                // 战绩值
                Text(`${item.value} ${this.selectedTab === 0 ? '天' : (this.selectedTab === 1 ? '点' : '活跃')}`)
                  .fontSize(13)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.PRIMARY)
              }
              .width('100%')
              .padding({ left: 14, right: 14, top: 10, bottom: 10 })
              .borderRadius(14)
              .backgroundColor('rgba(28, 28, 30, 0.55)')
              .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 14 })
              .onClick(() => {
                this.onUserClick(item.username, item.user_id);
              })
            }
          })
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: 16, right: 16, bottom: 20 })
      }
    }
    .width('100%')
    .height('85%')
    .backgroundBlurStyle(BlurStyle.Regular, { colorMode: ThemeColorMode.DARK })
    .borderRadius({ topLeft: 24, topRight: 24 })
  }

  @Builder
  TabPill(label: string, index: number) {
    Text(label)
      .fontSize(12)
      .fontWeight(this.selectedTab === index ? FontWeight.Bold : FontWeight.Normal)
      .fontColor(this.selectedTab === index ? Color.White : DeepColors.TEXT_TERTIARY)
      .padding({ left: 14, right: 14, top: 6, bottom: 6 })
      .borderRadius(16)
      .backgroundColor(this.selectedTab === index ? DeepColors.PRIMARY : DeepColors.SURFACE_ELEVATED)
      .onClick(() => {
        this.selectedTab = index;
      })
  }

  @Builder
  RenderRankBadge(rank: number) {
    if (rank === 1) {
      Row() {
        Text('1').fontSize(11).fontWeight(FontWeight.Bold).fontColor('#78350F')
      }
      .width(24).height(24).borderRadius(12)
      .backgroundColor('#FBBF24').justifyContent(FlexAlign.Center)
    } else if (rank === 2) {
      Row() {
        Text('2').fontSize(11).fontWeight(FontWeight.Bold).fontColor('#1E293B')
      }
      .width(24).height(24).borderRadius(12)
      .backgroundColor('#E2E8F0').justifyContent(FlexAlign.Center)
    } else if (rank === 3) {
      Row() {
        Text('3').fontSize(11).fontWeight(FontWeight.Bold).fontColor('#451A03')
      }
      .width(24).height(24).borderRadius(12)
      .backgroundColor('#FDBA74').justifyContent(FlexAlign.Center)
    } else {
      Text(`${rank}`)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor(DeepColors.TEXT_TERTIARY)
        .width(24)
        .textAlign(TextAlign.Center)
    }
  }

  private getCurrentList(): LeaderboardItem[] {
    if (this.selectedTab === 0) return this.streakList;
    if (this.selectedTab === 1) return this.earlyList;
    return this.activeList;
  }
}
