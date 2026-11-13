import { CategoryItem, PostItem } from '../model/PostModels';
import { DeepColors } from '../common/Constants';
import { HttpUtils } from '../common/HttpUtils';
import { PostCard } from '../components/PostCard';
import { UserProfileSheet } from '../components/UserProfileSheet';
import { LeaderboardSheet } from '../components/LeaderboardSheet';
import promptAction from '@ohos.promptAction';

/**
 * 鸿蒙原生首页屏幕 (HomeScreen ArkUI)
 * 具备双层舒展顶栏、推荐/关注专属流、板块分类筛选、全屏沉浸与丝滑流畅高帧滚动
 */
@Component
export struct HomeScreen {
  @State feedType: string = 'recommend'; // 'recommend' | 'following'
  @State posts: PostItem[] = [];
  @State categories: CategoryItem[] = [];
  @State selectedCategoryId: number | null = null;
  @State isRefreshing: boolean = false;
  @State isLoading: boolean = true;
  @State isSearchActive: boolean = false;
  @State searchQuery: string = '';
  @State showLeaderboardSheet: boolean = false;
  @State selectedUsername: string = '';
  @State selectedUserId: string = '';
  @State showUserProfileSheet: boolean = false;
  @State announcement: string = '欢迎加入深度社区！打造纯粹极客分享与原生多端互联社区。';
  onPostClick: (id: string) => void = () => {};
  onOpenDrawer: () => void = () => {};

  aboutToAppear() {
    this.loadCategories();
    this.loadPosts();
  }

  async loadCategories() {
    try {
      const res = await HttpUtils.get<CategoryItem[]>('api/posts/categories');
      this.categories = (res.isSuccess && res.data) ? res.data : [];
    } catch (e) {
      console.error('Failed to load categories: ' + JSON.stringify(e));
    }
  }

  async loadPosts() {
    this.isLoading = true;
    try {
      const params: Record<string, string | number | boolean> = {
        feed: this.feedType,
        page: 1,
        size: 30
      };
      if (this.selectedCategoryId !== null) {
        params.category_id = this.selectedCategoryId;
      }
      if (this.searchQuery.trim()) {
        params.q = this.searchQuery.trim();
      }
      const res = await HttpUtils.get<PostItem[]>('api/posts', params);
      this.posts = (res.isSuccess && res.data) ? res.data : [];
    } catch (e) {
      console.error('Failed to load posts: ' + JSON.stringify(e));
      promptAction.showToast({ message: '加载帖子失败，请重试' });
    } finally {
      this.isLoading = false;
      this.isRefreshing = false;
    }
  }

