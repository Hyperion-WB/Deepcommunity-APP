import React, { useEffect, useState, useRef } from 'react';
import {
  FileText,
  MessageSquare,
  Heart,
  Eye,
  Check,
  Pin,
  ChevronRight,
  Bookmark,
  Share2,
  Shield,
  Calendar,
  Terminal,
  Layout,
  List,
} from 'lucide-react';
import { apiClient } from '../api/client';
import { Leaderboard } from '../components/Leaderboard';
import { thumbUrl, thumbSrcSet } from '../utils/thumb';
import type { PostSimple, UserProfile } from '../types';
import { LevelBadge } from '../components/LevelBadge';
import { UserAvatar } from '../components/UserAvatar';
import { PostCardSkeleton } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import { FriendLinksCard } from '../components/FriendLinksCard';
import { formatEast8Date, getSmartDailyTitle } from '../utils/format';

export interface ProHomeProps {
  user: UserProfile | null;
  isAdmin?: boolean;
  onSelectPost: (postId: string) => void;
  onSelectUser?: (username: string) => void;
  onRequireAuth?: () => void;
  onGoWrite?: () => void;
  onGoCheckin?: () => void;
  onGoRules?: () => void;
  onGoLegal?: () => void;
  onOpenLinks?: () => void;
  onGoWriteDaily?: () => void;
  isActive?: boolean;
}

const DEMO_NEWS: PostSimple = {
  id: 'demo-news-1',
  title: '【极客早报】前沿技术架构与开源交流动态，记录日常，分享热爱',
  post_type: 'news',
  status: 'published',
  view_count: 3280,
  like_count: 142,
  comment_count: 38,
  created_at: new Date().toISOString(),
  author_username: 'geek_news',
  author_nickname: '每日早报·置顶要闻',
  author_avatar: '',
  author_level: 8,
  is_pinned: false,
  reading_time_minutes: 3,
};

/**
 * 深度极客社区 · 全新专业版首页 (ProHome)
 * 特点：
 * 1. 宽屏三栏专业控制台布局 (桌面端左工作台+中流+右监控面板)；
 * 2. 独家高密度摸鱼纯文本流 (Dense Mode) 与专业极客卡片流自由切换；
 * 3. 真实友情链接动态同步，支持深情友链徽章；
 * 4. 极客摸鱼老板键伪装控制台 (快捷键 Alt+B)；
 * 5. 全面适配移动端自适应流式排版，底部零遮挡。
 */
