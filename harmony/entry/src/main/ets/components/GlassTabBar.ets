import { DeepColors } from '../common/Constants';

/**
 * 鸿蒙沉浸光感液态玻璃悬浮导航栏 (HarmonyOS Fluid Glass Navigation Bar)
 * 严格 5 项对称排布，中间为高亮创建按键，支持官方 backgroundBlurStyle 沉浸光感渲染与 Badge 徽标未读统计
 */
@Component
export struct GlassTabBar {
  @Prop currentTab: number = 0;
  @Prop unreadMessageCount: number = 0;
  @Prop unreadNotificationCount: number = 0;
  onTabSelected: (tab: number) => void = () => {};
  onCreateClick: () => void = () => {};

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Row() {
        // 1. 首页
        this.NavItem(0, '首页', $r('sys.symbol.house'), this.currentTab === 0, 0)

        // 2. 聊天会话
        this.NavItem(1, '会话', $r('sys.symbol.message'), this.currentTab === 1, this.unreadMessageCount)

        // 3. 【居中】悬浮加号发帖键
        Button({ type: ButtonType.Circle }) {
          Text('+')
            .fontSize(28)
            .fontWeight(FontWeight.Light)
            .fontColor(Color.White)
        }
        .width(48)
        .height(48)
        .backgroundColor(DeepColors.PRIMARY)
        .shadow({ radius: 16, color: 'rgba(59, 130, 246, 0.4)', offsetY: 4 })
        .onClick(() => {
          this.onCreateClick();
        })

        // 4. 通知提醒
        this.NavItem(3, '提醒', $r('sys.symbol.bell'), this.currentTab === 3, this.unreadNotificationCount)

        // 5. 个人主页
        this.NavItem(4, '我的', $r('sys.symbol.person'), this.currentTab === 4, 0)
      }
      .width('92%')
      .height(64)
      .margin({ bottom: 20 })
      .padding({ left: 16, right: 16 })
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)
      .borderRadius(36)
      // 鸿蒙原生沉浸光感模糊样式 API: backgroundBlurStyle
      .backgroundBlurStyle(BlurStyle.Thin, {
        colorMode: ThemeColorMode.DARK,
        adaptiveColor: AdaptiveColor.DEFAULT,
        scale: 0.95
      })
      .backgroundColor('rgba(28, 28, 30, 0.72)')
      .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.12)' })
      .shadow({ radius: 24, color: 'rgba(0, 0, 0, 0.45)', offsetY: 8 })
    }
    .width('100%')
  }

  @Builder
  NavItem(index: number, label: string, iconSymbol: Resource, isSelected: boolean, badgeCount: number) {
    Column() {
      if (badgeCount > 0) {
        Badge({
          count: badgeCount,
          maxCount: 99,
          position: BadgePosition.RightTop,
          style: {
            color: Color.White,
            fontSize: 9,
            badgeSize: 16,
            badgeColor: DeepColors.ACCENT_ERROR
          }
        }) {
          this.NavIconContent(iconSymbol, label, isSelected)
        }
      } else {
        this.NavIconContent(iconSymbol, label, isSelected)
      }
    }
    .width(48)
    .height(48)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .borderRadius(24)
    .onClick(() => {
      this.onTabSelected(index);
    })
  }

  @Builder
  NavIconContent(iconSymbol: Resource, label: string, isSelected: boolean) {
    Column({ space: 2 }) {
      SymbolGlyph(iconSymbol)
        .fontSize(22)
        .fontColor([isSelected ? DeepColors.PRIMARY : DeepColors.TEXT_TERTIARY])

      Text(label)
        .fontSize(10)
        .fontWeight(isSelected ? FontWeight.Bold : FontWeight.Normal)
        .fontColor(isSelected ? DeepColors.PRIMARY : DeepColors.TEXT_TERTIARY)
    }
  }
}
