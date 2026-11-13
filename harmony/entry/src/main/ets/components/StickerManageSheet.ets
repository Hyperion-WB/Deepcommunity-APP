import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import promptAction from '@ohos.promptAction';

export interface StickerItem {
  id: string;
  url: string;
  title?: string;
  category?: string;
}

/**
 * 表情包管理抽屉 (StickerManageSheet ArkUI)
 * - 表情包管理、添加与删除
 * - 沉浸光感毛玻璃
 */
@Component
export struct StickerManageSheet {
  @State stickers: StickerItem[] = [];
  @State isLoading: boolean = true;
  @State newStickerUrl: string = '';
  @State isAdding: boolean = false;
  onSelectSticker: (url: string) => void = () => {};

  aboutToAppear() {
    this.loadStickers();
  }

  async loadStickers() {
    this.isLoading = true;
    const res = await HttpUtils.get<StickerItem[]>('api/chat/stickers');
    this.isLoading = false;
    if (res.isSuccess && res.data) {
      this.stickers = res.data;
    }
  }

  async handleAddSticker() {
    if (!this.newStickerUrl.trim()) {
      promptAction.showToast({ message: '请输入表情包图片链接' });
      return;
    }
    const res = await HttpUtils.post<StickerItem>('api/chat/stickers', {
      url: this.newStickerUrl.trim()
    });
    if (res.isSuccess && res.data) {
      this.stickers.push(res.data);
      this.newStickerUrl = '';
      this.isAdding = false;
      promptAction.showToast({ message: '表情包添加成功' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '添加失败' });
    }
  }

  async handleDeleteSticker(id: string) {
    const res = await HttpUtils.delete<{ success: boolean }>(`api/chat/stickers/${id}`);
    if (res.isSuccess) {
      this.stickers = this.stickers.filter(s => s.id !== id);
      promptAction.showToast({ message: '已删除该表情包' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '删除失败' });
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

      // 标题与添加按键
      Row() {
        Text('表情包管理')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(DeepColors.TEXT_PRIMARY)

        Blank()

        Button(this.isAdding ? '取消' : '+ 添加表情')
          .fontSize(12)
          .fontColor(DeepColors.PRIMARY)
          .backgroundColor('rgba(59, 130, 246, 0.12)')
          .height(28)
          .padding({ left: 10, right: 10 })
          .borderRadius(6)
          .onClick(() => {
            this.isAdding = !this.isAdding;
          })
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 12 })

      // 添加输入框
      if (this.isAdding) {
        Row({ space: 8 }) {
          TextInput({ placeholder: '输入图片直链 (http/https)...', text: this.newStickerUrl })
            .layoutWeight(1)
            .height(36)
            .fontSize(12)
            .backgroundColor('rgba(28, 28, 30, 0.65)')
            .fontColor(DeepColors.TEXT_PRIMARY)
            .placeholderColor(DeepColors.TEXT_TERTIARY)
            .borderRadius(8)
            .onChange((val) => {
              this.newStickerUrl = val;
            })

          Button('确定')
            .fontSize(12)
            .fontColor(Color.White)
            .backgroundColor(DeepColors.PRIMARY)
            .height(36)
            .padding({ left: 14, right: 14 })
            .borderRadius(8)
            .onClick(() => this.handleAddSticker())
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 12 })
      }

      // 表情包网格
      if (this.isLoading) {
        LoadingProgress()
          .width(32)
          .height(32)
          .color(DeepColors.PRIMARY)
          .margin({ top: 40, bottom: 40 })
      } else if (this.stickers.length === 0) {
        Text('暂无表情包，可点击右上角添加')
          .fontSize(12)
          .fontColor(DeepColors.TEXT_TERTIARY)
          .padding({ top: 40, bottom: 40 })
      } else {
        Grid() {
          ForEach(this.stickers, (st: StickerItem) => {
            GridItem() {
              Stack({ alignContent: Alignment.TopEnd }) {
                Image(st.url)
                  .width('100%')
                  .height('100%')
                  .borderRadius(8)
                  .objectFit(ImageFit.Contain)
                  .backgroundColor('rgba(255, 255, 255, 0.04)')
                  .onClick(() => {
                    this.onSelectSticker(st.url);
                  })

                // 删除微按键
                Button({ type: ButtonType.Circle }) {
                  Text('×')
                    .fontSize(12)
                    .fontColor(Color.White)
                }
                .width(18)
                .height(18)
                .backgroundColor('rgba(0, 0, 0, 0.6)')
                .margin({ top: 4, right: 4 })
                .onClick(() => this.handleDeleteSticker(st.id))
              }
              .width(68)
              .height(68)
            }
          }, (st: StickerItem) => st.id)
        }
        .columnsTemplate('1fr 1fr 1fr 1fr')
        .rowsGap(10)
        .columnsGap(10)
        .width('100%')
        .layoutWeight(1)
        .padding({ left: 16, right: 16, bottom: 16 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundBlurStyle(BlurStyle.Regular, { colorMode: ThemeColorMode.DARK })
  }
}