export const ProHome: React.FC<ProHomeProps> = ({
  user,
  isAdmin = false,
  onSelectPost,
  onSelectUser,
  onRequireAuth,
  onGoWrite,
  onGoCheckin,
  onGoRules,
  onGoLegal,
  onOpenLinks,
  onGoWriteDaily,
  isActive = true,
}) => {
  const toast = useToast();

  // 核心数据状态
  const [posts, setPosts] = useState<PostSimple[]>([]);
  const [newsPost, setNewsPost] = useState<PostSimple | null>(DEMO_NEWS);
  const [categories, setCategories] = useState<string[]>([
    '全部',
    '科技前沿',
    '后端架构',
    '前端全栈',
    'AI探索',
    '极客日常',
  ]);
  const [selectedCategory, setSelectedCategory] = useState('全部');
  const [filter, setFilter] = useState<'latest' | 'hot' | 'discuss'>('latest');
  const [streamTab, setStreamTab] = useState<'all' | 'following'>('all');
  const [feedViewMode, setFeedViewMode] = useState<'card' | 'dense'>(() => {
    try {
      return (localStorage.getItem('wb_pro_feed_view_mode') as 'card' | 'dense') || 'card';
    } catch {
      return 'card';
    }
  });

  // 摸鱼老板键
  const [bossMode, setBossMode] = useState(false);

  // 分页与滚动状态
  const [currentPage, setCurrentPage] = useState(1);
  const [displayTotal, setDisplayTotal] = useState(0);
  const [isFetchingPosts, setIsFetchingPosts] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);

  // 签到状态
  const [hasCheckedInToday, setHasCheckedInToday] = useState(false);
  const [checkinStreak, setCheckinStreak] = useState(0);

  const bottomSentinelRef = useRef<HTMLDivElement | null>(null);

  // 切换流模式（卡片 vs 摸鱼单行）
  const toggleFeedViewMode = (mode: 'card' | 'dense') => {
    setFeedViewMode(mode);
    try {
      localStorage.setItem('wb_pro_feed_view_mode', mode);
    } catch {}
  };

  // 监听 Alt+B 极客老板键
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.altKey && (e.key === 'b' || e.key === 'B')) {
        e.preventDefault();
        setBossMode((prev) => !prev);
      }
      if (e.key === 'Escape' && bossMode) {
        setBossMode(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [bossMode]);

  // 加载全站分类
  useEffect(() => {
    let alive = true;
    apiClient
      .get('/categories')
      .then((res) => {
        if (!alive || !Array.isArray(res.data)) return;
        const catNames: string[] = [];
        res.data.forEach((c: any) => {
          if (c.slug !== 'news' && c.name !== '新闻早报') catNames.push(c.name);
        });
        setCategories(Array.from(new Set(['全部', ...catNames, '极客日常'])));
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 加载每日早报
  useEffect(() => {
    let alive = true;
    apiClient
      .get('/posts/daily-news')
      .then((res) => {
        if (alive && res.data && res.data.id) setNewsPost(res.data);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 加载签到状态
  useEffect(() => {
    if (!user) {
      setHasCheckedInToday(false);
      return;
    }
    let alive = true;
    apiClient
      .get('/checkin/today')
      .then((res) => {
        if (!alive) return;
        setHasCheckedInToday(Boolean(res.data?.checked_in_today));
        setCheckinStreak(res.data?.streak_days || 0);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [user]);

  // 加载主列表数据
  const fetchPosts = async (page: number, append = false) => {
    if (page === 1) setIsFetchingPosts(true);
    else setLoadingMore(true);

    try {
      const params: Record<string, any> = {
        page,
        size: 15,
        filter,
      };
      if (selectedCategory !== '全部') {
        params.category = selectedCategory;
      }
      if (streamTab === 'following' && user) {
        params.following_only = true;
      }

      const res = await apiClient.get('/posts', { params });
      const items = res.data?.items || res.data || [];
      const total = res.data?.total || items.length;
      const pages = res.data?.pages || Math.ceil(total / 15) || 1;

      if (append) {
        setPosts((prev) => [...prev, ...items]);
      } else {
        setPosts(items);
      }
      setDisplayTotal(total);
      setHasMore(page < pages);
    } catch {
      if (!append) setPosts([]);
    } finally {
      setIsFetchingPosts(false);
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    if (!isActive) return;
    setCurrentPage(1);
    fetchPosts(1, false);
  }, [selectedCategory, filter, streamTab, isActive]);

  // 触底无限滚动
  useEffect(() => {
    if (!hasMore || loadingMore || isFetchingPosts) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore && !isFetchingPosts) {
          const nextPage = currentPage + 1;
          setCurrentPage(nextPage);
          fetchPosts(nextPage, true);
        }
      },
      { threshold: 0.1 }
    );
    if (bottomSentinelRef.current) observer.observe(bottomSentinelRef.current);
    return () => observer.disconnect();
  }, [hasMore, loadingMore, isFetchingPosts, currentPage]);

  // 点赞操作
  const handleQuickLike = async (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    if (!user) {
      onRequireAuth?.();
      return;
    }
    try {
      const res = await apiClient.post(`/posts/${post.id}/like`);
      const updatedLikeCount = res.data?.like_count ?? (post.like_count || 0) + 1;
      setPosts((prev) =>
        prev.map((p) => (p.id === post.id ? { ...p, like_count: updatedLikeCount } : p))
      );
      toast.success('点赞成功');
    } catch {
      toast.error('点赞失败');
    }
  };

  // 收藏操作
  const handleToggleCollect = async (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    if (!user) {
      onRequireAuth?.();
      return;
    }
    const willCollect = !post.is_collected;
    try {
      if (willCollect) {
        await apiClient.post(`/posts/${post.id}/collect`);
        toast.success('已加入个人收藏');
      } else {
        await apiClient.delete(`/posts/${post.id}/collect`);
        toast.info('已取消收藏');
      }
      setPosts((prev) =>
        prev.map((p) => (p.id === post.id ? { ...p, is_collected: willCollect } : p))
      );
    } catch {
      toast.error('操作失败');
    }
  };

  // 分享操作
  const handleShare = (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    const url = `${window.location.origin}?post=${post.id}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url).then(() => toast.success('链接已复制到剪贴板'));
    } else {
      toast.info(url);
    }
  };

  // 打卡操作
  const handleCheckin = async () => {
    if (!user) {
      onRequireAuth?.();
      return;
    }
    if (hasCheckedInToday) {
      onGoCheckin?.();
      return;
    }
    try {
      const res = await apiClient.post('/checkin');
      setHasCheckedInToday(true);
      setCheckinStreak(res.data?.streak_days || checkinStreak + 1);
      toast.success('今日打卡成功，经验 +10！');
    } catch {
      onGoCheckin?.();
    }
  };

  return (
    <div className="max-w-[1560px] mx-auto px-2 sm:px-4 py-2 sm:py-3 pb-32 sm:pb-8 flex gap-4 sm:gap-5 items-start w-full transition-all">
      {/* 1. 左侧工作台与导航 (桌面端 w-52 规整列出) */}
      <aside className="w-52 shrink-0 hidden md:block space-y-3.5 select-none sticky top-16">
        {/* 技术分区 */}
        <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-2 shadow-2xs space-y-1">
          <div className="px-3 py-1.5 text-3xs font-bold text-wb-text-muted uppercase tracking-wider flex items-center justify-between">
            <span>技术分区</span>
            <span className="font-mono text-[9px] bg-wb-bg-subtle px-1 rounded">PRO</span>
          </div>
          {categories.map((cat) => {
            const isActive = selectedCategory === cat;
            return (
              <button
                key={cat}
                type="button"
                onClick={() => setSelectedCategory(cat)}
                className={`w-full flex items-center justify-between px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                  isActive
                    ? 'bg-wb-text-primary text-wb-bg-page font-semibold shadow-xs'
                    : 'text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle'
                }`}
              >
                <span>{cat}</span>
                {isActive && <ChevronRight className="w-3 h-3 opacity-80" />}
              </button>
            );
          })}
        </div>

        {/* 快捷工作台 */}
        <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 shadow-2xs space-y-2">
          <div className="text-3xs font-bold text-wb-text-muted uppercase tracking-wider">
            快捷工作台
          </div>
          <div className="space-y-1">
            <button
              type="button"
              onClick={() => {
                if (!user) onRequireAuth?.();
                else onGoWrite?.();
              }}
              className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
            >
              <FileText className="w-3.5 h-3.5 text-blue-500" />
              <span>发布新帖</span>
            </button>
            <button
              type="button"
              onClick={handleCheckin}
              className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                hasCheckedInToday
                  ? 'text-amber-600 bg-amber-500/10'
                  : 'text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle'
              }`}
            >
              <Calendar className="w-3.5 h-3.5 text-amber-500" />
              <span>{hasCheckedInToday ? `今日已打卡 (${checkinStreak}天)` : '今日签到'}</span>
            </button>
          </div>
        </div>

        {/* 友情链接 (真实对齐后端接口，支持深情友链) */}
        <FriendLinksCard variant="sidebar" onOpenAll={onOpenLinks} />

        {/* 极客自治公约 */}
        <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 shadow-2xs space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <h4 className="font-semibold text-xs text-wb-text-primary flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-wb-text-muted" />
              <span>极客自治公约</span>
            </h4>
            <span className="text-3xs text-wb-text-muted font-mono">v4.2</span>
          </div>
          <p className="text-wb-text-muted text-3xs leading-relaxed">
            以实证与代码为基石，纯粹技术探讨，守护开源交流净土。
          </p>
          <div className="pt-1.5 border-t border-wb-border-subtle/60 space-y-1 text-3xs">
            <div
              onClick={onGoRules}
              className="text-wb-text-secondary hover:text-wb-text-primary hover:underline cursor-pointer flex items-center justify-between"
            >
              <span>入站必读规范</span>
              <span>&rarr;</span>
            </div>
            <div
              onClick={onGoLegal}
              className="text-wb-text-secondary hover:text-wb-text-primary hover:underline cursor-pointer flex items-center justify-between"
            >
              <span>免责与开源声明</span>
              <span>&rarr;</span>
            </div>
          </div>
        </div>
      </aside>

      {/* 2. 中间主信息流 */}
      <div className="flex-1 min-w-0 space-y-3.5">
        {/* 每日极客早报通栏卡片 (自适应响应式，绝不粗暴截断) */}
        <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 sm:p-3.5 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-2 sm:gap-3">
          <div className="flex items-center gap-2 sm:gap-2.5 min-w-0 flex-1">
            <span className="px-1.5 sm:px-2 py-0.5 rounded bg-wb-text-primary text-wb-bg-page text-3xs font-semibold shrink-0">
              <span className="hidden sm:inline">每日早报·首要要闻</span>
              <span className="sm:hidden">早报</span>
            </span>
            <span className="text-3xs text-wb-text-muted font-mono shrink-0 hidden sm:inline">
              {newsPost?.created_at ? formatEast8Date(newsPost.created_at) : '今日'}
            </span>
            <p
              onClick={() => newsPost && onSelectPost(newsPost.id)}
              className="text-xs text-wb-text-primary font-medium truncate cursor-pointer hover:underline flex-1 min-w-0"
            >
              {newsPost?.title || '【极客早报】前沿技术架构与开源交流动态，点击查看完整晨报'}
            </p>
          </div>
          <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
            {isAdmin && onGoWriteDaily && (
              <button
                type="button"
                onClick={onGoWriteDaily}
                className="text-3xs text-blue-500 hover:underline cursor-pointer"
              >
                写新早报
              </button>
            )}
            <button
              type="button"
              onClick={() => newsPost && onSelectPost(newsPost.id)}
              className="text-3xs text-wb-text-muted hover:text-wb-text-primary flex items-center gap-0.5 shrink-0 select-none cursor-pointer"
            >
              <span>立即阅读</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* 移动端专享：水平横滑技术分区胶囊栏 */}
        <div className="md:hidden relative -mx-2 px-2 py-0.5">
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5 select-none">
            {categories.map((cat) => {
              const isActive = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setSelectedCategory(cat)}
                  className={`h-7 px-3 rounded-lg text-xs font-medium whitespace-nowrap shrink-0 transition-all cursor-pointer ${
                    isActive
                      ? 'bg-wb-text-primary text-wb-bg-page font-bold shadow-xs'
                      : 'bg-wb-bg-surface text-wb-text-secondary border border-wb-border-subtle active:bg-wb-bg-subtle'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>
        </div>

        {/* 专业版一体化多维控制栏 */}
        <div className="p-2.5 sm:p-3 rounded-xl bg-wb-bg-surface border border-wb-border-subtle shadow-2xs select-none space-y-2 sm:space-y-0 sm:flex sm:items-center sm:justify-between sm:gap-2.5">
          {/* 流切换 */}
          <div className="flex items-center justify-between gap-2 w-full sm:w-auto">
            <div className="relative flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle select-none">
              <button
                type="button"
                onClick={() => setStreamTab('all')}
                className={`relative z-10 h-7 px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                  streamTab === 'all'
                    ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs'
                    : 'text-wb-text-muted hover:text-wb-text-secondary'
                }`}
              >
                全部发现
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!user) onRequireAuth?.();
                  else setStreamTab('following');
                }}
                className={`relative z-10 h-7 px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                  streamTab === 'following'
                    ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs'
                    : 'text-wb-text-muted hover:text-wb-text-secondary'
                }`}
              >
                关注动态
              </button>
            </div>

            {/* 移动端视图切换 */}
            <div className="sm:hidden flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle text-3xs font-medium">
              <button
                type="button"
                onClick={() => toggleFeedViewMode('card')}
                className={`px-2 py-1 rounded-md transition-colors ${
                  feedViewMode === 'card'
                    ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs font-semibold'
                    : 'text-wb-text-muted'
                }`}
              >
                卡片
              </button>
              <button
                type="button"
                onClick={() => toggleFeedViewMode('dense')}
                className={`px-2 py-1 rounded-md transition-colors ${
                  feedViewMode === 'dense'
                    ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs font-semibold'
                    : 'text-wb-text-muted'
                }`}
              >
                摸鱼流
              </button>
            </div>
          </div>

          {/* 排序分段控件与视图模式 */}
          <div className="flex items-center justify-between gap-2 pt-0.5 sm:pt-0 w-full sm:w-auto">
            <div className="flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle select-none flex-1 sm:flex-initial">
              {(
                [
                  { key: 'latest', label: '最新发布' },
                  { key: 'hot', label: '热门互动' },
                  { key: 'discuss', label: '热议讨论' },
                ] as const
              ).map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setFilter(tab.key)}
                  className={`h-7 px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors cursor-pointer flex-1 sm:flex-initial text-center ${
                    filter === tab.key
                      ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs'
                      : 'text-wb-text-muted hover:text-wb-text-secondary'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* 桌面端摸鱼流切换与老板键 */}
            <div className="hidden sm:flex items-center gap-2">
              <div className="flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle text-3xs font-medium">
                <button
                  type="button"
                  onClick={() => toggleFeedViewMode('card')}
                  className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-colors ${
                    feedViewMode === 'card'
                      ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs font-semibold'
                      : 'text-wb-text-muted'
                  }`}
                  title="舒适卡片流"
                >
                  <Layout className="w-3 h-3" />
                  <span>卡片</span>
                </button>
                <button
                  type="button"
                  onClick={() => toggleFeedViewMode('dense')}
                  className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-colors ${
                    feedViewMode === 'dense'
                      ? 'text-wb-text-primary bg-wb-bg-surface shadow-xs font-semibold'
                      : 'text-wb-text-muted'
                  }`}
                  title="高密度纯文本摸鱼流"
                >
                  <List className="w-3 h-3" />
                  <span>摸鱼流</span>
                </button>
              </div>

              <button
                type="button"
                onClick={() => setBossMode(true)}
                className="flex items-center gap-1 h-7 px-2 rounded-lg bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-wb-text-muted hover:text-emerald-500 transition-colors text-3xs font-mono cursor-pointer"
                title="一键伪装代码控制台 (快捷键: Alt+B)"
              >
                <Terminal className="w-3 h-3 text-emerald-500" />
                <span>伪装 (Alt+B)</span>
              </button>
            </div>
          </div>
        </div>

        {/* 3. 信息流列表展示 */}
        <div
          className={`space-y-3 min-h-[480px] transition-opacity duration-150 ${
            isFetchingPosts ? 'opacity-65 pointer-events-none' : 'opacity-100'
          }`}
        >
          {posts.length > 0 ? (
            <>
              {posts.map((post, postIdx) =>
                feedViewMode === 'dense' ? (
                  /* 极客纯文本单行摸鱼流 (Dense Row) */
                  <article
                    key={post.id}
                    onClick={() => onSelectPost(post.id)}
                    className="w-full bg-wb-bg-surface hover:bg-wb-bg-subtle/80 border border-wb-border-subtle hover:border-wb-border-strong px-3 sm:px-4 py-2 sm:py-2.5 rounded-lg transition-all cursor-pointer group flex items-center justify-between gap-3 text-xs select-none"
                  >
                    <div className="flex items-center gap-2 min-w-0 flex-1">
                      <span className="text-3xs text-wb-text-muted font-mono w-5 shrink-0 text-right">
                        {postIdx + 1}.
                      </span>
                      {post.is_pinned && (
                        <span className="inline-flex items-center gap-0.5 text-3xs px-1.5 py-0.2 rounded bg-red-50 dark:bg-red-950/40 text-red-600 dark:text-red-400 font-semibold shrink-0">
                          <Pin className="w-2.5 h-2.5" />
                          <span>置顶</span>
                        </span>
                      )}
                      <span className="text-3xs px-1.5 py-0.2 rounded bg-wb-bg-subtle text-wb-text-muted font-mono shrink-0 hidden sm:inline">
                        {post.category_name || '综合'}
                      </span>
                      <h3 className="font-semibold text-wb-text-primary group-hover:text-wb-brand truncate group-hover:underline text-xs">
                        {getSmartDailyTitle(post.title, post.id)}
                      </h3>
                    </div>
                    <div className="flex items-center gap-3 shrink-0 text-3xs text-wb-text-muted font-mono">
                      <span className="hidden md:inline">
                        @{post.author_nickname || post.author_username}
                      </span>
                      <span className="hidden sm:inline">{formatEast8Date(post.created_at)}</span>
                      <span className="text-wb-text-secondary flex items-center gap-1">
                        <Eye className="w-3 h-3 text-wb-text-muted" />
                        <span>{post.view_count || 0}</span>
                      </span>
                      <span className="text-wb-text-secondary flex items-center gap-1">
                        <MessageSquare className="w-3 h-3 text-wb-text-muted" />
                        <span>{post.comment_count || 0}</span>
                      </span>
                    </div>
                  </article>
                ) : (
                  /* 专业卡片流 (Pro Card) */
                  <article
                    key={post.id}
                    onClick={() => onSelectPost(post.id)}
                    className="w-full bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3.5 sm:p-4 shadow-2xs hover:border-wb-border-strong transition-all cursor-pointer group space-y-2"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-1.5 flex-1 min-w-0">
                        {/* 作者元信息 */}
                        <div className="flex items-center gap-2 text-3xs text-wb-text-muted font-mono">
                          <UserAvatar
                            avatarUrl={post.author_avatar}
                            username={post.author_nickname || post.author_username}
                            size="xs"
                          />
                          <span className="text-wb-text-primary font-medium truncate">
                            {post.author_nickname || post.author_username}
                          </span>
                          {post.author_level ? <LevelBadge level={post.author_level} /> : null}
                          <span>·</span>
                          <span>{formatEast8Date(post.created_at)}</span>
                          {post.is_pinned && (
                            <span className="text-red-500 font-sans font-bold flex items-center gap-0.5">
                              <Pin className="w-3 h-3" /> 置顶
                            </span>
                          )}
                        </div>

                        {/* 标题 */}
                        <h2 className="text-sm sm:text-base font-bold text-wb-text-primary group-hover:text-blue-500 transition-colors line-clamp-2">
                          {getSmartDailyTitle(post.title, post.id)}
                        </h2>

                        {/* 摘要正文 */}
                        {post.summary && (
                          <p className="text-xs text-wb-text-secondary line-clamp-2 leading-relaxed">
                            {post.summary}
                          </p>
                        )}
                      </div>

                      {/* 封面缩略图 */}
                      {post.cover_image && (
                        <div className="w-20 h-16 sm:w-28 sm:h-20 rounded-lg overflow-hidden shrink-0 border border-wb-border-subtle bg-wb-bg-subtle">
                          <img
                            src={thumbUrl(post.cover_image, 200)}
                            srcSet={thumbSrcSet(post.cover_image)}
                            alt=""
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                            loading="lazy"
                          />
                        </div>
                      )}
                    </div>

                    {/* 卡片底部操作栏 */}
                    <div className="flex items-center justify-between pt-2 border-t border-wb-border-subtle/40 text-3xs text-wb-text-muted">
                      <div className="flex items-center gap-2">
                        <span className="font-mono flex items-center gap-1">
                          <Eye className="w-3 h-3" />
                          <span>{post.view_count || 0}</span>
                        </span>
                        {post.category_name && (
                          <span className="px-1.5 py-0.2 rounded bg-wb-bg-subtle text-wb-text-muted font-mono">
                            {post.category_name}
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={(e) => handleToggleCollect(e, post)}
                          title={post.is_collected ? '已收藏' : '收藏'}
                          className={`p-1 rounded transition-colors ${
                            post.is_collected
                              ? 'text-amber-500'
                              : 'hover:text-wb-text-primary hover:bg-wb-bg-subtle'
                          }`}
                        >
                          <Bookmark
                            className={`w-3.5 h-3.5 ${post.is_collected ? 'fill-current' : ''}`}
                          />
                        </button>
                      </div>

                      <div className="flex items-center gap-3" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={(e) => handleQuickLike(e, post)}
                          title="点赞"
                          className="flex items-center gap-1 hover:text-red-500 transition-colors"
                        >
                          <Heart className="w-3.5 h-3.5" />
                          <span className="font-mono">{post.like_count || 0}</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => onSelectPost(post.id)}
                          title="评论"
                          className="flex items-center gap-1 hover:text-blue-500 transition-colors"
                        >
                          <MessageSquare className="w-3.5 h-3.5" />
                          <span className="font-mono">{post.comment_count || 0}</span>
                        </button>
                        <button
                          type="button"
                          onClick={(e) => handleShare(e, post)}
                          title="分享链接"
                          className="p-1 hover:text-wb-text-primary hover:bg-wb-bg-subtle rounded transition-colors"
                        >
                          <Share2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </article>
                )
              )}

              {/* 摸鱼流数据边界 */}
              {feedViewMode === 'dense' && (
                <div className="py-3 text-center text-3xs font-mono text-wb-text-muted flex items-center justify-center gap-2 select-none">
                  <span className="w-10 h-px bg-wb-border-subtle" />
                  <span>EOF :: 摸鱼流已载入全部条目 ({posts.length} 篇)</span>
                  <span className="w-10 h-px bg-wb-border-subtle" />
                </div>
              )}

              {/* 滚动哨兵 */}
              <div ref={bottomSentinelRef} className="h-4" />
              {loadingMore && (
                <div className="py-4 text-center text-xs text-wb-text-muted font-mono animate-pulse">
                  正在同步更多极客帖子...
                </div>
              )}
              {!hasMore && posts.length > 0 && (
                <div className="py-6 text-center text-xs text-wb-text-muted font-mono border-t border-wb-border-subtle/50 flex items-center justify-center gap-1.5">
                  <Check className="w-3.5 h-3.5 text-wb-success" />
                  <span>已展现全部极客内容 · 共 {displayTotal} 篇</span>
                </div>
              )}
            </>
          ) : isFetchingPosts ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((n) => (
                <PostCardSkeleton key={n} />
              ))}
            </div>
          ) : (
            <div className="p-12 rounded-xl bg-wb-bg-surface border border-wb-border-subtle text-center space-y-2">
              <FileText className="w-8 h-8 text-wb-text-muted mx-auto stroke-1" />
              <p className="text-xs text-wb-text-muted">当前分区暂无帖子，快来抢先发布吧</p>
            </div>
          )}
        </div>
      </div>

      {/* 3. 右侧极客专属仪表盘 (桌面端隐藏/大屏 sticky top-16) */}
      <aside className="hidden xl:block w-72 shrink-0 space-y-3.5 select-none sticky top-16">
        {/* 个人身份卡片 */}
        {user ? (
          <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3.5 shadow-2xs space-y-3">
            <div className="flex items-center gap-3">
              <UserAvatar avatarUrl={user.avatar_url} username={user.username} size="md" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-bold text-wb-text-primary truncate">
                    {user.nickname || user.username}
                  </span>
                  <LevelBadge level={user.level || 1} />
                </div>
                <p className="text-3xs text-wb-text-muted truncate mt-0.5">
                  {user.bio || '极客探索者 · 终身学习'}
                </p>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-1 pt-2 border-t border-wb-border-subtle/60 text-center text-3xs font-mono">
              <div className="p-1.5 rounded-lg bg-wb-bg-subtle/40">
                <div className="text-wb-text-primary font-bold">{user.experience || 0}</div>
                <div className="text-wb-text-muted text-[9px]">经验值</div>
              </div>
              <div className="p-1.5 rounded-lg bg-wb-bg-subtle/40">
                <div className="text-wb-text-primary font-bold">{user.following_count || 0}</div>
                <div className="text-wb-text-muted text-[9px]">关注</div>
              </div>
              <div className="p-1.5 rounded-lg bg-wb-bg-subtle/40">
                <div className="text-wb-text-primary font-bold">{user.followers_count || 0}</div>
                <div className="text-wb-text-muted text-[9px]">粉丝</div>
              </div>
            </div>
          </div>
        ) : (
          <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-4 shadow-2xs text-center space-y-2.5">
            <h3 className="text-xs font-bold text-wb-text-primary">加入深度极客社区</h3>
            <p className="text-3xs text-wb-text-muted leading-relaxed">
              登录通行证，与海量全栈工程师交流技术架构，记录技术日常。
            </p>
            <button
              type="button"
              onClick={onRequireAuth}
              className="w-full py-1.5 rounded-lg bg-wb-text-primary text-wb-bg-page text-xs font-semibold hover:opacity-90 transition-opacity"
            >
              立即登录 / 注册
            </button>
          </div>
        )}

        {/* 社区活跃排行榜 */}
        <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 shadow-2xs">
          <Leaderboard onSelectUser={onSelectUser} />
        </div>
      </aside>

      {/* 4. 极客摸鱼老板键伪装层 (Alt+B 随时呼出) */}
      {bossMode && (
        <div
          onClick={() => setBossMode(false)}
          className="fixed inset-0 z-[9999] bg-[#0c0d10] text-[#00ff66] font-mono p-6 sm:p-12 overflow-hidden flex flex-col justify-between cursor-pointer select-none"
          title="点击任意处或按 Esc / Alt+B 恢复论坛"
        >
          <div className="space-y-2 text-xs sm:text-sm">
            <div className="text-zinc-500 pb-3 flex items-center justify-between border-b border-zinc-800">
              <span className="font-bold tracking-wider">DEV CONSOLE :: CLUSTER REPL DAEMON (PRODUCTION)</span>
              <span className="text-emerald-400 flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                LIVE METRICS
              </span>
            </div>
            <p className="text-zinc-400 pt-2">&gt; pnpm --filter @core/engine build:prod</p>
            <p className="text-zinc-500">
              [vite] transforming (2728/2869 modules) [========================================] 100%
            </p>
            <p className="text-emerald-400">✓ 2728 modules transformed in 1.48s</p>
            <p className="text-zinc-400">&gt; docker compose up -d postgres redis api-cluster</p>
            <p className="text-zinc-500">Container api-cluster-1 Running [healthy]</p>
            <p className="text-zinc-500">Worker thread [0x7ffeef89a0] listening on 0.0.0.0:8000</p>
            <p className="text-emerald-300">
              [2026-11-14 23:30:00] INFO: Memory pool: 412MB / 2048MB, Latency: 1.2ms (OK)
            </p>
          </div>
          <div className="pt-4 border-t border-zinc-800 flex items-center justify-between text-zinc-500 text-xs">
            <span>TERMINAL RUNNING...</span>
            <span className="text-emerald-500">按 ESC 或 ALT+B 退出伪装</span>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProHome;
