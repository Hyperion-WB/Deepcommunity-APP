import { DeepColors } from '../common/Constants';

/**
 * 社区极客活跃绿格子热力图组件 (HeatmapView ArkUI)
 * - 纯粹质感，全站活跃度 / 个人打卡天数可视化
 * - 仿 GitHub Commit 矩阵，分为 0~4 级活跃度深浅色彩
 */
@Component
export struct HeatmapView {
  @Prop totalActiveDays: number = 42;
  @Prop totalContributions: number = 128;

  // 12 周 x 7 天的示例活跃矩阵（每个值代表该天活跃等级 0~4）
  private weeks: number[][] = [
    [0, 1, 0, 2, 0, 0, 1],
    [1, 0, 3, 1, 2, 0, 0],
    [0, 2, 1, 4, 1, 1, 0],
    [1, 1, 0, 2, 3, 1, 1],
    [0, 3, 2, 1, 0, 2, 2],
    [2, 4, 1, 3, 2, 1, 0],
    [1, 2, 3, 2, 4, 0, 1],
    [0, 1, 2, 1, 1, 3, 2],
    [2, 3, 1, 4, 2, 1, 0],
    [1, 0, 2, 3, 1, 2, 3],
    [2, 2, 3, 1, 4, 2, 1],
    [3, 4, 2, 3, 2, 1, 2]
  ];

  getBoxColor(level: number): string {
    switch (level) {
      case 1:
        return '#0e4429';
      case 2:
        return '#006d32';
      case 3:
        return '#26a641';
      case 4:
        return '#39d353';
      default:
        return 'rgba(255, 255, 255, 0.05)';
    }
  }

  build() {
    Column() {
      // 头部数据条
      Row() {
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.calendar'))
            .fontSize(13)
            .fontColor([DeepColors.PRIMARY])

          Text('活跃度热力图')
            .fontSize(12)
            .fontWeight(FontWeight.Bold)
            .fontColor(DeepColors.TEXT_PRIMARY)
        }

        Blank()

        Text(`${this.totalActiveDays} 天活跃 · ${this.totalContributions} 次贡献`)
          .fontSize(11)
          .fontColor(DeepColors.TEXT_TERTIARY)
      }
      .width('100%')
      .margin({ bottom: 10 })

      // 绿格子矩阵横滑视口
      Scroll() {
        Row({ space: 4 }) {
          ForEach(this.weeks, (week: number[]) => {
            Column({ space: 4 }) {
              ForEach(week, (lvl: number) => {
                Rect()
                  .width(11)
                  .height(11)
                  .radius(2.5)
                  .fill(this.getBoxColor(lvl))
              })
            }
          })
        }
        .padding({ top: 2, bottom: 2 })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .width('100%')

      // 图例
      Row({ space: 4 }) {
        Text('少')
          .fontSize(9)
          .fontColor(DeepColors.TEXT_TERTIARY)

        Rect().width(8).height(8).radius(2).fill('rgba(255, 255, 255, 0.05)')
        Rect().width(8).height(8).radius(2).fill('#0e4429')
        Rect().width(8).height(8).radius(2).fill('#006d32')
        Rect().width(8).height(8).radius(2).fill('#26a641')
        Rect().width(8).height(8).radius(2).fill('#39d353')

        Text('多')
          .fontSize(9)
          .fontColor(DeepColors.TEXT_TERTIARY)
      }
      .width('100%')
      .justifyContent(FlexAlign.End)
      .margin({ top: 8 })
    }
    .width('100%')
    .padding(12)
    .borderRadius(12)
    .backgroundColor('rgba(28, 28, 30, 0.45)')
    .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
  }
}
