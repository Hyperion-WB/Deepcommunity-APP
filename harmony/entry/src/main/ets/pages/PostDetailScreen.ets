import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { PostItem, CommentItem } from '../model/PostModels';
import { UserProfileSheet } from '../components/UserProfileSheet';

/**
 * 鸿蒙原生帖子详情与交互页面 (PostDetailScreen)
 * - 沉浸式磨砂顶栏与平滑回退
 * - 作者信息与独立防变形等级徽章
 * - 正文与多图展示
 * - 点赞、收藏、举报违规通道 (Report)
 * - 评论互动流：问答采纳 (Accept)、评论置顶 (Feature)、发表评论
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
  @State reportReason: string = '';

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
    const res = await HttpUtils.get<PostItem>(`/posts/${this.postId}`);
    this.isLoading = false;
    if (res.isSuccess && res.data) {
      this.post = res.data;
    } else {
      promptAction.showToast({ message: res.errorMsg || '加载帖子失败' });
    }
  }

  async loadComments() {
    const res = await HttpUtils.get<CommentItem[]>(`/posts/${this.postId}/comments`);
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

    const res = await HttpUtils.post<{ liked: boolean; count: number }>(`/posts/${this.postId}/like`);
    if (res.isSuccess && res.data) {
      this.post.is_liked = res.data.liked;
      this.post.like_count = res.data.count;
    }
  }

  async handleToggleFavorite() {
    const res = await HttpUtils.post<{ favorited: boolean }>(`/posts/${this.postId}/favorite`);
    if (res.isSuccess) {
      promptAction.showToast({ message: res.data?.favorited ? '已加入个人收藏' : '已取消收藏' });
    }
  }

  async handleSendComment() {
    if (!this.commentInput.trim() || this.isSubmittingComment) return;
    this.isSubmittingComment = true;
    const res = await HttpUtils.post<CommentItem>(`/posts/${this.postId}/comments`, {
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
    const res = await HttpUtils.post<{ success: boolean }>(`/posts/${this.postId}/accept/${comment.id}`);
    if (res.isSuccess) {
      comment.is_accepted = true;
      promptAction.showToast({ message: '已采纳该回答为最佳答案' });
    } else {
      promptAction.showToast({ message: res.errorMsg || '采纳失败' });
    }
  }

  async handleFeatureComment(comment: CommentItem) {
    const res = await HttpUtils.post<{ success: boolean }>(`/posts/${this.postId}/comments/${comment.id}/feature`);
    if (res.isSuccess) {
      comment.is_featured = !comment.is_featured;
      promptAction.showToast({ message: comment.is_featured ? '已将该评论置顶' : '已取消置顶' });
    }
  }

  async handleSubmitReport() {
    if (!this.reportReason.trim()) {
      promptAction.showToast({ message: '请填写举报原因' });
      return;
    }
    const res = await HttpUtils.post<{ success: boolean }>(`/reports`, {
      target_type: 'post',
      target_id: this.postId,
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
        // 顶部沉浸导航栏
        Row() {
          Row() {
            Text('←')
              .fontSize(20)
              .fontColor(DeepColors.TextPrimary)
          }
          .width(36)
          .height(36)
          .borderRadius(18)
          .backgroundColor(DeepColors.SurfaceRaised)
          .justifyContent(FlexAlign.Center)
          .onClick(() => {
            router.back();
          })

          Text('帖子详情')
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
            .fontColor(DeepColors.TextPrimary)
            .margin({ left: 12 })
            .layoutWeight(1)

          // 举报通道入口
          Row() {
            Text('举报')
              .fontSize(12)
              .fontColor(DeepColors.TextTertiary)
          }
          .padding({ left: 8, right: 8, top: 4, bottom: 4 })
          .borderRadius(6)
          .backgroundColor(DeepColors.SurfaceRaised)
          .onClick(() => {
            this.showReportDialog = true;
          })
        }
        .width('100%')
        .height(52)
        .padding({ left: 16, right: 16 })
        .backgroundColor(DeepColors.Surface)
        .border({ width: { bottom: 0.6 }, color: DeepColors.GlassBorder })

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
                  .backgroundColor(DeepColors.SurfaceRaised)
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
                      .fontColor(DeepColors.TextPrimary)

                    // 独立防变形等级微徽章
                    Row() {
                      Text(`Lv.${this.post.author_level || 1}`)
                        .fontSize(9)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(DeepColors.Primary)
                    }
                    .padding({ left: 6, right: 6, top: 1, bottom: 1 })
                    .margin({ left: 6 })
                    .borderRadius(4)
                    .backgroundColor('#1A3B82F6')
                  }
                  .alignItems(VerticalAlign.Center)

                  Row() {
                    Text(this.post.created_at || '刚刚')
                      .fontSize(11)
                      .fontColor(DeepColors.TextTertiary)

                    if (this.post.category_name) {
                      Text(` · ${this.post.category_name}`)
                        .fontSize(11)
                        .fontColor(DeepColors.Primary)
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
                    .fontColor(DeepColors.TextSecondary)
                }
                .padding({ left: 10, right: 10, top: 5, bottom: 5 })
                .borderRadius(6)
                .backgroundColor(DeepColors.SurfaceRaised)
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
                  .fontColor(DeepColors.TextPrimary)
                  .width('100%')
                  .margin({ bottom: 10 })

                Text(this.post.content_md || this.post.content_html || '')
                  .fontSize(14)
                  .fontColor(DeepColors.TextSecondary)
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
                      .fontColor(this.post.is_liked ? DeepColors.Primary : DeepColors.TextSecondary)
                    Text(` ${this.post.like_count}`)
                      .fontSize(12)
                      .fontColor(this.post.is_liked ? DeepColors.Primary : DeepColors.TextSecondary)
                  }
                  .padding({ left: 12, right: 12, top: 6, bottom: 6 })
                  .borderRadius(8)
                  .backgroundColor(this.post.is_liked ? '#1A3B82F6' : DeepColors.SurfaceRaised)
                  .onClick(() => this.handleToggleLike())

                  Blank()

                  Text(`浏览 ${this.post.view_count}`)
                    .fontSize(11)
                    .fontColor(DeepColors.TextTertiary)
                }
                .width('100%')
                .margin({ top: 14 })
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
                  .fontColor(DeepColors.TextPrimary)
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 12, bottom: 8 })
              .backgroundColor(DeepColors.Surface)
            }

            // 4. 评论列表
            if (this.comments.length === 0) {
              ListItem() {
                Column() {
                  Text('暂无评论，来发表第一条见解吧')
                    .fontSize(12)
                    .fontColor(DeepColors.TextTertiary)
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
                        .backgroundColor(DeepColors.SurfaceRaised)
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
                            .fontColor(DeepColors.TextPrimary)

                          // 采纳最佳回答徽章 (Accept)
                          if (item.is_accepted) {
                            Row() {
                              Text('已采纳答案')
                                .fontSize(9)
                                .fontWeight(FontWeight.Bold)
                                .fontColor(DeepColors.Success)
                            }
                            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                            .margin({ left: 6 })
                            .borderRadius(4)
                            .backgroundColor('#1A10B981')
                          }

                          // 置顶评论徽章 (Feature)
                          if (item.is_featured) {
                            Row() {
                              Text('置顶')
                                .fontSize(9)
                                .fontWeight(FontWeight.Bold)
                                .fontColor('#F59E0B')
                            }
                            .padding({ left: 5, right: 5, top: 1, bottom: 1 })
                            .margin({ left: 6 })
                            .borderRadius(4)
                            .backgroundColor('#1AF59E0B')
                          }
                        }
                        .alignItems(VerticalAlign.Center)

                        Text(item.created_at || '刚刚')
                          .fontSize(10)
                          .fontColor(DeepColors.TextTertiary)
                          .margin({ top: 2 })
                      }
                      .alignItems(HorizontalAlign.Start)
                      .margin({ left: 8 })
                      .layoutWeight(1)

                      // 采纳操作入口（仅楼主可操作）
                      if (!item.is_accepted && this.post?.author_id) {
                        Text('采纳')
                          .fontSize(11)
                          .fontColor(DeepColors.Success)
                          .margin({ right: 10 })
                          .onClick(() => this.handleAcceptComment(item))
                      }

                      // 置顶操作入口
                      Text(item.is_featured ? '取消置顶' : '置顶')
                        .fontSize(11)
                        .fontColor('#F59E0B')
                        .onClick(() => this.handleFeatureComment(item))
                    }
                    .width('100%')

                    Text(item.content)
                      .fontSize(13)
                      .fontColor(DeepColors.TextSecondary)
                      .lineHeight(18)
                      .margin({ left: 40, top: 6 })
                  }
                  .width('100%')
                  .padding({ left: 16, right: 16, top: 10, bottom: 10 })
                  .border({ width: { bottom: 0.6 }, color: DeepColors.GlassBorder })
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

      // 底部评论输入悬浮条
      Row() {
        TextInput({ text: this.commentInput, placeholder: '写下你的见解...' })
          .placeholderColor(DeepColors.TextTertiary)
          .fontColor(DeepColors.TextPrimary)
          .fontSize(13)
          .height(38)
          .backgroundColor(DeepColors.SurfaceRaised)
          .borderRadius(19)
          .padding({ left: 14, right: 14 })
          .layoutWeight(1)
          .onChange((val) => {
            this.commentInput = val;
          })

        Button(this.isSubmittingComment ? '...' : '发送')
          .fontSize(12)
          .fontColor(DeepColors.TextPrimary)
          .height(34)
          .padding({ left: 16, right: 16 })
          .borderRadius(17)
          .backgroundColor(DeepColors.Primary)
          .margin({ left: 10 })
          .onClick(() => this.handleSendComment())
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 10, bottom: 12 })
      .backgroundColor(DeepColors.Surface)
      .border({ width: { top: 0.6 }, color: DeepColors.GlassBorder })

      // 他人主页半屏抽屉
      UserProfileSheet({
        userId: this.selectedUserId,
        visible: this.showUserSheet,
        onClose: () => {
          this.showUserSheet = false;
        }
      })

      // 举报违规弹窗
      if (this.showReportDialog) {
        Stack() {
          Column() {
            Text('举报此内容')
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.TextPrimary)
              .width('100%')

            Text('请如实描述违规行为（垃圾广告/涉政涉黄/人身攻击等）')
              .fontSize(12)
              .fontColor(DeepColors.TextTertiary)
              .width('100%')
              .margin({ top: 4, bottom: 12 })

            TextArea({ text: this.reportReason, placeholder: '输入具体原因...' })
              .placeholderColor(DeepColors.TextTertiary)
              .fontColor(DeepColors.TextPrimary)
              .fontSize(13)
              .height(90)
              .backgroundColor(DeepColors.SurfaceRaised)
              .borderRadius(8)
              .onChange((val) => {
                this.reportReason = val;
              })

            Row() {
              Button('取消')
                .fontSize(13)
                .fontColor(DeepColors.TextSecondary)
                .backgroundColor(Color.Transparent)
                .onClick(() => {
                  this.showReportDialog = false;
                })

              Button('提交举报')
                .fontSize(13)
                .fontColor(DeepColors.TextPrimary)
                .backgroundColor('#EF4444')
                .margin({ left: 12 })
                .onClick(() => this.handleSubmitReport())
            }
            .width('100%')
            .justifyContent(FlexAlign.End)
            .margin({ top: 14 })
          }
          .width('88%')
          .padding(18)
          .borderRadius(14)
          .backgroundColor(DeepColors.Surface)
          .border({ width: 0.6, color: DeepColors.GlassBorder, radius: 14 })
        }
        .width('100%')
        .height('100%')
        .backgroundColor('#80000000')
        .alignment(Alignment.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.Background)
  }
}
