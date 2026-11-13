import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { PostItem, CommentItem } from '../model/PostModels';
import { UserProfileSheet } from '../components/UserProfileSheet';
import { StickerManageSheet } from '../components/StickerManageSheet';

/**
 * 鸿蒙原生帖子详情与交互页面 (PostDetailScreen)
 * - 沉浸式磨砂顶栏与平滑回退
 * - 作者信息与独立防变形等级徽章
 * - 正文与多图展示
 * - 点赞、收藏、帖子/评论举报违规通道 (Report)
 * - 评论互动流：问答采纳 (Accept)、评论置顶 (Feature)、发表评论、表情包
 * - 官方沉浸光感渲染 (backgroundBlurStyle)
 */
@Entry
@Component
export struct PostDetailScreen {
  @State postId: string = '';
  @State post: PostItem | null = null;
  @State comments: CommentItem[] = [];
  @State isLoading: boolean = true;
  @State isSubmittingComment: boolean = false;
  @State commentInput: string = '';
  @State showUserSheet: boolean = false;
  @State selectedUserId: string = '';
  @State showReportDialog: boolean = false;
  @State reportTargetType: string = 'post'; // 'post' | 'comment'
  @State reportTargetId: string = '';
  @State reportReason: string = '';
  @State showStickerSheet: boolean = false;

  aboutToAppear() {
    const params = router.getParams() as Record<string, Object>;
    if (params && params['postId']) {
      this.postId = params['postId'] as string;
      this.loadPostDetail();
      this.loadComments();
    }
  }

  async loadPostDetail() {
    this.isLoading = true;
    const res = await HttpUtils.get<PostItem>(`api/posts/${this.postId}`);
    this.isLoading = false;
    if (res.isSuccess && res.data) {
      this.post = res.data;
    } else {
      promptAction.showToast({ message: res.errorMsg || '加载帖子失败' });
    }
  }

  async loadComments() {
    const res = await HttpUtils.get<CommentItem[]>(`api/posts/${this.postId}/comments`);
    if (res.isSuccess && res.data) {
      this.comments = res.data;
    }
  }

  async handleToggleLike() {
    if (!this.post) return;
    const prevLiked = this.post.is_liked;
    const prevCount = this.post.like_count;
    this.post.is_liked = !prevLiked;
    this.post.like_count = prevLiked ? Math.max(0, prevCount - 1) : prevCount + 1;

    const res = await HttpUtils.post<{ liked: boolean; count: number }>(`api/posts/${this.postId}/like`);
    if (res.isSuccess && res.data) {
      this.post.is_liked = res.data.liked;
      this.post.like_count = res.data.count;
    }
  }

  async handleToggleFavorite() {
    const res = await HttpUtils.post<{ favorited: boolean }>(`api/posts/${this.postId}/favorite`);
    if (res.isSuccess) {
      promptAction.showToast({ message: res.data?.favorited ? '已加入个人收藏' : '已取消收藏' });
    }
  }

  async handleSendComment() {
    if (!this.commentInput.trim() || this.isSubmittingComment) return;
    this.isSubmittingComment = true;
    const res = await HttpUtils.post<CommentItem>(`api/posts/${this.postId}/comments`, {
      content: this.commentInput.trim()
    });
    this.isSubmittingComment = false;
    if (res.isSuccess && res.data) {
      this.comments.push(res.data);
      this.commentInput = '';
      if (this.post) this.post.comment_count += 1;
      promptAction.showToast({ message: '评论发表成功' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '评论发送失败' });
    }
  }

  async handleAcceptComment(comment: CommentItem) {
    const res = await HttpUtils.post<{ success: boolean }>(`api/posts/${this.postId}/accept/${comment.id}`);
    if (res.isSuccess) {
      comment.is_accepted = true;
      promptAction.showToast({ message: '已采纳该回答为最佳答案' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '采纳失败' });
    }
  }

  async handleFeatureComment(comment: CommentItem) {
    const res = await HttpUtils.post<{ success: boolean }>(`api/posts/${this.postId}/comments/${comment.id}/feature`);
    if (res.isSuccess) {
      comment.is_featured = !comment.is_featured;
      promptAction.showToast({ message: comment.is_featured ? '已将该评论置顶' : '已取消置顶' });
    }
  }

  openReport(targetType: string, targetId: string) {
    this.reportTargetType = targetType;
    this.reportTargetId = targetId;
    this.reportReason = '';
    this.showReportDialog = true;
  }

