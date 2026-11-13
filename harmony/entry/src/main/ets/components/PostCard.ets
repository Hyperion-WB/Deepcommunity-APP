import { PostItem } from '../model/PostModels';
import { Constants, DeepColors } from '../common/Constants';

/**
 * 鸿蒙原生质感帖子卡片组件 (PostCard ArkUI)
 * 具备黑白极简工业级设计、沉浸式卡片微边框、九宫格相册与点赞弹性反馈
 */
@Component
export struct PostCard {
  @Prop post: PostItem;
  @State likeCount: number = 0;
  @State isLiked: boolean = false;
  @State heartScale: number = 1.0;
  onPostClick: (id: string) => void = () => {};
  onLikeClick: (id: string) => void = () => {};
  onAuthorClick: (username: string, authorId: string) => void = () => {};
  onImageClick: (url: string) => void = () => {};

  aboutToAppear() {
    this.likeCount = this.post.like_count;
    this.isLiked = this.post.is_liked;
  }

  private cleanContent(raw?: string): string {
    if (!raw) return '';
    return raw.replace(/<[^>]*>/g, '').trim().slice(0, 120);
  }

  build() {
    Column({ space: 10 }) {
      // 1. 作者信息行
      Row() {
        Row({ space: 10 }) {
          Image(Constants.formatImageUrl(this.post.author_avatar || this.post.author?.avatarUrl))
            .width(38)
            .height(38)
            .borderRadius(19)
            .alt($r('sys.media.ohos_ic_public_user_head'))
            .objectFit(ImageFit.Cover)

          Column({ space: 3 }) {
            Row({ space: 6 }) {
              Text(this.post.author_nickname || this.post.author_username || '居民')
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor(DeepColors.TEXT_PRIMARY)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })

              // 等级微徽章
              Text(`Lv.${this.post.author_level || 1}`)
                .fontSize(9)
                .fontWeight(FontWeight.Bold)
                .fontColor(DeepColors.PRIMARY)
                .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                .borderRadius(4)
                .backgroundColor('rgba(59, 130, 246, 0.12)')
            }
            .alignItems(VerticalAlign.Center)

            Text((this.post.created_at || '').slice(0, 10))
              .fontSize(11)
              .fontColor(DeepColors.TEXT_TERTIARY)
          }
          .alignItems(HorizontalAlign.Start)
        }
        .onClick(() => {
          const uName = this.post.author_username || '';
          const uId = this.post.author_id || '';
          this.onAuthorClick(uName, uId);
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.Start)

      // 2. 置顶 / 精选 / 板块分类标签
      if (this.post.is_pinned || this.post.is_essence || this.post.category_name) {
        Row({ space: 6 }) {
          if (this.post.is_pinned) {
            Text('置顶')
              .fontSize(10)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.PRIMARY)
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .borderRadius(4)
              .backgroundColor('rgba(59, 130, 246, 0.12)')
          }
          if (this.post.is_essence) {
            Text('精选')
              .fontSize(10)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.ACCENT_GOLD)
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .borderRadius(4)
              .backgroundColor('rgba(245, 158, 11, 0.16)')
          }
          if (this.post.category_name) {
            Text(this.post.category_name)
              .fontSize(10)
              .fontColor(DeepColors.TEXT_SECONDARY)
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .borderRadius(4)
              .backgroundColor(DeepColors.SURFACE_ELEVATED)
              .border({ width: 0.6, color: DeepColors.BORDER })
          }
        }
      }

      // 3. 帖子标题
      Text(this.post.title)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(DeepColors.TEXT_PRIMARY)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')

      // 4. 正文纯净摘要
      if (this.post.content_md || this.post.content_html) {
        Text(this.cleanContent(this.post.content_md || this.post.content_html))
          .fontSize(13)
          .fontColor(DeepColors.TEXT_SECONDARY)
          .lineHeight(19)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
      }

      // 5. 图像网格 / 单图自适应展示
      this.RenderImageGallery()

      // 6. 底部交互栏 (点赞、评论、浏览)
      Row() {
        // 点赞交互
        Row({ space: 4 }) {
          SymbolGlyph(this.isLiked ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'))
            .fontSize(18)
            .fontColor([this.isLiked ? '#EF4444' : DeepColors.TEXT_TERTIARY])
            .scale({ x: this.heartScale, y: this.heartScale })

          Text(`${this.likeCount}`)
            .fontSize(12)
            .fontColor(this.isLiked ? '#EF4444' : DeepColors.TEXT_TERTIARY)
        }
        .onClick(() => {
          this.toggleLike();
        })

        // 评论数
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.message'))
            .fontSize(18)
            .fontColor([DeepColors.TEXT_TERTIARY])

          Text(`${this.post.comment_count}`)
            .fontSize(12)
            .fontColor(DeepColors.TEXT_TERTIARY)
        }

        // 浏览量
        Row({ space: 4 }) {
          SymbolGlyph($r('sys.symbol.eye'))
            .fontSize(18)
            .fontColor([DeepColors.TEXT_TERTIARY])

          Text(`${this.post.view_count}`)
            .fontSize(12)
            .fontColor(DeepColors.TEXT_TERTIARY)
        }
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      .margin({ top: 4 })
    }
    .width('100%')
    .padding(16)
    .borderRadius(16)
    .backgroundColor(DeepColors.SURFACE)
    .border({ width: 0.8, color: DeepColors.BORDER })
    .shadow({ radius: 10, color: 'rgba(0, 0, 0, 0.15)', offsetY: 4 })
    .onClick(() => {
      this.onPostClick(this.post.id);
    })
  }

  @Builder
  RenderImageGallery() {
    if (this.post.image_urls && this.post.image_urls.length > 0) {
      Grid() {
        ForEach(this.post.image_urls.slice(0, 9), (img: string) => {
          GridItem() {
            Image(Constants.formatImageUrl(img))
              .width('100%')
              .aspectRatio(1)
              .borderRadius(8)
              .objectFit(ImageFit.Cover)
              .backgroundColor(DeepColors.SURFACE_ELEVATED)
              .onClick(() => {
                this.onImageClick(Constants.formatImageUrl(img));
              })
          }
        })
      }
      .columnsTemplate(this.post.image_urls.length === 1 ? '1fr' : (this.post.image_urls.length <= 4 ? '1fr 1fr' : '1fr 1fr 1fr'))
      .columnsGap(6)
      .rowsGap(6)
      .width('100%')
    } else if (this.post.cover_image || this.post.display_image) {
      Image(Constants.formatImageUrl(this.post.cover_image || this.post.display_image))
        .width('100%')
        .height(160)
        .borderRadius(10)
        .objectFit(ImageFit.Cover)
        .onClick(() => {
          this.onImageClick(Constants.formatImageUrl(this.post.cover_image || this.post.display_image));
        })
    }
  }

  private toggleLike() {
    this.isLiked = !this.isLiked;
    this.likeCount += this.isLiked ? 1 : -1;
    animateTo({ duration: 150, curve: Curve.FastOutSlowIn }, () => {
      this.heartScale = 1.35;
    });
    setTimeout(() => {
      animateTo({ duration: 150, curve: Curve.EaseOut }, () => {
        this.heartScale = 1.0;
      });
    }, 150);
    this.onLikeClick(this.post.id);
  }
}
