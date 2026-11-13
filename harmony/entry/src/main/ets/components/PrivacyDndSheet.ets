import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import promptAction from '@ohos.promptAction';

export interface BlockedUser {
  id: string;
  blocked_user_id: string;
  username: string;
  nickname?: string;
  avatar_url?: string;
  created_at: string;
}

/**
 * 隐私与夜间免打扰设置抽屉 (PrivacyDndSheet ArkUI)
 * - 夜间免打扰 (23:00 - 08:00 消息免振动与提醒)
 * - 隐身模式设置
 * - 社区黑名单管理 (拉黑解封)
 * - 官方沉浸光感
 */
@Component
export struct PrivacyDndSheet {
  @State isDndEnabled: boolean = false;
  @State isStealthMode: boolean = false;
  @State blockedUsers: BlockedUser[] = [];
  @State isLoadingBlocks: boolean = false;
  onClose: () => void = () => {};

  aboutToAppear() {
    this.loadSettings();
    this.loadBlockedUsers();
  }

  async loadSettings() {
    const res = await HttpUtils.get<{ dnd_enabled?: boolean; stealth_mode?: boolean }>('api/users/privacy');
    if (res.isSuccess && res.data) {
      this.isDndEnabled = !!res.data.dnd_enabled;
      this.isStealthMode = !!res.data.stealth_mode;
    }
  }

  async loadBlockedUsers() {
    this.isLoadingBlocks = true;
    const res = await HttpUtils.get<BlockedUser[]>('api/users/blocks');
    this.isLoadingBlocks = false;
    if (res.isSuccess && res.data) {
      this.blockedUsers = res.data;
    }
  }

  async updateDnd(enabled: boolean) {
    this.isDndEnabled = enabled;
    const res = await HttpUtils.put<{ success: boolean }>('api/users/dnd', {
      enabled: enabled,
      start_time: '23:00',
      end_time: '08:00'
    });
    if (res.isSuccess) {
      promptAction.showToast({ message: enabled ? '已开启夜间免打扰 (23:00-08:00)' : '已关闭夜间免打扰' });
    }
  }

  async updateStealth(enabled: boolean) {
    this.isStealthMode = enabled;
    const res = await HttpUtils.put<{ success: boolean }>('api/users/privacy', {
      stealth_mode: enabled
    });
    if (res.isSuccess) {
      promptAction.showToast({ message: enabled ? '已开启隐身访问模式' : '已关闭隐身访问模式' });
    }
  }

  async unblockUser(userId: string, username: string) {
    const res = await HttpUtils.delete<{ success: boolean }>(`api/users/blocks/${userId}`);
    if (res.isSuccess) {
      this.blockedUsers = this.blockedUsers.filter(u => u.blocked_user_id !== userId);
      promptAction.showToast({ message: `已将 @${username} 移出黑名单` });
    } else {
      promptAction.showToast({ message: res.errorMsg || '解封失败' });
    }
  }

  build() {
    Column() {
      // 顶部拖拽条
      Row()
        .width(36)
        .height(4)
        .borderRadius(2)
        .backgroundColor(DeepColors.BORDER)
        .margin({ top: 12, bottom: 12 })

      // 标题
      Text('隐私与通知防打扰')
        .fontSize(17)
        .fontWeight(FontWeight.Bold)
        .fontColor(DeepColors.TEXT_PRIMARY)
        .margin({ bottom: 16 })

      List({ space: 12 }) {
        // 1. 夜间免打扰设置
        ListItem() {
          Row() {
            Column({ space: 3 }) {
              Text('夜间免打扰')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor(DeepColors.TEXT_PRIMARY)

              Text('每日 23:00 至次日 08:00 静音私聊与群聊提醒')
                .fontSize(11)
                .fontColor(DeepColors.TEXT_TERTIARY)
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)

            Toggle({ type: ToggleType.Switch, isOn: this.isDndEnabled })
              .selectedColor(DeepColors.PRIMARY)
              .onChange((isOn: boolean) => {
                this.updateDnd(isOn);
              })
          }
          .width('100%')
          .padding(14)
          .borderRadius(12)
          .backgroundColor('rgba(28, 28, 30, 0.55)')
          .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
        }

        // 2. 隐身模式设置
        ListItem() {
          Row() {
            Column({ space: 3 }) {
              Text('隐身访问模式')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor(DeepColors.TEXT_PRIMARY)

              Text('访问他人主页与动态时不留下访问痕迹')
                .fontSize(11)
                .fontColor(DeepColors.TEXT_TERTIARY)
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)

            Toggle({ type: ToggleType.Switch, isOn: this.isStealthMode })
              .selectedColor(DeepColors.PRIMARY)
              .onChange((isOn: boolean) => {
                this.updateStealth(isOn);
              })
          }
          .width('100%')
          .padding(14)
          .borderRadius(12)
          .backgroundColor('rgba(28, 28, 30, 0.55)')
          .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
        }

        // 3. 黑名单管理
        ListItem() {
          Column({ space: 10 }) {
            Row() {
              Text('黑名单管理')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor(DeepColors.TEXT_PRIMARY)

              Blank()

              Text(`${this.blockedUsers.length} 人`)
                .fontSize(12)
                .fontColor(DeepColors.TEXT_TERTIARY)
            }
            .width('100%')

            if (this.blockedUsers.length === 0) {
              Text('黑名单为空，社区环境健康')
                .fontSize(12)
                .fontColor(DeepColors.TEXT_TERTIARY)
                .padding({ top: 8, bottom: 8 })
            } else {
              ForEach(this.blockedUsers, (b: BlockedUser) => {
                Row() {
                  Text(`@${b.username}`)
                    .fontSize(13)
                    .fontColor(DeepColors.TEXT_PRIMARY)
                    .layoutWeight(1)

                  Button('解除屏蔽')
                    .fontSize(11)
                    .fontColor(DeepColors.PRIMARY)
                    .backgroundColor('rgba(59, 130, 246, 0.12)')
                    .height(26)
                    .padding({ left: 10, right: 10 })
                    .borderRadius(6)
                    .onClick(() => this.unblockUser(b.blocked_user_id, b.username))
                }
                .width('100%')
                .padding({ top: 6, bottom: 6 })
              }, (b: BlockedUser) => b.id)
            }
          }
          .width('100%')
          .padding(14)
          .borderRadius(12)
          .backgroundColor('rgba(28, 28, 30, 0.55)')
          .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)', radius: 12 })
        }
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, bottom: 20 })
    }
    .width('100%')
    .height('100%')
    .backgroundBlurStyle(BlurStyle.Regular, { colorMode: ThemeColorMode.DARK })
  }
}