  async handleSubmitReport() {
    if (!this.reportReason.trim()) {
      promptAction.showToast({ message: '请填写举报原因' });
      return;
    }
    const res = await HttpUtils.post<{ success: boolean }>('api/reports', {
      target_type: this.reportTargetType,
      target_id: this.reportTargetId,
      reason: this.reportReason.trim()
    });
    this.showReportDialog = false;
    this.reportReason = '';
    if (res.isSuccess) {
      promptAction.showToast({ message: '举报已提交，社区管理员将在24小时内处理' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '举报提交失败' });
    }
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Column() {
        // 顶部沉浸导航栏 (backgroundBlurStyle)
        Row() {
          Button({ type: ButtonType.Circle }) {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(20)
              .fontColor([DeepColors.TEXT_PRIMARY])
          }
          .width(36)
          .height(36)
          .backgroundColor('rgba(28, 28, 30, 0.65)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
          .onClick(() => {
            router.back();
          })

          Text('帖子详情')
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .margin({ left: 12 })
            .layoutWeight(1)

          // 举报帖子通道
          Row() {
            Text('举报')
              .fontSize(12)
              .fontColor(DeepColors.TEXT_TERTIARY)
          }
          .padding({ left: 10, right: 10, top: 5, bottom: 5 })
          .borderRadius(8)
          .backgroundColor('rgba(28, 28, 30, 0.65)')
          .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.06)' })
          .onClick(() => {
            this.openReport('post', this.postId);
          })
        }
        .width('100%')
        .height(52)
        .padding({ left: 16, right: 16 })
        .backgroundBlurStyle(BlurStyle.Thin, { colorMode: ThemeColorMode.DARK })
        .border({ width: { bottom: 0.6 }, color: 'rgba(255, 255, 255, 0.08)' })

        // 帖子内容与评论流
        List() {
          if (this.post) {
            // 1. 帖子作者卡片
            ListItem() {
              Row() {
                Image(this.post.author_avatar || '')
                  .width(44)
                  .height(44)
                  .borderRadius(22)
                  .backgroundColor('rgba(255, 255, 255, 0.05)')
                  .border({ width: 1, color: 'rgba(255, 255, 255, 0.12)' })
                  .alt('')
                  .onClick(() => {
                    this.selectedUserId = this.post?.author_id || '';
                    this.showUserSheet = true;
                  })

                Column() {
                  Row() {
                    Text(this.post.author_nickname || this.post.author_username || '居民')
                      .fontSize(14)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(DeepColors.TEXT_PRIMARY)

                    // 独立防变形等级微徽章
                    Row() {
                      Text(`Lv.${this.post.author_level || 1}`)
                        .fontSize(9)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(DeepColors.PRIMARY)
                    }
                    .padding({ left: 6, right: 6, top: 1, bottom: 1 })
                    .margin({ left: 6 })
                    .borderRadius(4)
                    .backgroundColor('rgba(59, 130, 246, 0.14)')
                  }
                  .alignItems(VerticalAlign.Center)

                  Row() {
                    Text(this.post.created_at || '刚刚')
                      .fontSize(11)
                      .fontColor(DeepColors.TEXT_TERTIARY)

                    if (this.post.category_name) {
                      Text(` · ${this.post.category_name}`)
                        .fontSize(11)
                        .fontColor(DeepColors.PRIMARY)
                    }
                  }
                  .margin({ top: 3 })
                }
                .alignItems(HorizontalAlign.Start)
                .margin({ left: 10 })
                .layoutWeight(1)

                // 收藏按钮
                Row() {
                  Text('收藏')
                    .fontSize(12)
                    .fontColor(DeepColors.TEXT_SECONDARY)
                }
                .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                .borderRadius(8)
                .backgroundColor('rgba(28, 28, 30, 0.65)')
                .border({ width: 0.6, color: 'rgba(255, 255, 255, 0.08)' })
                .onClick(() => this.handleToggleFavorite())
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 14, bottom: 10 })
            }

            // 2. 帖子正文
            ListItem() {
              Column() {
                Text(this.post.title)
                  .fontSize(18)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.TEXT_PRIMARY)
                  .width('100%')
                  .margin({ bottom: 10 })

                Text(this.post.content_md || this.post.content_html || '')
                  .fontSize(14)
                  .fontColor(DeepColors.TEXT_SECONDARY)
                  .lineHeight(22)
                  .width('100%')

                // 图片九宫格 / 展示
                if (this.post.image_urls && this.post.image_urls.length > 0) {
                  Flex({ wrap: FlexWrap.Wrap, justifyContent: FlexAlign.Start }) {
                    ForEach(this.post.image_urls, (imgUrl: string) => {
                      Image(imgUrl)
                        .width('31%')
                        .height(100)
                        .borderRadius(8)
                        .margin({ right: '2%', bottom: 8 })
                        .alt('')
                    }, (imgUrl: string) => imgUrl)
                  }
                  .width('100%')
                  .margin({ top: 12 })
                }

                // 赞/浏览数据条
                Row() {
                  Row() {
                    Text(this.post.is_liked ? '已点赞' : '赞')
                      .fontSize(12)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(this.post.is_liked ? DeepColors.PRIMARY : DeepColors.TEXT_SECONDARY)
                    Text(` ${this.post.like_count}`)
                      .fontSize(12)
                      .fontColor(this.post.is_liked ? DeepColors.PRIMARY : DeepColors.TEXT_SECONDARY)
                  }
                  .padding({ left: 14, right: 14, top: 6, bottom: 6 })
                  .borderRadius(8)
                  .backgroundColor(this.post.is_liked ? 'rgba(59, 130, 246, 0.15)' : 'rgba(28, 28, 30, 0.65)')
                  .border({ width: 0.6, color: this.post.is_liked ? 'rgba(59, 130, 246, 0.4)' : 'rgba(255, 255, 255, 0.08)' })
                  .onClick(() => this.handleToggleLike())

                  Blank()

                  Text(`浏览 ${this.post.view_count}`)
                    .fontSize(11)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                }
                .width('100%')
                .margin({ top: 16 })
                .alignItems(VerticalAlign.Center)
              }
              .width('100%')
              .padding({ left: 16, right: 16, bottom: 16 })
            }

            // 3. 评论区分割条
            ListItem() {
              Row() {
                Text(`评论 (${this.comments.length})`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DeepColors.TEXT_PRIMARY)
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 12, bottom: 8 })
              .backgroundColor('rgba(20, 20, 22, 0.7)')
            }

            // 4. 评论列表
            if (this.comments.length === 0) {
              ListItem() {
                Column() {
                  Text('暂无评论，来发表第一条见解吧')
                    .fontSize(12)
                    .fontColor(DeepColors.TEXT_TERTIARY)
                }
                .width('100%')
                .padding({ top: 40, bottom: 40 })
                .alignItems(HorizontalAlign.Center)
              }
            } else {
              ForEach(this.comments, (item: CommentItem) => {
                ListItem() {
                  Column() {
                    Row() {
                      Image(item.author_avatar || '')
                        .width(32)
                        .height(32)
                        .borderRadius(16)
                        .backgroundColor(DeepColors.SURFACE_ELEVATED)
                        .alt('')
                        .onClick(() => {
                          this.selectedUserId = item.author_id || '';
                          this.showUserSheet = true;
                        })

                      Column() {
                        Row() {
                          Text(item.author_nickname || item.author_username || '居民')
                            .fontSize(13)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(DeepColors.TEXT_PRIMARY)

                          // 采纳最佳回答徽章 (Accept)
                          if (item.is_accepted) {
                            Row() {
                              Text('已采纳答案')
                                .fontSize(9)
                                .fontWeight(FontWeight.Bold)
                                .fontColor(DeepColors.ACCENT_SUCCESS)
                            }
                            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                            .margin({ left: 6 })
                            .borderRadius(4)
                            .backgroundColor('rgba(16, 185, 129, 0.15)')
                          }

                          // 置顶评论徽章 (Feature)
                          if (item.is_featured) {
                            Row() {
                              Text('置顶')
                                .fontSize(9)
                                .fontWeight(FontWeight.Bold)
                                .fontColor(DeepColors.ACCENT_GOLD)
                            }
                            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                            .margin({ left: 6 })
                            .borderRadius(4)
                            .backgroundColor('rgba(245, 158, 11, 0.15)')
                          }
                        }
                        .alignItems(VerticalAlign.Center)

                        Text(item.created_at || '刚刚')
                          .fontSize(10)
                          .fontColor(DeepColors.TEXT_TERTIARY)
                          .margin({ top: 2 })
                      }
                      .alignItems(HorizontalAlign.Start)
                      .margin({ left: 8 })
                      .layoutWeight(1)

                      // 采纳操作入口（仅楼主可操作）
                      if (!item.is_accepted && this.post?.author_id) {
                        Text('采纳')
                          .fontSize(11)
                          .fontColor(DeepColors.ACCENT_SUCCESS)
                          .margin({ right: 10 })
                          .onClick(() => this.handleAcceptComment(item))
                      }

                      // 置顶操作入口
                      Text(item.is_featured ? '取消置顶' : '置顶')
                        .fontSize(11)
                        .fontColor(DeepColors.ACCENT_GOLD)
                        .margin({ right: 10 })
                        .onClick(() => this.handleFeatureComment(item))

                      // 评论违规举报入口
                      Text('举报')
                        .fontSize(10)
                        .fontColor(DeepColors.TEXT_TERTIARY)
                        .onClick(() => {
                          this.openReport('comment', item.id);
                        })
                    }
                    .width('100%')

                    Text(item.content)
                      .fontSize(13)
                      .fontColor(DeepColors.TEXT_SECONDARY)
                      .lineHeight(18)
                      .margin({ left: 40, top: 6 })
                  }
                  .width('100%')
                  .padding({ left: 16, right: 16, top: 10, bottom: 10 })
                  .border({ width: { bottom: 0.6 }, color: 'rgba(255, 255, 255, 0.06)' })
                }
              }, (item: CommentItem) => item.id)
            }

            // 底部占位防止被输入框遮挡
            ListItem() {
              Column().height(80)
            }
          }
        }
        .width('100%')
        .layoutWeight(1)
      }
      .width('100%')
      .height('100%')

      // 底部评论输入悬浮条 (沉浸光感)
      Row() {
        // 表情包面板微按键
        Button({ type: ButtonType.Circle }) {
          SymbolGlyph($r('sys.symbol.face_smile'))
            .fontSize(18)
            .fontColor([DeepColors.TEXT_SECONDARY])
        }
        .width(36)
        .height(36)
        .backgroundColor('rgba(255, 255, 255, 0.05)')
        .margin({ right: 8 })
        .onClick(() => {
          this.showStickerSheet = true;
        })

        TextInput({ text: this.commentInput, placeholder: '写下你的见解...' })
          .placeholderColor(DeepColors.TEXT_TERTIARY)
          .fontColor(DeepColors.TEXT_PRIMARY)
          .fontSize(13)
          .height(38)
          .backgroundColor('rgba(28, 28, 30, 0.75)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
          .borderRadius(19)
          .padding({ left: 14, right: 14 })
          .layoutWeight(1)
          .onChange((val) => {
            this.commentInput = val;
          })

        Button(this.isSubmittingComment ? '...' : '发送')
          .fontSize(12)
          .fontColor(Color.White)
          .height(34)
          .padding({ left: 16, right: 16 })
          .borderRadius(17)
          .backgroundColor(DeepColors.PRIMARY)
          .margin({ left: 10 })
          .onClick(() => this.handleSendComment())
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 10, bottom: 12 })
      .backgroundBlurStyle(BlurStyle.Thin, { colorMode: ThemeColorMode.DARK })
      .border({ width: { top: 0.6 }, color: 'rgba(255, 255, 255, 0.08)' })

      // 他人主页半屏抽屉
      UserProfileSheet({
        userId: this.selectedUserId,
        onClose: () => {
          this.showUserSheet = false;
        }
      })
      .visibility(this.showUserSheet ? Visibility.Visible : Visibility.None)

      // 举报违规弹窗 (支持帖子与评论举报)
      if (this.showReportDialog) {
        Stack() {
          Column() {
            Text(this.reportTargetType === 'post' ? '举报违规帖子' : '举报违规评论')
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.TEXT_PRIMARY)
              .width('100%')

            Text('请如实描述违规行为（垃圾广告/涉政涉黄/人身攻击等）')
              .fontSize(12)
              .fontColor(DeepColors.TEXT_TERTIARY)
              .width('100%')
              .margin({ top: 4, bottom: 12 })

            TextArea({ text: this.reportReason, placeholder: '输入具体原因...' })
              .placeholderColor(DeepColors.TEXT_TERTIARY)
              .fontColor(DeepColors.TEXT_PRIMARY)
              .fontSize(13)
              .height(90)
              .backgroundColor('rgba(28, 28, 30, 0.85)')
              .borderRadius(8)
              .onChange((val) => {
                this.reportReason = val;
              })

            Row() {
              Button('取消')
                .fontSize(13)
                .fontColor(DeepColors.TEXT_SECONDARY)
                .backgroundColor(Color.Transparent)
                .onClick(() => {
                  this.showReportDialog = false;
                })

              Button('提交举报')
                .fontSize(13)
                .fontColor(Color.White)
                .backgroundColor(DeepColors.ACCENT_ERROR)
                .margin({ left: 12 })
                .onClick(() => this.handleSubmitReport())
            }
            .width('100%')
            .justifyContent(FlexAlign.End)
            .margin({ top: 14 })
          }
          .width('88%')
          .padding(18)
          .borderRadius(16)
          .backgroundColor('rgba(20, 20, 22, 0.95)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.1)', radius: 16 })
        }
        .width('100%')
        .height('100%')
        .backgroundColor('rgba(0, 0, 0, 0.65)')
        .alignment(Alignment.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.BACKGROUND)
    .bindSheet($$this.showStickerSheet, this.StickerSheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
  }

  @Builder
  StickerSheetBuilder() {
    StickerManageSheet({
      onSelectSticker: (url: string) => {
        this.commentInput = url;
        this.showStickerSheet = false;
      }
    })
  }
}