  build() {
    Column() {
      // 1. 顶栏 (Header) - 鸿蒙官方沉浸光感
      Row() {
        Row({ space: 8 }) {
          Button({ type: ButtonType.Normal }) {
            SymbolGlyph($r('sys.symbol.line_horizontal_3'))
              .fontSize(22)
              .fontColor([DeepColors.TEXT_PRIMARY])
          }
          .backgroundColor(Color.Transparent)
          .width(36)
          .height(36)
          .onClick(() => {
            this.onOpenDrawer();
          })

          Column({ space: 1 }) {
            Text('深度社区')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor(DeepColors.TEXT_PRIMARY)

            Text('DEEP COMMUNITY')
              .fontSize(9)
              .fontColor(DeepColors.TEXT_TERTIARY)
              .letterSpacing(1)
          }
          .alignItems(HorizontalAlign.Start)
        }

        // 右侧操作按钮组：搜索 + 全站榜单
        Row({ space: 8 }) {
          // 搜索按钮
          Button({ type: ButtonType.Circle }) {
            SymbolGlyph(this.isSearchActive ? $r('sys.symbol.xmark') : $r('sys.symbol.magnifyingglass'))
              .fontSize(17)
              .fontColor([this.isSearchActive ? DeepColors.PRIMARY : DeepColors.TEXT_PRIMARY])
          }
          .width(36)
          .height(36)
          .backgroundColor(this.isSearchActive ? 'rgba(59, 130, 246, 0.15)' : 'rgba(28, 28, 30, 0.65)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
          .onClick(() => {
            this.isSearchActive = !this.isSearchActive;
          })

          // 全站榜单胶囊按钮 - 沉浸微高光
          Row({ space: 4 }) {
            SymbolGlyph($r('sys.symbol.chart_bar_fill'))
              .fontSize(15)
              .fontColor([DeepColors.PRIMARY])

            Text('榜单')
              .fontSize(12)
              .fontWeight(FontWeight.SemiBold)
              .fontColor(DeepColors.TEXT_PRIMARY)
          }
          .height(36)
          .padding({ left: 12, right: 12 })
          .borderRadius(18)
          .backgroundColor('rgba(28, 28, 30, 0.65)')
          .border({ width: 0.8, color: 'rgba(255, 255, 255, 0.08)' })
          .onClick(() => {
            this.showLeaderboardSheet = true;
          })
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)
      .backgroundBlurStyle(BlurStyle.Thin, { colorMode: ThemeColorMode.DARK })

      // 展开实时搜索条
      if (this.isSearchActive) {
        Row() {
          TextInput({ placeholder: '搜索文章、日常、关键词…', text: this.searchQuery })
            .layoutWeight(1)
            .height(40)
            .borderRadius(12)
            .backgroundColor(DeepColors.SURFACE_ELEVATED)
            .fontColor(DeepColors.TEXT_PRIMARY)
            .placeholderColor(DeepColors.TEXT_TERTIARY)
            .onChange((val) => {
              this.searchQuery = val;
            })
            .onSubmit(() => {
              this.loadPosts();
            })
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 8 })
      }

      // 2. 次级功能栏：“推荐 / 关注” 专属动态流双 Tab 与刷新状态
      Row() {
        Row({ space: 2 }) {
          this.FeedTabPill('推荐', this.feedType === 'recommend')
          this.FeedTabPill('关注', this.feedType === 'following')
        }
        .padding(3)
        .borderRadius(14)
        .backgroundColor(DeepColors.SURFACE_ELEVATED)
        .border({ width: 0.8, color: DeepColors.BORDER })

        // 刷新状态提示
        Row({ space: 4 }) {
          if (this.isLoading) {
            LoadingProgress().width(13).height(13).color(DeepColors.PRIMARY)
          }
          Text(this.isLoading ? '刷新中…' : '点击刷新')
            .fontSize(11)
            .fontColor(DeepColors.TEXT_TERTIARY)
        }
        .onClick(() => {
          this.loadPosts();
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 4, bottom: 8 })
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)

      // 3. 板块分类横滑胶囊
      List({ space: 8 }) {
        ListItem() {
          this.CategoryPill('全部板块', this.selectedCategoryId === null, null)
        }
        ForEach(this.categories, (cat: CategoryItem) => {
          ListItem() {
            this.CategoryPill(cat.name, this.selectedCategoryId === cat.id, cat.id)
          }
        })
      }
      .listDirection(Axis.Horizontal)
      .width('100%')
      .height(34)
      .padding({ left: 16, right: 16 })
      .margin({ bottom: 8 })

      // 4. 帖子流列表 (支持下拉刷新)
      Refresh({ refreshing: $$this.isRefreshing, offset: 64, friction: 62 }) {
        if (this.isLoading && this.posts.length === 0) {
          Column() {
            LoadingProgress().width(38).height(38).color(DeepColors.PRIMARY)
          }
          .width('100%')
          .height('70%')
          .justifyContent(FlexAlign.Center)
        } else if (this.posts.length === 0) {
          Column({ space: 12 }) {
            SymbolGlyph($r('sys.symbol.doc_plaintext'))
              .fontSize(44)
              .fontColor([DeepColors.TEXT_TERTIARY])

            Text('暂无相关帖子')
              .fontSize(13)
              .fontColor(DeepColors.TEXT_TERTIARY)

            Button('刷新重试')
              .fontSize(12)
              .height(34)
              .borderRadius(10)
              .backgroundColor('rgba(59, 130, 246, 0.12)')
              .fontColor(DeepColors.PRIMARY)
              .onClick(() => {
                this.loadPosts();
              })
          }
          .width('100%')
          .height('60%')
          .justifyContent(FlexAlign.Center)
        } else {
          List({ space: 14 }) {
            ForEach(this.posts, (item: PostItem) => {
              ListItem() {
                PostCard({
                  post: item,
                  onPostClick: (id) => {
                    this.onPostClick(id);
                  },
                  onLikeClick: (id) => {
                    HttpUtils.post(`api/posts/${id}/like`).catch((_e) => {});
                  },
                  onAuthorClick: (uName, uId) => {
                    this.selectedUsername = uName;
                    this.selectedUserId = uId;
                    this.showUserProfileSheet = true;
                  }
                })
              }
            }, (item: PostItem) => item.id)
          }
          .width('100%')
          .layoutWeight(1)
          .padding({ left: 16, right: 16, top: 4, bottom: 90 })
        }
      }
      .onRefreshing(() => {
        this.loadPosts();
      })
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(DeepColors.BACKGROUND)
    // 榜单半屏抽屉
    .bindSheet($$this.showLeaderboardSheet, this.LeaderboardSheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
    // 用户个人主页半屏抽屉
    .bindSheet($$this.showUserProfileSheet, this.UserProfileSheetBuilder(), {
      height: SheetSize.MEDIUM,
      dragBar: true,
      backgroundColor: DeepColors.SURFACE
    })
  }

  @Builder
  FeedTabPill(label: string, isSelected: boolean) {
    Text(label)
      .fontSize(13)
      .fontWeight(isSelected ? FontWeight.Bold : FontWeight.Medium)
      .fontColor(isSelected ? Color.White : DeepColors.TEXT_SECONDARY)
      .padding({ left: 16, right: 16, top: 6, bottom: 6 })
      .borderRadius(11)
      .backgroundColor(isSelected ? DeepColors.PRIMARY : Color.Transparent)
      .onClick(() => {
        this.feedType = label === '推荐' ? 'recommend' : 'following';
        this.loadPosts();
      })
  }

  @Builder
  CategoryPill(name: string, isSelected: boolean, catId: number | null) {
    Text(name)
      .fontSize(12)
      .fontWeight(isSelected ? FontWeight.SemiBold : FontWeight.Normal)
      .fontColor(isSelected ? Color.White : DeepColors.TEXT_TERTIARY)
      .padding({ left: 12, right: 12, top: 5, bottom: 5 })
      .borderRadius(14)
      .backgroundColor(isSelected ? DeepColors.PRIMARY : DeepColors.SURFACE_ELEVATED)
      .border({ width: isSelected ? 0 : 0.6, color: DeepColors.BORDER })
      .onClick(() => {
        this.selectedCategoryId = catId;
        this.loadPosts();
      })
  }

  @Builder
  LeaderboardSheetBuilder() {
    LeaderboardSheet({
      onUserClick: (username, userId) => {
        this.showLeaderboardSheet = false;
        this.selectedUsername = username;
        this.selectedUserId = userId;
        this.showUserProfileSheet = true;
      }
    })
  }

  @Builder
  UserProfileSheetBuilder() {
    UserProfileSheet({
      username: this.selectedUsername,
      userId: this.selectedUserId,
      onClose: () => {
        this.showUserProfileSheet = false;
      }
    })
  }
}
