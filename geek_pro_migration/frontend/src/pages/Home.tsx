import React, { useEffect, useLayoutEffect, useState, useMemo, useRef } from 'react';
import { motion } from 'framer-motion';
import {
  FileText,
  UserRound,
  Clock,
  MessageSquare,
  Heart,
  Eye,
  Sparkles,
  Check,
  Pin,
  ChevronRight,
  Search,
  X,
  Bookmark,
  Share2,
  RotateCcw,
  Activity,
  Users,
  UserCheck,
  UserPlus,
  Shield,
  Calendar,
  Edit3,
  Flame,
  Terminal,
  ArrowUpRight,
  History,
  Trash2,
  ExternalLink,
  ArrowDownCircle,
  BookOpen,
  FolderPlus,
  Smile,
  Flag,
  Layout,
  LayoutGrid,
  List,
} from 'lucide-react';
import { useUiMode } from '../utils/uiMode';
import { DailyNewsBlock } from '../components/DailyNewsBlock';
import { apiClient } from '../api/client';
import { Leaderboard } from '../components/Leaderboard';
import { StaffDirectory } from '../components/StaffDirectory';
import { thumbUrl, thumbSrcSet } from '../utils/thumb';
import type { PostSimple, UserProfile } from '../types';
import { LevelBadge } from '../components/LevelBadge';
import { SegmentedControl } from '../components/SegmentedControl';
import { UserAvatar } from '../components/UserAvatar';
import { PostCardSkeleton } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import { FriendLinksCard } from '../components/FriendLinksCard';
import { formatEast8Date, getSmartDailyTitle } from '../utils/format';
import { bumpEgg, reportEggOnce } from '../utils/eggs';
import { triggerCrazyThursday } from '../utils/crazyThursday';
import { SeriesPanel } from '../components/SeriesPanel';
import { openCollectionModal, onCollectionChanged } from '../utils/collection';

const DEMO_NEWS: PostSimple = {
  id: 'demo-news-1',
  title: '【极客早报·11-13】黄仁勋正式辞任英伟达CEO，CFO临时接棒；谷歌连发Gemini 4 Hydrogen免费开放；SpaceX星舰第7次试飞成功完成受控软着陆...',
  post_type: 'news',
  status: 'published',
  is_pinned: true,
  view_count: 1024,
  like_count: 88,
  comment_count: 32,
  reading_time_minutes: 3,
  created_at: '2026-11-13T08:00:00Z',
  author_username: 'geek_news',
  author_nickname: '每日早报·置顶要闻',
  author_avatar: '',
  author_level: 8,
  summary: '1、黄仁勋正式辞任英伟达CEO，CFO临时接棒；2、谷歌连发Gemini 4 Hydrogen免费开放；3、SpaceX星舰第7次试飞成功完成受控软着陆。',
};

const DEMO_POSTS: PostSimple[] = [
  {
    id: 'demo-post-1',
    title: '本站声明',
    post_type: 'post',
    status: 'published',
    is_pinned: true,
    view_count: 6,
    like_count: 1,
    comment_count: 2,
    reading_time_minutes: 2,
    created_at: '2026-11-13T10:00:00Z',
    author_username: 'half_night',
    author_nickname: '半夜悠网站',
    author_avatar: '',
    author_level: 6,
    category_name: '综合交流',
    summary: '极客社区声明：崇尚代码与理性，探索前沿技术与纯粹美学。坚决抵制网络戾气与人身攻击，守护开源交流净土与理性探讨氛围。',
    is_collected: false,
    tags: ['公告', '社区规范'],
  },
  {
    id: 'demo-post-2',
    title: '小站免责与声明',
    post_type: 'post',
    status: 'published',
    is_pinned: true,
    is_essence: true,
    view_count: 216,
    like_count: 28,
    comment_count: 13,
    reading_time_minutes: 9,
    created_at: '2026-11-09T08:30:00Z',
    author_username: 'half_night',
    author_nickname: '半夜悠网站',
    author_avatar: '',
    author_level: 6,
    category_name: '科技前沿',
    summary: '本站所有技术分享、源码与架构探讨均属于技术交流与学术探索范畴，请大家在合规前提下共同打造高效、专业的极客学习家园。',
    is_collected: false,
    tags: ['免责声明', '开源协议'],
  },
  {
    id: 'demo-post-3',
    title: '示例帖子标题，可在源码中替换为实际内容',
    post_type: 'post',
    status: 'published',
    is_pinned: true,
    view_count: 42,
    like_count: 9,
    comment_count: 3,
    reading_time_minutes: 15,
    created_at: '2026-11-06T14:20:00Z',
    author_username: 'yuefu_sakura',
    author_nickname: '月赴樱花树下',
    author_avatar: '',
    author_level: 3,
    category_name: '极客日常',
    summary: '这是一篇用于全景预览专业版排版、正文摘要与交互设计的测试文章。正文预览直接展示关键内容，提升信息量与学习专业度。',
    is_collected: false,
    tags: ['日常思考', '技术探索'],
  },
  {
    id: 'demo-post-4',
    title: '深入浅出：基于 React 19 与 TypeScript 打造高质感社区体验',
    post_type: 'post',
    status: 'published',
    is_pinned: false,
    is_essence: true,
    view_count: 512,
    like_count: 47,
    comment_count: 19,
    reading_time_minutes: 8,
    created_at: '2026-11-04T16:00:00Z',
    author_username: 'xiyan',
    author_nickname: '汐爻',
    author_avatar: '',
    author_level: 6,
    category_name: '科技前沿',
    summary: '通过深入剖析现代前端响应式范式、代码拆分（Code Splitting）以及流式服务端状态管理，探讨如何在不牺牲灵活性的同时保持代码整洁。',
    is_collected: true,
    tags: ['React 19', 'TypeScript', 'UI/UX'],
  },
];

const DEFAULT_CATEGORIES = ['全部', '科技前沿', '后端架构', '设计美学', '极客日常', '综合交流', '好文分享', '问答广场'];

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * 搜索状态与地址栏的对应关系：`?q=<关键词>`，用户搜索时额外带 `&stype=users`。
 * 回车搜索必须真实改变 URL（可分享、可刷新、可前进后退），否则搜索结果页没有可寻址的落地页。
 */
type SearchTargetType = 'posts' | 'users';

const readSearchQueryFromUrl = (): { q: string; target: SearchTargetType } => {
  try {
    const params = new URLSearchParams(window.location.search);
    return {
      q: params.get('q') || '',
      target: params.get('stype') === 'users' ? 'users' : 'posts',
    };
  } catch {
    return { q: '', target: 'posts' };
  }
};

/** push 用于「回车搜索」以留下可回退的历史；replace 用于清空等不应污染历史的场景 */
const writeSearchQueryToUrl = (q: string, target: SearchTargetType, replace = false) => {
  try {
    const url = new URL(window.location.href);
    const keyword = q.trim();
    if (keyword) {
      url.searchParams.set('q', keyword);
      if (target === 'users') url.searchParams.set('stype', 'users');
      else url.searchParams.delete('stype');
    } else {
      url.searchParams.delete('q');
      url.searchParams.delete('stype');
    }
    if (url.toString() === window.location.href) return;
    if (replace) window.history.replaceState({ q: keyword }, '', url.toString());
    else window.history.pushState({ q: keyword }, '', url.toString());
  } catch {
    /* 地址栏不可用时静默忽略，搜索本身仍然可用 */
  }
};

/** 搜索结果关键词高亮：大小写不敏感，仅渲染命中片段 */
const HighlightText: React.FC<{ text: string; query: string }> = ({ text, query }) => {
  const keyword = query.trim();
  if (!keyword) return <>{text}</>;
  const parts = text.split(new RegExp(`(${escapeRegExp(keyword)})`, 'ig'));
  return (
    <>
      {parts.map((part, index) =>
        part.toLowerCase() === keyword.toLowerCase() ? (
          <mark key={index} className="bg-wb-warn/45 text-inherit rounded px-0.5">
            {part}
          </mark>
        ) : (
          <React.Fragment key={index}>{part}</React.Fragment>
        )
      )}
    </>
  );
};

interface SearchedUserItem {
  id: string;
  username: string;
  nickname?: string | null;
  avatar_url?: string | null;
  bio?: string;
  level: number;
  role: string;
  followers_count: number;
  following_count: number;
  is_following: boolean;
  /** 命中来源：username / nickname / bio */
  match_type?: string;
}

interface TelemetryData {
  total_posts: number;
  total_users: number;
  online_users: number;
  top_hot_posts: { id: string; title: string; comment_count: number; like_count: number }[];
}

interface PublicStats {
  total_posts: number;
  total_members: number;
  popular_tags: string[];
  top_hot_posts: { id: string; title: string; comment_count: number; like_count: number }[];
  top_contributors: {
    username: string;
    nickname?: string | null;
    avatar_url: string;
    level: number;
    role: string;
    experience: number;
  }[];
}

interface Props {
  onSelectPost: (postId: string) => void;
  user?: UserProfile | null;
  onRequireAuth?: () => void;
  onGoWrite?: () => void;
  onGoWriteDaily?: () => void;
  onGoAdmin?: () => void;
  onRefreshUser?: () => void;
  onSelectUser?: (username: string) => void;
  onGoRules?: () => void;
  onGoLegal?: () => void;
  /** 打开友情链接独立页 */
  onOpenLinks?: () => void;
  /** 首页当前是否处于可见状态（App 用 display:none 常驻挂载，切回时需重拉信息流） */
  isActive?: boolean;
}

const generatePaginationPages = (current: number, total: number): (number | string)[] => {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  if (current <= 4) {
    return [1, 2, 3, 4, 5, '...', total];
  }
  if (current >= total - 3) {
    return [1, '...', total - 4, total - 3, total - 2, total - 1, total];
  }
  return [1, '...', current - 1, current, current + 1, '...', total];
};

export const Home: React.FC<Props> = ({
  onSelectPost,
  user,
  onRequireAuth,
  onGoWrite,
  onGoWriteDaily,
  onGoAdmin,
  onRefreshUser,
  onSelectUser,
  onGoRules,
  onGoLegal,
  onOpenLinks,
  isActive = true,
}) => {
  const toast = useToast();
  const [uiMode] = useUiMode();
  const [activeReactionPostId, setActiveReactionPostId] = useState<string | null>(null);

  // 快捷点赞
  const handleQuickLike = async (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    if (!user) {
      onRequireAuth?.();
      return;
    }
    try {
      const res = await apiClient.post(`/posts/${post.id}/like`);
      const newLiked = res.data?.is_liked;
      const diff = newLiked ? 1 : -1;
      const updateList = (list: PostSimple[]) =>
        list.map((p) => (p.id === post.id ? { ...p, like_count: Math.max(0, p.like_count + diff) } : p));
      setPosts(updateList);
      setFollowingPosts(updateList);
      toast.success(newLiked ? '已点赞' : '已取消点赞');
    } catch (err: any) {
      toast.error(err.response?.data?.detail || '点赞操作失败');
    }
  };

  // 快捷举报
  const handleReportPost = async (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    if (!user) {
      onRequireAuth?.();
      return;
    }
    const reason = window.prompt('请输入举报该帖子的违规原因：');
    if (!reason || !reason.trim()) return;
    try {
      await apiClient.post('/feedbacks', {
        content: `【举报帖子】ID: ${post.id} 标题: ${post.title}。原因: ${reason.trim()}`,
        feedback_type: 'report',
      });
      toast.success('举报已提交，社区管理员将尽快核实处理');
    } catch {
      toast.success('举报已记录，感谢您的监督');
    }
  };

  // 快捷表情互动
  const handleQuickReaction = async (e: React.MouseEvent, post: PostSimple, emoji: string) => {
    e.stopPropagation();
    setActiveReactionPostId(null);
    if (!user) {
      onRequireAuth?.();
      return;
    }
    try {
      await apiClient.post(`/posts/${post.id}/reactions`, { emoji });
      toast.success(`已互动表情 ${emoji}`);
    } catch {
      toast.success(`已互动表情 ${emoji}`);
    }
  };

  // 监听全局搜索触发事件
  useEffect(() => {
    const onTriggerSearch = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail?.query) {
        setSearchQuery(detail.query);
        if (detail.target) setSearchTarget(detail.target);
        setCurrentPage(1);
        setPostsPage(1);
      }
    };
    window.addEventListener('geek:trigger-search', onTriggerSearch);
    return () => window.removeEventListener('geek:trigger-search', onTriggerSearch);
  }, []);

  const [posts, setPosts] = useState<PostSimple[]>([]);
  const [loadingPosts, setLoadingPosts] = useState(true);
  const [isFetchingPosts, setIsFetchingPosts] = useState(false);

  // 首页浏览偏好 (无限滚动 vs 普通传统分页，仿照 linux.sb)
  const [infiniteScroll, setInfiniteScroll] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem('geek_home_infinite_scroll');
      // 默认分页：一页 10 条，DOM 数量固定，滚动更稳，也更符合「翻页浏览」的习惯
      return saved !== null ? saved === 'true' : false;
    } catch {
      return false;
    }
  });

  const [currentPage, setCurrentPage] = useState<number>(1);
  const [jumpPageInput, setJumpPageInput] = useState<string>('');
  const PAGE_SIZE = 10;
  const bottomSentinelRef = useRef<HTMLDivElement | null>(null);
  const feedTopRef = useRef<HTMLDivElement | null>(null);
  // 请求序号：快速切换排序/搜索/筛选时，丢弃先发后到的过期响应，避免列表串数据
  const postsReqIdRef = useRef(0);
  const [followingPosts, setFollowingPosts] = useState<PostSimple[]>([]);
  const [streamTab, setStreamTab] = useState<'all' | 'following'>('all');
  const [loadingFollowing, setLoadingFollowing] = useState(false);
  // 服务端分页游标：一次拉一页（100 条），滚动/翻页到底再向后取，突破"只有前 100 篇"的上限
  const [postsPage, setPostsPage] = useState<number>(1);
  // 服务端返回的全站命中总数（搜索态下用于「共 N 篇」与分页）
  const [totalPosts, setTotalPosts] = useState<number>(0);
  const [hasMorePosts, setHasMorePosts] = useState<boolean>(false);
  const [loadingMorePosts, setLoadingMorePosts] = useState<boolean>(false);
  const [newsPost, setNewsPost] = useState<PostSimple | null>(null);
  const [filter, setFilter] = useState<'latest' | 'hot' | 'comments'>('latest');
  const [selectedCategory, setSelectedCategory] = useState<string>('全部');
  const [categories, setCategories] = useState<string[]>(DEFAULT_CATEGORIES);
  // 分类 id → 名称，用于在信息流卡片上显示分类锚点
  const [categoryMap, setCategoryMap] = useState<Record<number, string>>({});

  // 极客摸鱼视图模式：'card'（紧凑卡片） vs 'dense'（高密度纯文本摸鱼流）
  const [feedViewMode, setFeedViewMode] = useState<'card' | 'dense'>(() => {
    try {
      return (localStorage.getItem('geek_feed_view_mode') as 'card' | 'dense') || 'card';
    } catch {
      return 'card';
    }
  });

  const toggleFeedViewMode = (mode: 'card' | 'dense') => {
    setFeedViewMode(mode);
    try {
      localStorage.setItem('geek_feed_view_mode', mode);
    } catch {}
  };

  // 极客老板键伪装模式 (Boss Key: Alt+B)
  const [bossMode, setBossMode] = useState<boolean>(false);

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

  useEffect(() => {
    apiClient.get('/posts/categories').then((res) => {
      if (Array.isArray(res.data) && res.data.length > 0) {
        const catNames = res.data
          .filter((c: any) => c.slug !== 'news' && c.name !== '新闻早报')
          .map((c: any) => c.name);
        const finalCats = Array.from(new Set(['全部', ...catNames, '综合交流']));
        setCategories(finalCats);
        const map: Record<number, string> = {};
        res.data.forEach((c: any) => {
          if (c && typeof c.id === 'number' && c.name) map[c.id] = c.name;
        });
        setCategoryMap(map);
      }
    }).catch(() => {});
  }, []);
  // 首屏即从地址栏恢复搜索词，保证 ?q=xxx 的链接打开就是结果页（刷新/分享/直达都成立）
  const [searchQuery, setSearchQuery] = useState<string>(() => readSearchQueryFromUrl().q);
  // 输入联想：文章 / 用户（防抖 220ms 请求 /search/suggest）
  const [suggest, setSuggest] = useState<{ posts: { id: string; title: string }[]; users: { username: string; nickname: string }[] }>({
    posts: [],
    users: [],
  });
  const [searchTarget, setSearchTarget] = useState<SearchTargetType>(() => readSearchQueryFromUrl().target);
  // 文章/极客切换：只做水平位移的实测胶囊，避免 layout 投影带来的上下抖动
  const postsTargetRef = useRef<HTMLButtonElement | null>(null);
  const usersTargetRef = useRef<HTMLButtonElement | null>(null);
  const [targetPill, setTargetPill] = useState<{ x: number; width: number }>({ x: 0, width: 0 });

  const measureTargetPill = React.useCallback(() => {
    const el = searchTarget === 'posts' ? postsTargetRef.current : usersTargetRef.current;
    if (!el) return;
    const next = { x: el.offsetLeft, width: el.offsetWidth };
    setTargetPill((prev) =>
      Math.abs(prev.x - next.x) < 0.5 && Math.abs(prev.width - next.width) < 0.5 ? prev : next
    );
  }, [searchTarget]);

  useLayoutEffect(() => {
    measureTargetPill();
  }, [measureTargetPill]);

  useEffect(() => {
    const handle = () => measureTargetPill();
    window.addEventListener('resize', handle);
    if (document.fonts?.ready) {
      document.fonts.ready.then(handle).catch(() => undefined);
    }
    return () => window.removeEventListener('resize', handle);
  }, [measureTargetPill]);
  const [searchSort, setSearchSort] = useState<'relevance' | 'latest' | 'hot'>('relevance');
  const [timeRange, setTimeRange] = useState<'all' | 'week' | 'month'>('all');
  // 按作者筛选（服务端 User.username 精确匹配）
  const [searchAuthor, setSearchAuthor] = useState<string>('');
  const [searchedUsers, setSearchedUsers] = useState<SearchedUserItem[]>([]);
  const [searchingUsers, setSearchingUsers] = useState(false);
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkedInLocally, setCheckedInLocally] = useState(false);

  const hasCheckedInToday = Boolean(user?.has_checked_in_today || checkedInLocally);

  // 搜索容器引用与聚焦浮层状态
  const searchContainerRef = useRef<HTMLDivElement | null>(null);
  const [isSearchFocused, setIsSearchFocused] = useState(false);

  // 真实搜索历史 (初始为空数组，支持单项删除与清空)
  const [searchHistory, setSearchHistory] = useState<string[]>(() => {
    try {
      const s = localStorage.getItem('geek_search_history');
      return s ? JSON.parse(s) : [];
    } catch {
      return [];
    }
  });

  const addSearchHistory = (term: string) => {
    if (!term.trim()) return;
    const clean = term.trim();
    setSearchHistory((prev) => {
      const next = [clean, ...prev.filter((s) => s !== clean)].slice(0, 8);
      localStorage.setItem('geek_search_history', JSON.stringify(next));
      return next;
    });
  };

  const removeSearchHistory = (term: string) => {
    setSearchHistory((prev) => {
      const next = prev.filter((s) => s !== term);
      localStorage.setItem('geek_search_history', JSON.stringify(next));
      return next;
    });
  };

  const clearSearchHistory = () => {
    setSearchHistory([]);
    localStorage.removeItem('geek_search_history');
    toast.info('搜索历史已清空');
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchContainerRef.current && !searchContainerRef.current.contains(event.target as Node)) {
        setIsSearchFocused(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // 浏览器前进/后退时，把搜索框与地址栏里的 ?q= 重新对齐
  useEffect(() => {
    const handlePopState = () => {
      const { q, target } = readSearchQueryFromUrl();
      setSearchQuery(q);
      setSearchTarget(target);
      setCurrentPage(1);
      setPostsPage(1);
      setIsSearchFocused(false);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const isAdmin = ['super_admin', 'admin', 'moderator', 'auditor'].includes(user?.role || '');
  const isSuperAdmin = ['super_admin', 'admin'].includes(user?.role || '');

  const [telemetry, setTelemetry] = useState<TelemetryData>({
    total_posts: 0,
    total_users: 0,
    online_users: 0,
    top_hot_posts: [],
  });

  const [publicStats, setPublicStats] = useState<PublicStats>({
    total_posts: 0,
    total_members: 0,
    popular_tags: [],
    top_hot_posts: [],
    top_contributors: [],
  });

  const showToast = (msg: string) => {
    toast.info(msg);
  };

  const fetchStats = async () => {
    if (isSuperAdmin) {
      try {
        const res = await apiClient.get('/posts/stats/telemetry');
        if (res.data && res.data.total_posts !== undefined) {
          setTelemetry(res.data);
        }
      } catch {}
    } else {
      try {
        const res = await apiClient.get('/posts/stats/public');
        if (res.data && res.data.popular_tags) {
          setPublicStats(res.data);
        }
      } catch {}
    }
  };

  const handleCheckin = async () => {
    if (!user) {
      onRequireAuth?.();
      return;
    }
    if (hasCheckedInToday) {
      showToast('今日已完成签到打卡');
      return;
    }
    setCheckingIn(true);
    try {
      const res = await apiClient.post('/users/checkin');
      setCheckedInLocally(true);
      showToast(`打卡成功！获得 +${res.data.exp_gained} 经验 (连续 ${res.data.continuous_days} 天)`);
      onRefreshUser?.();
    } catch (err: any) {
      if (err.response?.data?.detail?.includes('今日已经完成签到') || err.response?.data?.detail?.includes('已完成签到')) {
        setCheckedInLocally(true);
      }
      showToast(err.response?.data?.detail || '今日已完成签到');
    } finally {
      setCheckingIn(false);
    }
  };

  // 置顶日报：独立请求「最新一期」，不受列表的 最新/热议/分类筛选影响
  const fetchLatestNews = async () => {
    try {
      const res = await apiClient.get('/posts/latest-news');
      setNewsPost(res.data || DEMO_NEWS);
    } catch {
      setNewsPost(DEMO_NEWS);
    }
  };

  const FEED_PAGE_SIZE = 100;

  const fetchPosts = async (page: number = 1, append: boolean = false) => {
      if (searchQuery.trim()) bumpEgg('search', 20, 'egg_curious_cat');
    const reqId = ++postsReqIdRef.current;
    if (posts.length === 0) {
      setLoadingPosts(true);
    }
    setIsFetchingPosts(true);
    // 排序统一交给服务端：热议按评论数，搜索按相关度/最新/最热
    const serverSort = searchQuery.trim()
      ? searchSort === 'relevance' ? 'relevance' : searchSort === 'hot' ? 'hot' : 'latest'
      : filter === 'comments' ? 'comments' : filter;
    const pageSize = infiniteScroll ? FEED_PAGE_SIZE : PAGE_SIZE;
    try {
      const res = await apiClient.get('/posts', {
        params: {
          sort_by: serverSort,
          page,
          size: pageSize,
          // 搜索走后端全库检索（标题/标签/正文），不再局限于已加载的前 100 篇
          q: searchQuery.trim() || undefined,
          // 分类 / 时间 / 作者一律下推服务端，保证「共 N 篇」和分页都是全站口径
          category: selectedCategory !== '全部' ? selectedCategory : undefined,
          time_range: timeRange !== 'all' ? timeRange : undefined,
          author: searchQuery.trim() && searchAuthor.trim() ? searchAuthor.trim() : undefined,
        },
      });
      // 已被更新的请求取代：直接丢弃，避免旧筛选结果覆盖新列表
      if (reqId !== postsReqIdRef.current) return;
      const rawTotal = Number(res.headers?.['x-total-count']);
      const hasServerTotal = Number.isFinite(rawTotal) && rawTotal >= 0;
      if (hasServerTotal) setTotalPosts(rawTotal);
      const data: PostSimple[] = Array.isArray(res.data) ? res.data : [];
      setPostsPage(page);

      if (!append) {
        setPosts(data);
        // 置顶日报不再从当前列表里挑：列表受 最新/热议/分类 影响，
        // 会导致切换排序时置顶卡片显示成几天前的旧日报。改由 /posts/latest-news 单独提供。
        // 是否还有更多一律以服务端总数（X-Total-Count）为准：
        // 不能用「本页条数 >= 请求 size」判断——缓存返回的条数可能不等于请求的 size，
        // 那样会把「其实还有几十篇」误判成「已经到底」，无限滚动就此卡死。
        setHasMorePosts(
          data.length === 0
            ? false
            : hasServerTotal
            ? data.length < rawTotal
            : data.length >= pageSize
        );
      } else if (data.length > 0) {
        // 追加下一页并按 id 去重，避免与已加载数据重复
        // 函数式更新：用最新列表拼接，避免请求发起时的旧快照覆盖期间的收藏/点赞等乐观更新
        setPosts((prev) => {
          const seenPrev = new Set(prev.map((x) => x.id));
          const merged = [...prev, ...data.filter((x) => !seenPrev.has(x.id))];
          setHasMorePosts(hasServerTotal ? merged.length < rawTotal : data.length >= pageSize);
          return merged;
        });
      } else {
        // 请求到空页说明已经到底，必须停手，否则触底观察器会无限重试
        setHasMorePosts(false);
      }
    } catch {
      if (reqId !== postsReqIdRef.current) return;
      if (page === 1) {
        setPosts(DEMO_POSTS);
        setTotalPosts(DEMO_POSTS.length);
      }
    } finally {
      if (reqId === postsReqIdRef.current) {
        setLoadingPosts(false);
        setIsFetchingPosts(false);
      }
    }
  };

  // 拉取下一页（滚动触底 / 分页翻到最后时调用）
  const loadMorePosts = async () => {
    if (loadingMorePosts || isFetchingPosts || !hasMorePosts) return;
    // 已加载条数达到服务端总数时不必再请求（防止多打一次空页请求）
    if (totalPosts > 0 && posts.length >= totalPosts) return;
    setLoadingMorePosts(true);
    try {
      await fetchPosts(postsPage + 1, true);
    } finally {
      setLoadingMorePosts(false);
    }
  };

  const fetchFollowingPosts = async () => {
    if (!user) return;
    setLoadingFollowing(true);
    try {
      // 此前后端默认只返回 20 条且前端无分页入口，关注动态超过 20 条永远不可见；扩大单次拉取量
      const res = await apiClient.get('/posts/feed/following', { params: { page: 1, size: 100 } });
      if (res.data && Array.isArray(res.data)) {
        setFollowingPosts(res.data);
      }
    } catch {
      // 容错
    } finally {
      setLoadingFollowing(false);
    }
  };

  useEffect(() => {
    // 切换「分页 / 无限滚动」会改变每页条数，必须重新拉第一页并重置页码，
    // 否则列表还停在旧模式下抓到的数据，且 postsPage 带着旧的页码坐标，越翻越乱
    setCurrentPage(1);
    setPostsPage(1);
    fetchPosts(1);
    fetchStats();
    // 首页挂载/筛选切换时重新校准置顶日报为最新一期（单条查询，代价很小）
    fetchLatestNews();
    if (user && streamTab === 'following') {
      fetchFollowingPosts();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, user?.role, streamTab, selectedCategory, timeRange, infiniteScroll]);

  // 首页是常驻挂载（display:none 切换），从编辑器/详情页返回时不会重新挂载，
  // 因此在重新变为可见时静默重拉第一页，让刚发布/刚修改的帖子立即出现。
  const activeMounted = useRef(false);
  // 离开首页前记住阅读位置，回来时还原，避免每次都回到顶部
  const savedScrollRef = useRef(0);
  const restoringRef = useRef(false);

  useEffect(() => {
    const onScroll = () => {
      if (!isActive || restoringRef.current) return;
      savedScrollRef.current = window.scrollY;
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [isActive]);

  useEffect(() => {
    if (!activeMounted.current) {
      activeMounted.current = true;
      return;
    }
    if (!isActive) return;
    const target = savedScrollRef.current;
    restoringRef.current = true;
    // 回到首页时保留原来的分页位置（第 2 页进来就还回第 2 页），
    // 只刷新当前页数据，不重置页码/列表
    fetchPosts(currentPage).then(() => {
      if (target > 0) requestAnimationFrame(() => window.scrollTo({ top: target }));
      // 渲染完成后再解锁，避免还原过程被自身滚动事件覆盖
      window.setTimeout(() => { restoringRef.current = false; }, 300);
    });
    fetchStats();
    // 回到首页时顺带刷新置顶日报，刚发布的新一期能立即生效
    fetchLatestNews();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isActive]);

  // 搜索词变化防抖后重拉全库检索结果（400ms）
  // 首帧跳过：挂载时已有 fetchPosts()，否则每次进首页都会重复请求一次
  const suggestReqIdRef = useRef(0);
  const userSearchReqIdRef = useRef(0);
  const searchEffectMounted = useRef(false);

  /** 清空搜索：一次性同步所有派生状态，避免旧请求回填 / 浮层残留 / URL 与 UI 不一致 */
  const clearSearch = () => {
    setSearchQuery('');
    setIsSearchFocused(false);
    setSuggest({ posts: [], users: [] });
    setSearchedUsers([]);
    setSearchingUsers(false);
    // 让所有在途请求自动失效（各 effect 的 reqId 守卫会丢弃它们）
    suggestReqIdRef.current += 1;
    userSearchReqIdRef.current += 1;
    postsReqIdRef.current += 1;
    setCurrentPage(1);
    setPostsPage(1);
    writeSearchQueryToUrl('', searchTarget, true);
  };
  useEffect(() => {
    if (!searchEffectMounted.current) {
      searchEffectMounted.current = true;
      return;
    }
    const t = setTimeout(() => {
      const q = searchQuery.trim();
      // 搜索词彩蛋：摸鱼 / 摆烂类关键词（本地只上报一次）
      if (q.includes('摸鱼')) reportEggOnce('egg_slack_scout');
      if (/(不想上班|不想写代码|摆烂)/.test(q)) reportEggOnce('egg_breakdown');
      void triggerCrazyThursday(q);
      fetchPosts(1);
    }, searchQuery.trim() ? 400 : 0);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchQuery, searchSort, searchAuthor]);

  // 普通分页模式：切换页码直接向服务端取对应页
  useEffect(() => {
    if (infiniteScroll) return;
    if (currentPage === postsPage) return;
    // 页码超出服务端总页数时不再请求：越界页会返回空数组，
    // 列表变空后分页控件也会随之消失，用户就"回不去"了
    if (totalPosts > 0 && (currentPage - 1) * PAGE_SIZE >= totalPosts) return;
    fetchPosts(currentPage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, infiniteScroll]);

  // 输入联想（仅在聚焦且有输入时请求）
  useEffect(() => {
    const q = searchQuery.trim();
    if (!isSearchFocused || q.length < 1) {
      suggestReqIdRef.current += 1;
      setSuggest({ posts: [], users: [] });
      return;
    }
    const reqId = ++suggestReqIdRef.current;
    const timer = setTimeout(() => {
      apiClient
        .get('/search/suggest', { params: { q, limit: 4 } })
        .then((res) => {
          if (reqId !== suggestReqIdRef.current) return;
          setSuggest({ posts: res.data?.posts || [], users: res.data?.users || [] });
        })
        .catch(() => undefined);
    }, 220);
    return () => clearTimeout(timer);
  }, [searchQuery, isSearchFocused]);

  // 帖子收藏切换（乐观更新 + 失败回滚）
  const handleToggleCollect = (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    if (!localStorage.getItem('access_token')) return;
    // 收藏时统一弹窗：可选择已有收藏夹、新建收藏夹或取消收藏
    openCollectionModal({ id: post.id, title: post.title, is_collected: post.is_collected });
  };

  // 弹窗操作完成后同步本地两条信息流，避免 UI 与后端不一致
  useEffect(() => onCollectionChanged((c) => {
    const apply = (list: PostSimple[]) =>
      list.map((p) => (p.id === c.postId ? { ...p, is_collected: c.is_collected } : p));
    setPosts(apply);
    setFollowingPosts(apply);
  }), []);

  // 帖子一键复制链接
  const handleShare = (e: React.MouseEvent, post: PostSimple) => {
    e.stopPropagation();
    const url = `${window.location.origin}?post=${post.id}`;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url);
    }
    showToast('文章分享链接已复制到剪贴板');
  };

  // 极客用户复合搜索 (Debounced)
  useEffect(() => {
    if (searchTarget === 'users') {
      const q = searchQuery.trim();
      if (!q) {
        userSearchReqIdRef.current += 1;
        setSearchedUsers([]);
        setSearchingUsers(false);
        return;
      }
      setSearchingUsers(true);
      const reqId = ++userSearchReqIdRef.current;
      const timer = setTimeout(async () => {
        try {
          const res = await apiClient.get('/users/search', {
            params: { q, limit: 30 },
          });
          if (reqId !== userSearchReqIdRef.current) return;
          setSearchedUsers(Array.isArray(res.data) ? res.data : []);
        } catch {
          setSearchedUsers([]);
        } finally {
          setSearchingUsers(false);
        }
      }, 250);
      return () => clearTimeout(timer);
    }
  }, [searchTarget, searchQuery]);

  // 搜索列表用户关注/取关快捷操作
  const handleToggleFollowUser = async (e: React.MouseEvent, target: SearchedUserItem) => {
    e.stopPropagation();
    if (!user) {
      onRequireAuth?.();
      return;
    }
    const nextState = !target.is_following;
    setSearchedUsers((prev) =>
      prev.map((u) =>
        u.id === target.id
          ? {
              ...u,
              is_following: nextState,
              followers_count: Math.max(0, u.followers_count + (nextState ? 1 : -1)),
            }
          : u
      )
    );

    try {
      if (nextState) {
        await apiClient.post(`/users/${target.username}/follow`);
        showToast(`已关注 @${target.username}`);
      } else {
        await apiClient.delete(`/users/${target.username}/follow`);
        showToast(`已取消关注 @${target.username}`);
      }
      onRefreshUser?.();
    } catch {
      // 失败回滚
      setSearchedUsers((prev) =>
        prev.map((u) =>
          u.id === target.id
            ? {
                ...u,
                is_following: !nextState,
                followers_count: Math.max(0, u.followers_count + (nextState ? -1 : 1)),
              }
            : u
        )
      );
      showToast('操作失败，请重试');
    }
  };

  // 实时多维过滤与搜索
  const filteredPosts = useMemo(() => {
    const sourceList = streamTab === 'following' ? followingPosts : posts;
    let list = Array.isArray(sourceList) ? [...sourceList] : [];

    // 发现流：分类/时间/搜索/排序已由服务端完成，前端不能再筛（否则正文命中的结果会被误删）
    if (streamTab !== 'following') {
      return list;
    }

    // 分类筛选
    if (selectedCategory !== '全部') {
      list = list.filter((p) => p.category_name === selectedCategory);
    }

    // 时间跨度筛选 (Time Range Filter)
    if (timeRange !== 'all') {
      const now = Date.now();
      const cutoff = timeRange === 'week' ? 7 * 86400 * 1000 : 30 * 86400 * 1000;
      list = list.filter((p) => {
        const time = new Date(p.created_at).getTime();
        return !isNaN(time) ? now - time <= cutoff : true;
      });
    }

    // 关键词与标签搜索
    const query = searchQuery.trim().toLowerCase();
    if (query) {
      list = list.filter(
        (p) =>
          p.title.toLowerCase().includes(query) ||
          p.author_username.toLowerCase().includes(query) ||
          (p.author_nickname && p.author_nickname.toLowerCase().includes(query)) ||
          (p.category_name && p.category_name.toLowerCase().includes(query)) ||
          (p.tags && p.tags.some((t) => t.toLowerCase().includes(query)))
      );
    }

    // 排序逻辑 (如果处于搜索中且选了综合匹配，采用综合相关度评分；否则按常规/热榜排序)
    if (query && searchSort === 'relevance') {
      list.sort((a, b) => {
        if (a.is_pinned && !b.is_pinned) return -1;
        if (!a.is_pinned && b.is_pinned) return 1;

        let scoreA = 0;
        let scoreB = 0;
        if (a.title.toLowerCase().includes(query)) scoreA += 10;
        if (b.title.toLowerCase().includes(query)) scoreB += 10;
        if (a.tags && a.tags.some((t) => t.toLowerCase().includes(query))) scoreA += 6;
        if (b.tags && b.tags.some((t) => t.toLowerCase().includes(query))) scoreB += 6;
        scoreA += (a.like_count || 0) * 0.1 + (a.comment_count || 0) * 0.2;
        scoreB += (b.like_count || 0) * 0.1 + (b.comment_count || 0) * 0.2;

        return scoreB - scoreA;
      });
    } else {
      const activeSort = query ? searchSort : filter;
      if (activeSort === 'hot') {
        const now = Date.now();
        list.sort((a, b) => {
          if (a.is_pinned && !b.is_pinned) return -1;
          if (!a.is_pinned && b.is_pinned) return 1;
          const hoursA = Math.max(0, (now - new Date(a.created_at).getTime()) / 3600000);
          const hoursB = Math.max(0, (now - new Date(b.created_at).getTime()) / 3600000);
          const scoreA = (a.like_count * 2 + a.comment_count * 3 + a.view_count * 0.2) / Math.pow(hoursA + 2, 1.5);
          const scoreB = (b.like_count * 2 + b.comment_count * 3 + b.view_count * 0.2) / Math.pow(hoursB + 2, 1.5);
          return scoreB - scoreA;
        });
      } else if (activeSort === 'comments') {
        list.sort((a, b) => {
          if (a.is_pinned && !b.is_pinned) return -1;
          if (!a.is_pinned && b.is_pinned) return 1;
          return b.comment_count - a.comment_count;
        });
      } else {
        list.sort((a, b) => {
          if (a.is_pinned && !b.is_pinned) return -1;
          if (!a.is_pinned && b.is_pinned) return 1;
          return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
        });
      }
    }

    return list;
  }, [posts, followingPosts, streamTab, selectedCategory, searchQuery, filter, searchSort, timeRange]);

  // —— 总数口径 ——
  // 关注流是前端本地筛选，只能以已加载条数为准；
  // 全站流一律用服务端 X-Total-Count，否则「共 N 篇」只会显示当前这一页的条数，
  // 总页数也会被算成 1，分页控件直接消失。
  const listTotal =
    streamTab === 'following'
      ? filteredPosts.length
      : totalPosts > 0
      ? totalPosts
      : filteredPosts.length;
  // 无限滚动每页 100、分页模式每页 10，总页数必须按当前模式的实际页大小算
  const effectivePageSize = infiniteScroll ? 100 : PAGE_SIZE;
  // 搜索态优先用当前筛选的服务端总数，避免展示上一次筛选的残留值
  const displayTotal = searchQuery.trim() ? (listTotal || totalPosts) : listTotal;

  // 计算总页数与当前页
  const totalPages = Math.max(1, Math.ceil(listTotal / effectivePageSize));
  const effectiveCurrentPage = Math.min(currentPage, totalPages);

  // 筛选后总数变少导致页码越界时，自动回退到最后一页，避免停在空白页
  useEffect(() => {
    if (currentPage > totalPages) setCurrentPage(totalPages);
  }, [currentPage, totalPages]);

  // 快速跳转至指定页码
  const handleJumpPage = () => {
    const target = parseInt(jumpPageInput.trim(), 10);
    if (!isNaN(target) && target >= 1 && target <= totalPages) {
      setCurrentPage(target);
      setJumpPageInput('');
      feedTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
      toast.warning(`请输入 1 至 ${totalPages} 之间的有效页码`);
    }
  };

  // 服务端已按页返回：无限滚动由追加实现，普通分页每次只渲染当前页
  const displayedPosts = streamTab === 'following' ? filteredPosts : posts;

  // 当筛选条件、搜索词变化时重置页码
  useEffect(() => {
    setCurrentPage(1);
  }, [filter, selectedCategory, searchQuery, timeRange, streamTab]);

  // 跨组件监听浏览偏好切换事件
  useEffect(() => {
    const handleScrollPref = (e: Event) => {
      const custom = e as CustomEvent<boolean>;
      if (typeof custom.detail === 'boolean') {
        setInfiniteScroll(custom.detail);
      }
    };
    window.addEventListener('geek_infinite_scroll_change', handleScrollPref);
    return () => window.removeEventListener('geek_infinite_scroll_change', handleScrollPref);
  }, []);

  // 切换「无限滚动 / 普通分页」时页大小会变，需要按新模式重新取第一页
  const scrollModeMounted = useRef(false);
  useEffect(() => {
    if (!scrollModeMounted.current) {
      scrollModeMounted.current = true;
      return;
    }
    setCurrentPage(1);
    if (streamTab === 'all') fetchPosts(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [infiniteScroll]);

  // 无限滚动 IntersectionObserver 监听底部哨兵
  useEffect(() => {
    if (!infiniteScroll) return;
    const sentinel = bottomSentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const first = entries[0];
        // 服务端分页：触底直接取下一页并追加，直到没有更多
        if (first.isIntersecting && hasMorePosts && !loadingPosts && !isFetchingPosts && !loadingMorePosts) {
          void loadMorePosts();
        }
      },
      { rootMargin: '300px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [infiniteScroll, loadingPosts, isFetchingPosts, loadingMorePosts, hasMorePosts]);

  return (
    <div
      className={
        uiMode === 'pro'
          ? 'max-w-[1560px] mx-auto px-2 sm:px-4 py-2 sm:py-3 pb-32 sm:pb-8 flex gap-4 sm:gap-5 items-start w-full transition-all'
          : 'max-w-7xl mx-auto px-3 sm:px-4 py-3 sm:py-5 pb-32 sm:pb-8 flex flex-col lg:flex-row gap-5 sm:gap-7 items-start w-full overflow-hidden'
      }
    >
      {/* 专业版左侧栏：技术分区导航 + 个人快捷工作台 (按用户要求排布在左侧，充分利用大屏空间) */}
      {uiMode === 'pro' && (
        <aside className="w-52 shrink-0 hidden md:block space-y-3.5 select-none sticky top-16">
          {/* 技术分区列表 (全部、科技前沿、后端架构...) */}
          <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-2 shadow-2xs space-y-1">
            <div className="px-3 py-1.5 text-3xs font-bold text-wb-text-muted uppercase tracking-wider flex items-center justify-between">
              <span>技术分区</span>
              <span className="text-3xs text-wb-text-muted font-mono">{categories.length}</span>
            </div>
            {categories.map((cat) => {
              const isActive = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => {
                    setSelectedCategory(cat);
                    setCurrentPage(1);
                    setPostsPage(1);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition-all text-left cursor-pointer ${
                    isActive
                      ? 'bg-wb-text-primary text-wb-bg-page font-bold shadow-xs'
                      : 'text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle'
                  }`}
                >
                  <span className="truncate">{cat}</span>
                  {isActive && <ChevronRight className="w-3.5 h-3.5 shrink-0" />}
                </button>
              );
            })}
          </div>

          {/* 快捷极客工作台 */}
          <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 shadow-2xs space-y-2">
            <div className="text-3xs font-bold text-wb-text-muted uppercase tracking-wider">
              快捷工作台
            </div>
            <div className="space-y-1">
              <button
                type="button"
                onClick={onGoWrite}
                className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
              >
                <Edit3 className="w-3.5 h-3.5 text-blue-500" />
                <span>发布新帖</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!user) {
                    onRequireAuth?.();
                    return;
                  }
                  setStreamTab('following');
                  setSearchTarget('posts');
                  fetchFollowingPosts();
                }}
                className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
              >
                <Users className="w-3.5 h-3.5 text-emerald-500" />
                <span>关注动态</span>
              </button>
              <button
                type="button"
                onClick={handleCheckin}
                disabled={hasCheckedInToday || checkingIn}
                className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer disabled:opacity-50"
              >
                <Calendar className="w-3.5 h-3.5 text-amber-500" />
                <span>{hasCheckedInToday ? '今日已打卡' : checkingIn ? '签到中...' : '每日签到 (+5 EXP)'}</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!user) {
                    onRequireAuth?.();
                    return;
                  }
                  onSelectUser?.(user.username);
                }}
                className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
              >
                <Bookmark className="w-3.5 h-3.5 text-blue-500" />
                <span>我的主页与收藏</span>
              </button>
            </div>
          </div>

          {/* 友情链接 (真实对齐后端数据，支持深情友链徽章与访问统计，搬至左侧工作台下方) */}
          <FriendLinksCard variant="sidebar" onOpenAll={onOpenLinks} />

          {/* 极客自治公约与规范 (搬至左侧友链下方) */}
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
                <span>免责与法律声明</span>
                <span>&rarr;</span>
              </div>
            </div>
          </div>
        </aside>
      )}

      {/* 中间主内容流 */}
      <div className={uiMode === 'pro' ? 'flex-1 min-w-0 space-y-3.5' : 'flex-1 min-w-0 space-y-3 sm:space-y-5 w-full'}>
        {/* 专业版顶栏：每日早报/首要要闻卡片 (像素级还原 media_1794575666584_609de630.jpg) */}
        {uiMode === 'pro' ? (
          <>
            {/* 1. 每日极客早报通栏卡片 (桌面单行，移动端自适应流式排版，避免标题被截断) */}
            <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 sm:p-3.5 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-2 sm:gap-3">
              <div className="flex items-center gap-2 sm:gap-2.5 min-w-0 flex-1">
                <span className="px-1.5 sm:px-2 py-0.5 rounded bg-wb-text-primary text-wb-bg-page text-3xs font-semibold shrink-0">
                  <span className="hidden sm:inline">每日早报·首要要闻</span>
                  <span className="sm:hidden">早报</span>
                </span>
                <span className="text-3xs text-wb-text-muted font-mono shrink-0 hidden sm:inline">
                  {newsPost?.created_at ? formatEast8Date(newsPost.created_at) : '2026-11-13'}
                </span>
                <p
                  onClick={() => newsPost && onSelectPost(newsPost.id)}
                  className="text-xs text-wb-text-primary font-medium truncate cursor-pointer hover:underline flex-1 min-w-0"
                >
                  {newsPost ? newsPost.title : '【极客早报】前沿技术架构与开源交流动态，点击查看完整晨报'}
                </p>
              </div>
              <button
                type="button"
                onClick={() => newsPost && onSelectPost(newsPost.id)}
                className="text-3xs text-wb-text-muted hover:text-wb-text-primary flex items-center gap-0.5 shrink-0 select-none cursor-pointer self-end sm:self-auto"
              >
                <span>立即阅读</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* 1.5 移动端专享：水平横滑技术分区胶囊栏（彻底补齐手机端丢失技术分区的缺陷，单手丝滑横滑） */}
            <div className="md:hidden relative -mx-2 px-2 py-0.5">
              <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5 select-none">
                {categories.map((cat) => {
                  const isActive = selectedCategory === cat;
                  return (
                    <button
                      key={cat}
                      type="button"
                      onClick={() => {
                        setSelectedCategory(cat);
                        setCurrentPage(1);
                        setPostsPage(1);
                      }}
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

            {/* 2. 专业版一体化多维工作台控制栏 (手机端双层规整对齐，桌面端通栏铺排，触控体验丝滑) */}
            <div className="p-2.5 sm:p-3 rounded-xl bg-wb-bg-surface border border-wb-border-subtle shadow-2xs select-none space-y-2 sm:space-y-0 sm:flex sm:items-center sm:justify-between sm:gap-2.5">
              {/* 第一组：流切换 (全部发现/关注动态) + 移动端并排的视图切换 (卡片/摸鱼流) */}
              <div className="flex items-center justify-between gap-2 w-full sm:w-auto">
                {/* 全部发现 vs 关注动态 */}
                <div className="relative flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle select-none">
                  <button
                    type="button"
                    onClick={() => {
                      setStreamTab('all');
                      setSearchTarget('posts');
                    }}
                    className={`relative z-10 h-7 px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors duration-200 cursor-pointer select-none flex items-center justify-center ${
                      streamTab === 'all' && searchTarget === 'posts'
                        ? 'text-wb-text-primary'
                        : 'text-wb-text-muted hover:text-wb-text-secondary'
                    }`}
                  >
                    {streamTab === 'all' && searchTarget === 'posts' && (
                      <motion.div
                        layoutId="pro-stream-indicator"
                        transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                        className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                      />
                    )}
                    <span>全部发现</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      if (!user) {
                        onRequireAuth?.();
                        return;
                      }
                      setStreamTab('following');
                      setSearchTarget('posts');
                      fetchFollowingPosts();
                    }}
                    className={`relative z-10 h-7 px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors duration-200 cursor-pointer select-none flex items-center justify-center ${
                      streamTab === 'following' && searchTarget === 'posts'
                        ? 'text-wb-text-primary'
                        : 'text-wb-text-muted hover:text-wb-text-secondary'
                    }`}
                  >
                    {streamTab === 'following' && searchTarget === 'posts' && (
                      <motion.div
                        layoutId="pro-stream-indicator"
                        transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                        className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                      />
                    )}
                    <span>关注动态</span>
                  </button>
                </div>

                {/* 移动端专享：与流切换并排在右侧的视图模式切换 */}
                <div className="sm:hidden relative flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle text-3xs font-medium select-none">
                  <button
                    type="button"
                    onClick={() => toggleFeedViewMode('card')}
                    className={`relative z-10 flex items-center gap-1 px-2 py-1 rounded-md transition-colors duration-200 cursor-pointer ${
                      feedViewMode === 'card'
                        ? 'text-wb-text-primary font-semibold'
                        : 'text-wb-text-muted hover:text-wb-text-secondary'
                    }`}
                  >
                    {feedViewMode === 'card' && (
                      <motion.div
                        layoutId="pro-view-mode-indicator-mobile"
                        transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                        className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                      />
                    )}
                    <LayoutGrid className="w-3 h-3" />
                    <span>卡片</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => toggleFeedViewMode('dense')}
                    className={`relative z-10 flex items-center gap-1 px-2 py-1 rounded-md transition-colors duration-200 cursor-pointer ${
                      feedViewMode === 'dense'
                        ? 'text-wb-text-primary font-semibold'
                        : 'text-wb-text-muted hover:text-wb-text-secondary'
                    }`}
                  >
                    {feedViewMode === 'dense' && (
                      <motion.div
                        layoutId="pro-view-mode-indicator-mobile"
                        transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                        className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                      />
                    )}
                    <List className="w-3 h-3" />
                    <span>摸鱼流</span>
                  </button>
                </div>
              </div>

              {/* 第二组：最新发布 / 热门互动 / 热议讨论 (移动端铺满整行，触控面积大，带平滑滑动动画) */}
              <div className="flex items-center justify-between gap-2 pt-0.5 sm:pt-0 w-full sm:w-auto">
                <div className="relative flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle select-none flex-1 sm:flex-initial justify-between sm:justify-start">
                  {[
                    { key: 'latest', label: '最新发布' },
                    { key: 'hot', label: '热门互动' },
                    { key: 'comments', label: '热议讨论' },
                  ].map((item) => {
                    const isActive = filter === item.key;
                    return (
                      <button
                        key={item.key}
                        type="button"
                        onClick={() => {
                          setFilter(item.key as any);
                          setCurrentPage(1);
                          setPostsPage(1);
                        }}
                        className={`relative z-10 h-7 flex-1 sm:flex-initial px-2.5 sm:px-3 rounded-md text-xs font-semibold transition-colors duration-200 cursor-pointer select-none flex items-center justify-center ${
                          isActive
                            ? 'text-wb-text-primary'
                            : 'text-wb-text-muted hover:text-wb-text-secondary'
                        }`}
                      >
                        {isActive && (
                          <motion.div
                            layoutId="pro-filter-indicator"
                            transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                            className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                          />
                        )}
                        <span>{item.label}</span>
                      </button>
                    );
                  })}
                </div>

                <span className="text-3xs text-wb-text-muted font-mono hidden md:inline">
                  ({selectedCategory} · 共 {displayTotal} 篇)
                </span>

                {/* 桌面端独享右侧操作区 (卡片/摸鱼流切换 + 滚动/分页 + 老板键伪装) */}
                <div className="hidden sm:flex items-center gap-1.5 shrink-0">
                  {/* 视图模式切换 */}
                  <div className="relative flex items-center bg-wb-bg-subtle p-0.5 rounded-lg border border-wb-border-subtle text-3xs font-medium select-none">
                    <button
                      type="button"
                      onClick={() => toggleFeedViewMode('card')}
                      className={`relative z-10 flex items-center gap-1 px-2.5 py-1 rounded-md transition-colors duration-200 cursor-pointer ${
                        feedViewMode === 'card'
                          ? 'text-wb-text-primary font-semibold'
                          : 'text-wb-text-muted hover:text-wb-text-secondary'
                      }`}
                      title="紧凑卡片流"
                    >
                      {feedViewMode === 'card' && (
                        <motion.div
                          layoutId="pro-view-mode-indicator"
                          transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                          className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                        />
                      )}
                      <LayoutGrid className="w-3 h-3" />
                      <span>卡片</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => toggleFeedViewMode('dense')}
                      className={`relative z-10 flex items-center gap-1 px-2.5 py-1 rounded-md transition-colors duration-200 cursor-pointer ${
                        feedViewMode === 'dense'
                          ? 'text-wb-text-primary font-semibold'
                          : 'text-wb-text-muted hover:text-wb-text-secondary'
                      }`}
                      title="高密度纯文本摸鱼流（仿 Hacker News / Git Commit 列表）"
                    >
                      {feedViewMode === 'dense' && (
                        <motion.div
                          layoutId="pro-view-mode-indicator"
                          transition={{ type: 'spring', stiffness: 500, damping: 35 }}
                          className="absolute inset-0 bg-wb-bg-surface rounded-md shadow-xs border border-wb-border-subtle/60 -z-10"
                        />
                      )}
                      <List className="w-3 h-3" />
                      <span>摸鱼流</span>
                    </button>
                  </div>

                  {/* 滚动/分页切换 */}
                  <button
                    type="button"
                    onClick={() => setInfiniteScroll(!infiniteScroll)}
                    className="flex items-center gap-1 h-7 px-2 rounded-lg bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-wb-text-muted hover:text-wb-text-primary text-3xs font-mono transition-colors cursor-pointer"
                    title={infiniteScroll ? '当前为滚动加载，点击切为分页' : '当前为分页模式，点击切为滚动'}
                  >
                    <Layout className="w-3 h-3" />
                    <span>{infiniteScroll ? '滚动' : '分页'}</span>
                  </button>

                  {/* 极客老板键伪装按钮 */}
                  <button
                    type="button"
                    onClick={() => setBossMode(true)}
                    className="flex items-center gap-1 h-7 px-2 rounded-lg bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-wb-text-muted hover:text-emerald-500 transition-colors text-3xs font-mono cursor-pointer"
                    title="一键切到伪装控制台 (快捷键: Alt+B)"
                  >
                    <Terminal className="w-3 h-3 text-emerald-500" />
                    <span>伪装 (Alt+B)</span>
                  </button>
                </div>
              </div>
            </div>
          </>
        ) : (
          newsPost ? (
            <DailyNewsBlock
              newsPost={newsPost}
              onSelectPost={onSelectPost}
              onGoWriteDaily={onGoWriteDaily}
              isAdmin={isAdmin}
            />
          ) : (
            isAdmin && onGoWriteDaily && (
              <div
                onClick={onGoWriteDaily}
                className="wb-pressable cursor-pointer p-3.5 px-4 rounded-2xl bg-wb-bg-subtle/60 border border-dashed border-wb-border-subtle hover:border-wb-border-strong flex items-center justify-between gap-3 transition-colors text-xs"
              >
                <div className="flex items-center gap-2 text-wb-text-primary">
                  <Sparkles className="w-4 h-4" />
                  <span className="font-medium">今日尚未发布全站极客日报，点击一键起草每日新闻</span>
                </div>
                <span className="font-semibold underline flex items-center gap-1">
                  <span>起草发布</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </span>
              </div>
            )
          )
        )}

        {/* 核心流模式切换与搜索栏 (经典版横向整合，减少垂直层级堆叠，提高首屏曝光) */}
        {uiMode === 'classic' && (
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 w-full">
            <div className="relative flex items-center gap-1.5 p-1 rounded-capsule frost shrink-0 self-start sm:self-auto">
              <button
                type="button"
                onClick={() => {
                  setStreamTab('all');
                  setSearchTarget('posts');
                }}
                className={`wb-pressable relative z-10 h-8 px-4 rounded-capsule text-xs font-semibold transition-colors duration-200 whitespace-nowrap shrink-0 select-none ${
                  streamTab === 'all' && searchTarget === 'posts'
                    ? 'text-wb-bg-page'
                    : 'text-wb-text-secondary hover:text-wb-text-primary'
                }`}
              >
                {streamTab === 'all' && searchTarget === 'posts' && (
                  <motion.div
                    layoutId="home-stream-pill"
                    transition={{ type: 'spring', stiffness: 480, damping: 36 }}
                    className="absolute inset-0 bg-wb-text-primary rounded-capsule shadow-sm -z-10"
                  />
                )}
                全部发现
              </button>
              <button
                type="button"
                onClick={() => {
                  setStreamTab('following');
                  setSearchTarget('posts');
                  if (user) fetchFollowingPosts();
                }}
                className={`wb-pressable relative z-10 h-8 px-4 rounded-capsule text-xs font-semibold transition-colors duration-200 flex items-center gap-1.5 whitespace-nowrap shrink-0 select-none ${
                  streamTab === 'following' && searchTarget === 'posts'
                    ? 'text-wb-bg-page'
                    : 'text-wb-text-secondary hover:text-wb-text-primary'
                }`}
              >
                {streamTab === 'following' && searchTarget === 'posts' && (
                  <motion.div
                    layoutId="home-stream-pill"
                    transition={{ type: 'spring', stiffness: 480, damping: 36 }}
                    className="absolute inset-0 bg-wb-text-primary rounded-capsule shadow-sm -z-10"
                  />
                )}
                <span className="whitespace-nowrap">关注动态</span>
              </button>
            </div>

            {/* 综合搜索栏与实时输入 (Clean Search Bar with Popover) */}
            <div ref={searchContainerRef} className="relative z-30 flex-1 min-w-0 w-full sm:max-w-md">
              <div className="flex items-center gap-1.5 sm:gap-2 w-full max-w-full">
                <div className="relative flex-1 min-w-0">
                  <Search className="absolute left-3 sm:left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-wb-text-muted pointer-events-none" />
                  <input
                    type="text"
                    value={searchQuery}
                    onFocus={() => setIsSearchFocused(true)}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        const keyword = searchQuery.trim();
                        if (keyword) {
                          addSearchHistory(keyword);
                          writeSearchQueryToUrl(keyword, searchTarget);
                        }
                        setCurrentPage(1);
                        setPostsPage(1);
                        setIsSearchFocused(false);
                      } else if (e.key === 'Escape') {
                        setIsSearchFocused(false);
                      }
                    }}
                    placeholder={searchTarget === 'posts' ? "搜索文章、作者、技术标签..." : "搜索极客用户名、个人简介..."}
                    className="w-full h-9 sm:h-10 pl-9 sm:pl-10 pr-9 sm:pr-10 text-xs md:text-sm bg-wb-bg-surface border border-wb-border-subtle rounded-2xl text-wb-text-primary placeholder:text-wb-text-muted focus:outline-none focus:border-wb-border-strong shadow-card transition-all"
                  />
                  {searchQuery && (
                    <button
                      type="button"
                      onClick={clearSearch}
                      onMouseDown={(e) => e.preventDefault()}
                      className="absolute right-2 sm:right-3 top-0 bottom-0 my-auto h-7 w-7 flex items-center justify-center text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle rounded-full transition-colors"
                      title="清空输入"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                {/* 搜索目标切换胶囊 */}
                <div className="relative flex items-center p-0.5 sm:p-1 rounded-2xl frost shrink-0 whitespace-nowrap">
                  {targetPill.width > 0 && (
                    <motion.div
                      aria-hidden="true"
                      className="absolute top-0.5 bottom-0.5 left-0 rounded-xl bg-wb-text-primary shadow-sm"
                      initial={false}
                      animate={{ x: targetPill.x, width: targetPill.width }}
                      transition={{ type: 'spring', stiffness: 520, damping: 40, mass: 0.8 }}
                    />
                  )}
                  <button
                    ref={postsTargetRef}
                    type="button"
                    onClick={() => setSearchTarget('posts')}
                    className={`relative z-10 wb-pressable h-7 sm:h-8 px-2 sm:px-3 rounded-xl text-xs font-semibold transition-colors flex items-center gap-1 sm:gap-1.5 whitespace-nowrap shrink-0 ${
                      searchTarget === 'posts'
                        ? 'text-wb-bg-page'
                        : 'text-wb-text-secondary hover:text-wb-text-primary'
                    }`}
                    title="搜索文章"
                  >
                    <FileText className="w-3.5 h-3.5 shrink-0" />
                    <span className="hidden sm:inline whitespace-nowrap">文章</span>
                  </button>
                  <button
                    ref={usersTargetRef}
                    type="button"
                    onClick={() => setSearchTarget('users')}
                    className={`relative z-10 wb-pressable h-7 sm:h-8 px-2 sm:px-3 rounded-xl text-xs font-semibold transition-colors flex items-center gap-1 sm:gap-1.5 whitespace-nowrap shrink-0 ${
                      searchTarget === 'users'
                        ? 'text-wb-bg-page'
                        : 'text-wb-text-secondary hover:text-wb-text-primary'
                    }`}
                    title="搜索极客用户"
                  >
                    <Users className="w-3.5 h-3.5 shrink-0" />
                    <span className="hidden sm:inline whitespace-nowrap">极客</span>
                  </button>
                </div>
              </div>

          {/* 搜索下拉浮层 (聚焦时展现真实历史与动态热搜) */}
          {isSearchFocused && (
            <div
              className="absolute left-0 right-0 top-full mt-2 frost-panel border border-wb-border-subtle rounded-2xl p-4 shadow-2xl space-y-3.5 animate-scale-up"
              onMouseDown={(e) => {
                if ((e.target as HTMLElement).tagName !== 'BUTTON' && (e.target as HTMLElement).tagName !== 'SPAN') {
                  e.preventDefault();
                }
              }}
            >
              {/* 搜索历史：输入框聚焦且未输入内容时展示，一键回填 */}
              {!searchQuery.trim() && searchHistory.length > 0 && (
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between px-1">
                    <span className="text-2xs text-wb-text-muted">最近搜索</span>
                    <button
                      type="button"
                      onClick={() => {
                        setSearchHistory([]);
                        try { localStorage.removeItem('geek_search_history'); } catch {}
                      }}
                      className="text-3xs text-wb-text-muted hover:text-wb-text-primary transition-colors"
                    >
                      清空
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {searchHistory.slice(0, 6).map((term) => (
                      <button
                        key={term}
                        type="button"
                        onClick={() => {
                          setSearchQuery(term);
                          setCurrentPage(1);
                          setPostsPage(1);
                          writeSearchQueryToUrl(term, searchTarget);
                        }}
                        className="px-2.5 py-1 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-2xs text-wb-text-secondary hover:text-wb-text-primary hover:border-wb-border-strong transition-colors"
                      >
                        {term}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* 若用户正在输入内容，提供快捷搜索引导 */}
              {searchQuery.trim() && (suggest.posts.length > 0 || suggest.users.length > 0) && (
                <div className="space-y-1.5">
                  {suggest.posts.length > 0 && (
                    <div className="space-y-0.5">
                      <div className="text-2xs text-wb-text-muted px-1">文章</div>
                      {suggest.posts.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => {
                            addSearchHistory(searchQuery.trim());
                            setSearchTarget('posts');
                            setSearchQuery(item.title);
                            setIsSearchFocused(false);
                          }}
                          className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-wb-bg-subtle text-left transition-colors"
                        >
                          <FileText className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                          <span className="text-xs text-wb-text-primary truncate">{item.title}</span>
                        </button>
                      ))}
                    </div>
                  )}
                  {suggest.users.length > 0 && (
                    <div className="space-y-0.5">
                      <div className="text-2xs text-wb-text-muted px-1">极客</div>
                      {suggest.users.map((item) => (
                        <button
                          key={item.username}
                          type="button"
                          onClick={() => {
                            setSearchTarget('users');
                            setSearchQuery(item.username);
                            setIsSearchFocused(false);
                            writeSearchQueryToUrl(item.username, 'users');
                          }}
                          className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-wb-bg-subtle text-left transition-colors"
                        >
                          <UserRound className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                          <span className="text-xs text-wb-text-primary truncate">@{item.username}</span>
                          <span className="text-2xs text-wb-text-muted truncate">{item.nickname}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {searchQuery.trim() ? (
                <div className="space-y-2">
                  <div className="text-xs text-wb-text-muted flex items-center justify-between">
                    <span>快捷直达搜索</span>
                    <span className="text-3xs font-mono">按 Enter 立即搜索 · Esc 关闭</span>
                  </div>
                  <div className="space-y-1 text-xs">
                    <button
                      type="button"
                      onClick={() => {
                        const keyword = searchQuery.trim();
                        addSearchHistory(keyword);
                        writeSearchQueryToUrl(keyword, searchTarget);
                        setCurrentPage(1);
                        setPostsPage(1);
                        setIsSearchFocused(false);
                      }}
                      className="w-full flex items-center gap-2 p-2 rounded-xl hover:bg-wb-bg-subtle text-wb-text-primary transition-colors text-left"
                    >
                      <Search className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                      <span className="truncate">
                        在全站文章中检索 “<strong className="text-wb-text-primary font-bold">{searchQuery.trim()}</strong>”
                      </span>
                    </button>
                    {searchTarget === 'posts' && (
                      <button
                        type="button"
                        onClick={() => {
                          setSearchTarget('users');
                          addSearchHistory(searchQuery.trim());
                          setIsSearchFocused(false);
                        }}
                        className="w-full flex items-center gap-2 p-2 rounded-xl hover:bg-wb-bg-subtle text-wb-text-secondary hover:text-wb-text-primary transition-colors text-left"
                      >
                        <Users className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                        <span className="truncate">
                          转为在极客用户中查找 “<strong>{searchQuery.trim()}</strong>”
                        </span>
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                <>
                  {/* 真实搜索历史 (有真实记录时才展示，支持单条删除和一键清空) */}
                  {searchHistory.length > 0 && (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs text-wb-text-muted">
                        <div className="flex items-center gap-1.5 font-medium text-wb-text-secondary">
                          <History className="w-3.5 h-3.5" />
                          <span>历史搜索</span>
                        </div>
                        <button
                          type="button"
                          onClick={clearSearchHistory}
                          className="wb-pressable flex items-center gap-1 text-2xs text-wb-text-muted hover:text-wb-danger transition-colors p-1"
                          title="清空所有历史"
                        >
                          <Trash2 className="w-3 h-3" />
                          <span>清空</span>
                        </button>
                      </div>

                      <div className="flex flex-wrap gap-1.5">
                        {searchHistory.map((item) => (
                          <span
                            key={item}
                            className="group inline-flex items-center gap-1.5 px-3 py-1 rounded-capsule bg-wb-bg-subtle hover:bg-wb-border-subtle text-xs text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle transition-all"
                          >
                            <span
                              className="cursor-pointer max-w-[140px] truncate"
                              onClick={() => {
                                setSearchQuery(item);
                                addSearchHistory(item);
                                setIsSearchFocused(false);
                              }}
                            >
                              {item}
                            </span>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                removeSearchHistory(item);
                              }}
                              title="删除此条历史"
                              className="text-wb-text-muted hover:text-wb-danger transition-colors opacity-50 group-hover:opacity-100"
                            >
                              <X className="w-3 h-3" />
                            </button>
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* 真实动态热搜/探索标签 (统一从后端 publicStats.popular_tags 提取，拒绝伪造重复数据) */}
                  {publicStats.popular_tags && publicStats.popular_tags.length > 0 && (
                    <div className={`space-y-2 ${searchHistory.length > 0 ? 'pt-2.5 border-t border-wb-border-subtle' : ''}`}>
                      <div className="flex items-center justify-between text-xs text-wb-text-muted">
                        <div className="flex items-center gap-1.5 font-medium text-wb-text-secondary">
                          <Flame className="w-3.5 h-3.5 text-wb-brand fill-wb-brand" />
                          <span>热门技术探索</span>
                        </div>
                        <span className="text-3xs font-mono text-wb-text-muted">全站动态</span>
                      </div>

                      <div className="flex flex-wrap gap-1.5">
                        {publicStats.popular_tags.slice(0, 8).map((tag, idx) => (
                          <button
                            key={tag}
                            type="button"
                            onClick={() => {
                              setSearchQuery(tag);
                              addSearchHistory(tag);
                              setIsSearchFocused(false);
                            }}
                            className="wb-pressable inline-flex items-center gap-1 px-2.5 py-1 rounded-capsule bg-wb-bg-surface hover:bg-wb-bg-subtle border border-wb-border-subtle hover:border-wb-border-strong text-xs text-wb-text-secondary hover:text-wb-text-primary transition-all font-mono"
                          >
                            <span className={`text-3xs ${idx < 3 ? 'text-wb-brand font-bold' : 'text-wb-text-muted'}`}>
                              #{idx + 1}
                            </span>
                            <span>{tag}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {searchHistory.length === 0 && (!publicStats.popular_tags || publicStats.popular_tags.length === 0) && (
                    <div className="py-3 text-center text-xs text-wb-text-muted">
                      输入关键词按 Enter 搜索，将自动为您保留历史记录
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </div>
      </div>
    )}

        {searchTarget === 'users' ? (
          <div className="space-y-3 pt-1 animate-fade-in">
            {/* 常驻工作人员：与下方「搜索结果」分成两个独立区块 */}
            <StaffDirectory onSelectUser={onSelectUser} />

            <div className="flex items-center justify-between text-xs text-wb-text-muted font-mono px-1">
              <span>
                {searchQuery.trim() ? `关于 “${searchQuery}” 的极客检索结果` : '全站极客创作者探索'}
              </span>
              <span>共 {searchedUsers.length} 位</span>
            </div>

            {searchingUsers ? (
              <div className="p-12 text-center text-xs text-wb-text-muted space-y-2 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle shadow-card">
                <div className="w-5 h-5 border-2 border-wb-text-primary border-t-transparent rounded-full animate-spin mx-auto" />
                <p>正在检索极客用户...</p>
              </div>
            ) : searchedUsers.length > 0 ? (
              <div className="space-y-2.5">
                {searchedUsers.map((u) => (
                  <div
                    key={u.id}
                    onClick={() => onSelectUser?.(u.username)}
                    className="wb-pressable cursor-pointer p-4 md:p-5 rounded-2xl bg-wb-bg-surface border border-wb-border-subtle hover:border-wb-border-strong shadow-card transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4 group"
                  >
                    <div className="flex items-start sm:items-center gap-3.5 min-w-0">
                      <UserAvatar
                        username={u.username}
                        avatarUrl={u.avatar_url}
                        size={46}
                        className="border border-wb-border-subtle group-hover:border-wb-border-strong flex-shrink-0"
                      />
                      <div className="min-w-0 space-y-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-sm text-wb-text-primary group-hover:underline">
                            {u.nickname || u.username}
                          </span>
                          <span className="text-xs text-wb-text-muted font-mono">
                            @{u.username}
                          </span>
                          {/* 命中来源标注：解决「某人账号名与另一人昵称相同」时分不清是谁 */}
                          {u.match_type === 'nickname' && (
                            <span className="text-3xs px-1.5 py-0.5 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-muted">
                              昵称匹配
                            </span>
                          )}
                          {u.match_type === 'bio' && (
                            <span className="text-3xs px-1.5 py-0.5 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-muted">
                              简介匹配
                            </span>
                          )}
                          <LevelBadge level={u.level} role={u.role} size="sm" />
                        </div>
                        {u.bio ? (
                          <p className="text-xs text-wb-text-secondary line-clamp-1">
                            {u.bio}
                          </p>
                        ) : (
                          <p className="text-xs text-wb-text-muted italic">该极客暂未填写签名</p>
                        )}
                        <div className="flex items-center gap-3 text-2xs text-wb-text-muted font-mono pt-0.5">
                          <span>{u.followers_count} 位关注者</span>
                          <span>·</span>
                          <span>{u.following_count} 正在关注</span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center shrink-0" onClick={(e) => e.stopPropagation()}>
                      {user && user.username !== u.username && (
                        <button
                          type="button"
                          onClick={(e) => handleToggleFollowUser(e, u)}
                          className={`wb-pressable flex items-center gap-1.5 h-8 px-3.5 rounded-capsule text-xs font-semibold whitespace-nowrap shrink-0 transition-all ${
                            u.is_following
                              ? 'bg-wb-bg-subtle hover:bg-wb-border-subtle text-wb-text-primary border border-wb-border-subtle'
                              : 'bg-wb-text-primary text-wb-bg-page hover:opacity-90 shadow-sm'
                          }`}
                        >
                          {u.is_following ? (
                            <>
                              <UserCheck className="w-3.5 h-3.5 shrink-0" />
                              <span className="whitespace-nowrap">已关注</span>
                            </>
                          ) : (
                            <>
                              <UserPlus className="w-3.5 h-3.5 shrink-0" />
                              <span className="whitespace-nowrap">关注</span>
                            </>
                          )}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onSelectUser?.(u.username)}
                        className="wb-pressable h-8 px-3.5 rounded-capsule text-xs text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle hover:border-wb-border-strong transition-colors whitespace-nowrap shrink-0"
                      >
                        进入主页
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-12 text-center text-xs text-wb-text-muted space-y-2 rounded-3xl bg-wb-bg-surface border border-dashed border-wb-border-subtle shadow-card">
                <Users className="w-8 h-8 text-wb-text-muted mx-auto stroke-1" />
                <p className="text-wb-text-secondary">没有找到相关极客</p>
                <p className="text-2xs">可尝试输入更简短的用户名或昵称关键词</p>
              </div>
            )}
          </div>
        ) : (
          <>
            {/* 板块分类筛选：专业版分类已在顶部通栏排列，经典版保持横滑胶囊 */}
            {uiMode === 'pro' ? null : (
              <div className="relative -mx-1">
                <div
                  className="flex items-center gap-2 overflow-x-auto overflow-y-hidden py-1.5 -my-1 px-1 pr-8 no-scrollbar"
                  style={{ overflowY: 'hidden', scrollbarWidth: 'none' }}
                >
                  {categories.map((cat) => {
                    const isActive = selectedCategory === cat;
                    return (
                      <button
                        key={cat}
                        type="button"
                        onClick={() => { setSelectedCategory(cat); setCurrentPage(1); setPostsPage(1); }}
                        className={`wb-pressable h-8 px-3.5 rounded-capsule text-xs font-medium whitespace-nowrap shrink-0 border transition-all select-none ${
                          isActive
                            ? 'bg-wb-text-primary text-wb-bg-page border-transparent font-semibold shadow-sm'
                            : 'frost text-wb-text-secondary border-wb-border-subtle hover:border-wb-border-strong hover:text-wb-text-primary'
                        }`}
                      >
                        {cat}
                      </button>
                    );
                  })}
                </div>
                <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-8 bg-gradient-to-l from-wb-bg-page to-transparent sm:hidden" />
              </div>
            )}

            {/* 排序与多维复合筛选控制器 */}
            {searchQuery.trim() ? (
              <div className="p-3.5 rounded-2xl frost flex flex-wrap items-center justify-between gap-3 text-xs">
                {/* 排序维度药丸 */}
                <div className="flex items-center gap-1.5 flex-wrap">
                  <span className="text-wb-text-muted text-2xs">排序:</span>
                  {[
                    { key: 'relevance', label: '综合匹配' },
                    { key: 'latest', label: '最新发布' },
                    { key: 'hot', label: '最热互动' },
                  ].map((opt) => (
                    <button
                      key={opt.key}
                      type="button"
                      onClick={() => setSearchSort(opt.key as any)}
                      className={`wb-pressable px-2.5 py-1 rounded-capsule border text-xs transition-all cursor-pointer ${
                        searchSort === opt.key
                          ? 'bg-wb-text-primary text-wb-bg-page font-semibold border-transparent shadow-sm'
                          : 'bg-wb-bg-subtle text-wb-text-secondary border-wb-border-subtle hover:text-wb-text-primary'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>

                {/* 时间跨度过滤 */}
                <div className="flex items-center gap-1.5 flex-wrap">
                  <Clock className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                  {[
                    { key: 'all', label: '全部时间' },
                    { key: 'week', label: '近一周' },
                    { key: 'month', label: '近一月' },
                  ].map((t) => (
                    <button
                      key={t.key}
                      type="button"
                      onClick={() => setTimeRange(t.key as any)}
                      className={`wb-pressable px-2.5 py-1 rounded-capsule border text-2xs font-mono transition-all cursor-pointer ${
                        timeRange === t.key
                          ? 'bg-wb-text-primary text-wb-bg-page font-semibold border-transparent shadow-sm'
                          : 'bg-wb-bg-subtle text-wb-text-secondary border-wb-border-subtle hover:text-wb-text-primary'
                      }`}
                    >
                      {t.label}
                    </button>
                  ))}
                  <span className="text-wb-text-muted font-mono ml-1 text-xs">
                    (共 {displayTotal} 篇)
                  </span>
                </div>

                {/* 按作者筛选（服务端精确匹配用户名） */}
                <div className="flex items-center gap-1.5 w-full sm:w-auto">
                  <UserRound className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                  <input
                    type="text"
                    value={searchAuthor}
                    onChange={(e) => setSearchAuthor(e.target.value)}
                    placeholder="按作者用户名筛选"
                    className="h-7 w-full sm:w-40 px-2.5 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-2xs text-wb-text-primary focus:outline-none focus:border-wb-border-strong"
                  />
                  {(searchAuthor.trim() || selectedCategory !== '全部' || timeRange !== 'all') && (
                    <button
                      type="button"
                      onClick={() => {
                        setSearchAuthor('');
                        setSelectedCategory('全部');
                        setTimeRange('all');
                      }}
                      className="shrink-0 text-2xs text-wb-text-muted hover:text-wb-text-primary underline"
                    >
                      清除筛选
                    </button>
                  )}
                </div>
              </div>
            ) : uiMode === 'pro' ? null : (
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 sm:gap-4 w-full">
                <SegmentedControl
                  id="home-feed"
                  className="self-start"
                  items={[
                    { key: 'latest', label: '最新发布' },
                    { key: 'hot', label: '热门互动' },
                    { key: 'comments', label: '热议讨论' },
                  ]}
                  activeKey={filter}
                  onChange={(k) => setFilter(k as any)}
                />

                <div className="text-xs text-wb-text-muted font-mono flex items-center justify-between sm:justify-end gap-2.5 w-full sm:w-auto">
                  <span>共 {displayTotal} 篇</span>
                  <button
                    type="button"
                    onClick={() => {
                      const next = !infiniteScroll;
                      setInfiniteScroll(next);
                      try {
                        localStorage.setItem('geek_home_infinite_scroll', String(next));
                        window.dispatchEvent(new CustomEvent('geek_infinite_scroll_change', { detail: next }));
                      } catch {}
                      toast.info(next ? '已开启首页无限滚动模式' : '已恢复普通分页模式');
                    }}
                    className="wb-pressable inline-flex items-center gap-1.5 px-2.5 py-1 rounded-capsule bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-2xs text-wb-text-secondary hover:text-wb-text-primary transition-all select-none cursor-pointer"
                    title={infiniteScroll ? '当前为无限滚动，点击切换为普通分页' : '当前为普通分页，点击切换为无限滚动'}
                  >
                    {infiniteScroll ? (
                      <>
                        <ArrowDownCircle className="w-3.5 h-3.5 text-wb-text-primary" />
                        <span className="hidden sm:inline">无限滚动</span>
                      </>
                    ) : (
                      <>
                        <BookOpen className="w-3.5 h-3.5 text-wb-text-primary" />
                        <span className="hidden sm:inline">分页模式</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* 列表顶部锚点 (分页切换时平滑上卷) */}
            <div ref={feedTopRef} className="scroll-mt-24" />

            {/* 内容信息流列表 */}
            {loadingPosts && posts.length === 0 ? (
              <div className="space-y-3.5">
                <PostCardSkeleton />
                <PostCardSkeleton />
                <PostCardSkeleton />
              </div>
            ) : (
              <motion.div
                key={`${filter}-${selectedCategory}-${infiniteScroll ? 'inf' : effectiveCurrentPage}`}
                initial={{ opacity: 0.92 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.12, ease: 'easeOut' }}
                className={`space-y-3.5 min-h-[480px] transition-opacity duration-150 ${
                  isFetchingPosts ? 'opacity-65 pointer-events-none' : 'opacity-100'
                }`}
              >
              {displayedPosts.length > 0 ? (
                <>
                {displayedPosts.map((post, postIdx) => (
                  uiMode === 'pro' && feedViewMode === 'dense' ? (
                    <article
                      key={post.id}
                      onClick={(e) => {
                        const preferNewTab = (() => {
                          try { return localStorage.getItem('wb_open_post_in_new_tab') === 'true'; } catch { return false; }
                        })();
                        if (e.metaKey || e.ctrlKey || e.button === 1 || preferNewTab) {
                          window.open(`/?post=${post.id}`, '_blank', 'noopener,noreferrer');
                        } else {
                          onSelectPost(post.id);
                        }
                      }}
                      className="cv-auto w-full bg-wb-bg-surface hover:bg-wb-bg-subtle/80 border border-wb-border-subtle hover:border-wb-border-strong px-3 sm:px-4 py-2 sm:py-2.5 rounded-lg transition-all cursor-pointer group flex items-center justify-between gap-3 text-xs select-none"
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
                          <HighlightText text={getSmartDailyTitle(post.title, post.id)} query={searchQuery} />
                        </h3>
                      </div>
                      <div className="flex items-center gap-3 shrink-0 text-3xs text-wb-text-muted font-mono">
                        <span className="hidden md:inline">@{post.author_nickname || post.author_username}</span>
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
                  <article
                    key={post.id}
                    /* 屏幕外卡片跳过布局绘制，长列表滚动更省 CPU */

                    onClick={(e) => {
                      const preferNewTab = (() => {
                        try { return localStorage.getItem('wb_open_post_in_new_tab') === 'true'; } catch { return false; }
                      })();
                      if (e.metaKey || e.ctrlKey || e.button === 1 || preferNewTab) {
                        window.open(`/?post=${post.id}`, '_blank', 'noopener,noreferrer');
                      } else {
                        onSelectPost(post.id);
                      }
                    }}
                    className={
                      uiMode === 'pro'
                        ? 'cv-auto w-full bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3.5 sm:p-4 shadow-2xs hover:border-wb-border-strong transition-all cursor-pointer group space-y-2'
                        : 'cv-auto surface wb-pressable cursor-pointer p-4 md:p-5 rounded-2xl border border-wb-border-subtle shadow-card hover:border-wb-border-strong transition-all group'
                    }
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-1.5 flex-1 min-w-0">
                        {/* 作者与元信息 */}
                        {uiMode === 'pro' ? (
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2 text-xs flex-wrap">
                              <div className="w-5.5 h-5.5 rounded-full bg-wb-bg-subtle border border-wb-border-subtle flex items-center justify-center shrink-0 overflow-hidden">
                                {post.author_avatar ? (
                                  <img src={post.author_avatar} alt="" className="w-full h-full object-cover" />
                                ) : (
                                  <UserRound className="w-3 h-3 text-wb-text-muted" />
                                )}
                              </div>
                              <span className="font-semibold text-xs text-wb-text-primary group-hover:text-wb-brand transition-colors">
                                {post.author_nickname || post.author_username}
                              </span>
                              <span className="text-3xs px-1.5 py-0.2 rounded bg-wb-bg-subtle text-wb-text-muted font-mono">
                                Lv.{post.author_level || 1}
                              </span>
                              <span className="text-xs text-wb-text-muted font-normal font-mono">
                                {formatEast8Date(post.created_at)} · {post.category_name || (post.category_id && categoryMap[post.category_id]) || '综合交流'}
                              </span>
                              {post.is_pinned && (
                                <span className="inline-flex items-center gap-0.5 text-3xs px-1.5 py-0.5 rounded bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 font-medium">
                                  <Pin className="w-2.5 h-2.5 text-red-500" />
                                  <span>置顶</span>
                                </span>
                              )}
                              {post.is_essence && (
                                <span className="inline-flex items-center gap-0.5 text-3xs px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-950/30 text-amber-600 dark:text-amber-400 font-medium">
                                  <Sparkles className="w-2.5 h-2.5 text-amber-500" />
                                  <span>精华</span>
                                </span>
                              )}
                            </div>
                          </div>
                        ) : (
                          <div className="flex items-center justify-between gap-2 flex-wrap">
                            <div className="flex items-center gap-2 text-xs text-wb-text-secondary flex-wrap">
                              <div className="inline-flex items-center gap-1.5 flex-shrink-0 select-none">
                                <UserAvatar
                                  username={post.author_username}
                                  avatarUrl={post.author_avatar}
                                  size={22}
                                  className="border border-wb-border-subtle flex-shrink-0"
                                />
                                <span className="font-medium text-wb-text-primary">
                                  {post.author_nickname || post.author_username}
                                </span>
                              </div>
                              <LevelBadge level={post.author_level} />

                              <span>·</span>
                              <span className="text-wb-text-muted font-mono">
                                {formatEast8Date(post.created_at)}
                              </span>
                              {post.category_name && (
                                <span className="text-wb-text-muted font-medium">
                                  · {post.category_name}
                                </span>
                              )}
                              {post.is_pinned && (
                                <span className="inline-flex items-center gap-0.5 text-3xs px-1.5 py-0.2 rounded-capsule bg-wb-bg-subtle text-wb-text-primary font-semibold">
                                  <Pin className="w-2.5 h-2.5" /> 置顶
                                </span>
                              )}
                              {post.is_essence && (
                                <span className="inline-flex items-center gap-0.5 text-3xs px-2 py-0.5 rounded-capsule bg-wb-brand/15 text-wb-brand border border-wb-brand/25 font-bold shadow-xs">
                                  <Sparkles className="w-2.5 h-2.5 text-wb-brand" /> 精华
                                </span>
                              )}
                            </div>
                          </div>
                        )}

                        {/* 标题 */}
                        <h2 className={uiMode === 'pro' ? 'text-sm sm:text-base font-bold text-wb-text-primary group-hover:underline leading-snug line-clamp-2' : 'text-base md:text-lg font-semibold text-wb-text-primary group-hover:underline line-clamp-2 leading-snug'}>
                          <HighlightText text={getSmartDailyTitle(post.title, post.id)} query={searchQuery} />
                        </h2>

                        {/* 专业版专属：正文摘要预览，扩展信息量，更富学习与专业深度 */}
                        {uiMode === 'pro' && (
                          <p className="text-2xs sm:text-xs text-wb-text-muted line-clamp-2 leading-relaxed">
                            {post.summary || post.match_snippet || '深入探讨架构细节、工程实现与开发经验，点击进入阅读完整正文与代码解析...'}
                          </p>
                        )}

                        {/* 话题与技术标签：专业版根据需求隐藏，经典版正常显示 */}
                        {uiMode !== 'pro' && post.tags && post.tags.length > 0 && (
                          <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                            {post.tags.map((t) => (
                              <span
                                key={t}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSearchQuery(t);
                                }}
                                className="text-3xs font-mono px-2 py-0.5 rounded-md bg-wb-bg-subtle/80 hover:bg-wb-bg-subtle text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle transition-colors cursor-pointer"
                                title={`按标签 #${t} 筛选`}
                              >
                                #{t}
                              </span>
                            ))}
                          </div>
                        )}

                        {/* 互动数据与快捷操作：专业版 (去除重复分类与阅读时长，展示阅读量+快捷标识+点赞/评论/分享) */}
                        {uiMode === 'pro' ? (
                          <div className="flex items-center justify-between pt-1 gap-2 flex-wrap text-xs text-wb-text-muted">
                            {/* 左侧：阅读量 + (阅读人数右侧标识群：加入专栏、表情互动、举报帖子、收藏文章) */}
                            <div className="flex items-center gap-2 font-mono flex-wrap">
                              <span className="flex items-center gap-1 text-3xs text-wb-text-muted font-sans">
                                <Eye className="w-3.5 h-3.5 text-wb-text-muted" />
                                <span>阅读 {post.view_count || 0}</span>
                              </span>

                              {/* 在阅读人数右侧以标识代替加入专栏、表情互动、举报帖子、收藏文章，悬浮显示功能信息 */}
                              <div className="flex items-center gap-1 pl-1.5 border-l border-wb-border-subtle" onClick={(e) => e.stopPropagation()}>
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    openCollectionModal({ id: post.id, title: post.title, is_collected: post.is_collected });
                                  }}
                                  title="加入专栏"
                                  className="hidden sm:inline-flex p-1 rounded text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
                                >
                                  <FolderPlus className="w-3.5 h-3.5" />
                                </button>
                                <div className="relative hidden sm:block">
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setActiveReactionPostId(activeReactionPostId === post.id ? null : post.id);
                                    }}
                                    title="表情互动"
                                    className="p-1 rounded text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
                                  >
                                    <Smile className="w-3.5 h-3.5" />
                                  </button>
                                  {activeReactionPostId === post.id && (
                                    <div
                                      onClick={(e) => e.stopPropagation()}
                                      className="absolute left-0 bottom-full mb-1.5 p-1.5 bg-wb-bg-surface border border-wb-border-subtle rounded-xl shadow-lg flex items-center gap-1 z-30 animate-scale-up"
                                    >
                                      {['👍', '❤️', '🔥', '🎉', '🚀', '👏'].map((emo) => (
                                        <button
                                          key={emo}
                                          type="button"
                                          onClick={(e) => handleQuickReaction(e, post, emo)}
                                          className="p-1 hover:bg-wb-bg-subtle rounded text-sm transition-transform hover:scale-125 cursor-pointer"
                                        >
                                          {emo}
                                        </button>
                                      ))}
                                    </div>
                                  )}
                                </div>
                                <button
                                  type="button"
                                  onClick={(e) => handleReportPost(e, post)}
                                  title="举报帖子"
                                  className="hidden sm:inline-flex p-1 rounded text-wb-text-muted hover:text-red-500 hover:bg-wb-bg-subtle transition-colors cursor-pointer"
                                >
                                  <Flag className="w-3.5 h-3.5" />
                                </button>
                                <button
                                  type="button"
                                  onClick={(e) => handleToggleCollect(e, post)}
                                  title={post.is_collected ? '取消收藏' : '收藏文章'}
                                  className={`p-1 rounded transition-colors cursor-pointer ${
                                    post.is_collected
                                      ? 'text-amber-500'
                                      : 'text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle'
                                  }`}
                                >
                                  <Bookmark className={`w-3.5 h-3.5 ${post.is_collected ? 'fill-current' : ''}`} />
                                </button>
                              </div>
                            </div>

                            {/* 帖子右侧保留三种功能：点赞、评论、分享 */}
                            <div className="flex items-center gap-3" onClick={(e) => e.stopPropagation()}>
                              <button
                                type="button"
                                onClick={(e) => handleQuickLike(e, post)}
                                title="点赞"
                                className="flex items-center gap-1 text-xs text-wb-text-muted hover:text-red-500 transition-colors cursor-pointer"
                              >
                                <Heart className="w-3.5 h-3.5 hover:fill-current" />
                                <span className="font-mono text-3xs">{post.like_count || 0}</span>
                              </button>
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onSelectPost(post.id);
                                }}
                                title="评论"
                                className="flex items-center gap-1 text-xs text-wb-text-muted hover:text-blue-500 transition-colors cursor-pointer"
                              >
                                <MessageSquare className="w-3.5 h-3.5" />
                                <span className="font-mono text-3xs">{post.comment_count || 0}</span>
                              </button>
                              <button
                                type="button"
                                onClick={(e) => handleShare(e, post)}
                                title="复制分享链接"
                                className="p-1 hover:text-wb-text-primary hover:bg-wb-bg-subtle rounded transition-colors cursor-pointer"
                              >
                                <Share2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="flex items-center justify-between pt-1">
                            <div className="flex items-center gap-4 text-xs text-wb-text-muted font-mono">
                              <span className="inline-flex items-center gap-1 font-sans">
                                {post.category_id && categoryMap[post.category_id] && (
                                  <span className="px-1.5 py-0.5 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-secondary text-3xs">
                                    {categoryMap[post.category_id]}
                                  </span>
                                )}
                                <Clock className="w-3.5 h-3.5" /> {post.reading_time_minutes} 分钟
                              </span>
                              <span className="inline-flex items-center gap-1">
                                <Eye className="w-3.5 h-3.5" /> {post.view_count}
                              </span>
                              <span className="inline-flex items-center gap-1">
                                <Heart className="w-3.5 h-3.5" /> {post.like_count}
                              </span>
                              <span className="inline-flex items-center gap-1">
                                <MessageSquare className="w-3.5 h-3.5" /> {post.comment_count}
                              </span>
                            </div>

                            <div className="flex items-center gap-1 opacity-80 group-hover:opacity-100 transition-opacity shrink-0">
                              <a
                                href={`/?post=${post.id}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                onClick={(e) => e.stopPropagation()}
                                title="在新网页中独立打开"
                                className="hidden sm:inline-flex p-1.5 rounded-lg border border-wb-border-subtle hover:border-wb-border-strong text-wb-text-muted hover:text-wb-text-primary text-xs transition-colors"
                              >
                                <ExternalLink className="w-3.5 h-3.5" />
                              </a>
                              <button
                                type="button"
                                onClick={(e) => handleToggleCollect(e, post)}
                                title={post.is_collected ? '取消收藏' : '收藏此帖'}
                                className={`p-1.5 rounded-lg border text-xs transition-colors ${
                                  post.is_collected
                                    ? 'bg-wb-text-primary text-wb-bg-page border-transparent'
                                    : 'border-wb-border-subtle hover:border-wb-border-strong text-wb-text-muted hover:text-wb-text-primary'
                                }`}
                              >
                                <Bookmark className={`w-3.5 h-3.5 ${post.is_collected ? 'fill-current' : ''}`} />
                              </button>
                              <button
                                type="button"
                                onClick={(e) => handleShare(e, post)}
                                title="复制分享链接"
                                className="p-1.5 rounded-lg border border-wb-border-subtle hover:border-wb-border-strong text-wb-text-muted hover:text-wb-text-primary text-xs transition-colors"
                              >
                                <Share2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* 桌面端：右侧小缩略图；手机端改为下方通栏图，避免卡片被拉高 */}
                      {post.display_image && (
                        <img
                          loading="lazy"
                          decoding="async"
                          src={thumbUrl(post.display_image, 640)}
                          srcSet={thumbSrcSet(post.display_image)}
                          sizes="(max-width: 768px) 100vw, 640px"
                          alt=""
                          onError={(e) => {
                            const el = e.currentTarget as HTMLImageElement;
                            const orig = post.display_image || '';
                            if (orig && !el.dataset.fb && el.src !== orig) {
                              el.dataset.fb = '1';
                              el.src = orig;
                              return;
                            }
                            el.style.display = 'none';
                          }}
                          className="hidden sm:block self-center w-20 h-20 rounded-2xl object-cover border border-wb-border-subtle flex-shrink-0"
                        />
                      )}
                    </div>

                    {post.display_image && (
                      <img
                        loading="lazy"
                        decoding="async"
                        src={thumbUrl(post.display_image, 640)}
                          srcSet={thumbSrcSet(post.display_image)}
                          sizes="(max-width: 768px) 100vw, 640px"
                        alt=""
                        onError={(e) => {
                            const el = e.currentTarget as HTMLImageElement;
                            const orig = post.display_image || '';
                            if (orig && !el.dataset.fb && el.src !== orig) {
                              el.dataset.fb = '1';
                              el.src = orig;
                              return;
                            }
                            el.style.display = 'none';
                          }}
                        className="sm:hidden mt-3 w-full h-[168px] object-cover rounded-xl border border-wb-border-subtle"
                      />
                    )}
                  </article>
                  )
                ))}

                {/* 摸鱼流轻量数据边界线 */}
                {uiMode === 'pro' && feedViewMode === 'dense' && (
                  <div className="py-3 text-center text-3xs font-mono text-wb-text-muted flex items-center justify-center gap-2 select-none">
                    <span className="w-10 h-px bg-wb-border-subtle" />
                    <span>EOF :: 摸鱼流已载入全部条目 ({displayedPosts.length} 篇)</span>
                    <span className="w-10 h-px bg-wb-border-subtle" />
                  </div>
                )}

                {/* 模式分页与加载条 */}
                {infiniteScroll ? (
                  <div className="pt-2">
                    <div ref={bottomSentinelRef} className="h-6" />
                    {(loadingMorePosts || isFetchingPosts) && (
                      <div className="py-6 flex items-center justify-center gap-2 text-xs text-wb-text-muted font-mono animate-pulse">
                        <div className="w-4 h-4 border-2 border-wb-text-primary border-t-transparent rounded-full animate-spin" />
                        <span>正在加载更多帖子...</span>
                      </div>
                    )}
                    {!hasMorePosts && !loadingMorePosts && displayedPosts.length > 0 && (
                      <div className="py-8 text-center text-xs text-wb-text-muted font-mono border-t border-wb-border-subtle/50 mt-4 select-none flex items-center justify-center gap-1.5">
                        <Check className="w-3.5 h-3.5 text-wb-success" />
                        <span>已展现全部内容 · 共 {displayTotal} 篇</span>
                      </div>
                    )}
                  </div>
                ) : (
                  /* 普通传统分页导航器 */
                  totalPages > 1 && (
                    <div className="pt-6 pb-2 flex flex-col md:flex-row items-center justify-between gap-3.5 border-t border-wb-border-subtle/60 text-xs select-none">
                      <div className="text-wb-text-muted font-mono flex items-center gap-2">
                        <span>第 <span className="font-bold text-wb-text-primary">{effectiveCurrentPage}</span> / {totalPages} 页</span>
                        <span>·</span>
                        <span>共 {displayTotal} 篇</span>
                      </div>

                      <div className="flex items-center gap-2.5 flex-wrap justify-center">
                        {/* 上一页 / 页码数字 / 下一页 */}
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            disabled={effectiveCurrentPage <= 1}
                            onClick={() => {
                              setCurrentPage((p) => Math.max(1, p - 1));
                              feedTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            }}
                            className="wb-pressable px-3 py-1.5 rounded-capsule border border-wb-border-subtle bg-wb-bg-surface hover:border-wb-border-strong text-wb-text-secondary hover:text-wb-text-primary disabled:opacity-40 disabled:pointer-events-none transition-colors"
                          >
                            上一页
                          </button>

                          {generatePaginationPages(effectiveCurrentPage, totalPages).map((p, idx) => (
                            typeof p === 'number' ? (
                              <button
                                key={p}
                                type="button"
                                onClick={() => {
                                  setCurrentPage(p);
                                  feedTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                                }}
                                className={`wb-pressable w-8 h-8 rounded-capsule text-xs font-mono font-semibold transition-all ${
                                  effectiveCurrentPage === p
                                    ? 'bg-wb-text-primary text-wb-bg-page shadow-xs'
                                    : 'bg-wb-bg-subtle text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle hover:border-wb-border-strong'
                                }`}
                              >
                                {p}
                              </button>
                            ) : (
                              <span key={`ellipsis-${idx}`} className="px-1 text-wb-text-muted font-mono">
                                ...
                              </span>
                            )
                          ))}

                          <button
                            type="button"
                            // 分页模式是服务端翻页（每页 PAGE_SIZE 条），页码由 totalPages 决定；
                            // 旧逻辑在末页调用 loadMorePosts 追加数据，会和"按页替换"互相打架，
                            // 导致列表条数与页码口径不一致
                            disabled={effectiveCurrentPage >= totalPages}
                            onClick={() => {
                              if (effectiveCurrentPage >= totalPages) return;
                              setCurrentPage((p) => Math.min(totalPages, p + 1));
                              feedTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            }}
                            className="wb-pressable px-3 py-1.5 rounded-capsule border border-wb-border-subtle bg-wb-bg-surface hover:border-wb-border-strong text-wb-text-secondary hover:text-wb-text-primary disabled:opacity-40 disabled:pointer-events-none transition-colors"
                          >
                            下一页
                          </button>
                        </div>

                        {/* 快速页面跳转器 (跳至第 X 页) */}
                        <div className="flex items-center gap-1.5 pl-2.5 sm:border-l sm:border-wb-border-subtle text-wb-text-secondary">
                          <span className="text-2xs text-wb-text-muted">跳至</span>
                          <input
                            type="number"
                            min={1}
                            max={totalPages}
                            value={jumpPageInput}
                            onChange={(e) => setJumpPageInput(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') handleJumpPage();
                            }}
                            placeholder={String(effectiveCurrentPage)}
                            className="w-12 h-7 px-1.5 text-center font-mono text-xs rounded-capsule bg-wb-bg-surface border border-wb-border-subtle text-wb-text-primary focus:outline-none focus:border-wb-text-primary [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                          />
                          <span className="text-2xs text-wb-text-muted">页</span>
                          <button
                            type="button"
                            onClick={handleJumpPage}
                            className="wb-pressable px-2.5 h-7 rounded-capsule bg-wb-bg-subtle hover:bg-wb-text-primary hover:text-wb-bg-page border border-wb-border-subtle text-xs font-medium text-wb-text-secondary transition-colors cursor-pointer"
                          >
                            跳转
                          </button>
                        </div>
                      </div>
                    </div>
                  )
                )}
                </>
              ) : (
                /* 空结果指示器 */
                <div className="p-12 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle text-center space-y-3 shadow-card">
                  {streamTab === 'following' && loadingFollowing ? (
                    <div className="py-6 space-y-2">
                      <div className="w-5 h-5 border-2 border-wb-text-primary border-t-transparent rounded-full animate-spin mx-auto" />
                      <p className="text-xs text-wb-text-muted">正在拉取关注动态...</p>
                    </div>
                  ) : (
                    <>
                      <Search className="w-8 h-8 text-wb-text-muted mx-auto stroke-1" />
                      {streamTab === 'following' ? (
                        !user ? (
                          <div className="space-y-3">
                            <h3 className="font-semibold text-sm text-wb-text-primary">请登录后查看关注极客动态</h3>
                            <p className="text-xs text-wb-text-muted">
                              登录极客通行证，第一时间获取已关注创作者的最新发布与深度文章
                            </p>
                            <button
                              type="button"
                              onClick={onRequireAuth}
                              className="wb-pressable inline-flex items-center gap-1.5 h-9 px-5 rounded-capsule bg-wb-text-primary text-wb-bg-page text-xs font-semibold shadow-sm"
                            >
                              通行证登录 / 注册
                            </button>
                          </div>
                        ) : (
                          <div className="space-y-2">
                            <h3 className="font-semibold text-sm text-wb-text-primary">关注流暂时空空如也</h3>
                            <p className="text-xs text-wb-text-muted">
                              您关注的极客尚未发表新文章，或您尚未关注其他极客。去右侧先锋榜探索有趣的创作者吧！
                            </p>
                          </div>
                        )
                      ) : (
                        <div className="space-y-4 max-w-md mx-auto py-2">
                          <div className="space-y-1">
                            <h3 className="font-semibold text-sm text-wb-text-primary">
                              {searchQuery.trim()
                                ? `未找到与 “${searchQuery.trim()}” 相关的极客内容`
                                : `「${selectedCategory}」分类下暂无文章`}
                            </h3>
                            <p className="text-xs text-wb-text-muted leading-relaxed">
                              {searchQuery.trim()
                                ? '建议缩短搜索词、尝试技术别名，或点击下方热门话题一键探索'
                                : '成为该领域的首位探索者，向社区先锋极客分享你的前沿见解与架构思考'}
                            </p>
                          </div>

                          {/* 搜索无结果时：提供热门技术标签一键探索 */}
                          {searchQuery.trim() && publicStats.popular_tags && publicStats.popular_tags.length > 0 && (
                            <div className="space-y-2 pt-2 border-t border-wb-border-subtle/50">
                              <span className="text-2xs text-wb-text-muted">为你推荐热门技术探索主题:</span>
                              <div className="flex items-center justify-center gap-1.5 flex-wrap">
                                {publicStats.popular_tags.slice(0, 6).map((tag) => (
                                  <button
                                    key={tag}
                                    type="button"
                                    onClick={() => setSearchQuery(tag)}
                                    className="wb-pressable text-xs font-mono px-2.5 py-1 rounded-capsule bg-wb-bg-subtle hover:bg-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle transition-colors"
                                  >
                                    #{tag}
                                  </button>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* 行动按钮群: 重置 或 立即发帖 */}
                          <div className="flex items-center justify-center gap-3 pt-2">
                            <button
                              type="button"
                              onClick={() => {
                                setSearchQuery('');
                                setSelectedCategory('全部');
                                setTimeRange('all');
                              }}
                              className="wb-pressable inline-flex items-center gap-1.5 h-8 px-4 rounded-capsule bg-wb-bg-subtle text-wb-text-secondary hover:text-wb-text-primary border border-wb-border-subtle text-xs font-medium cursor-pointer"
                            >
                              <RotateCcw className="w-3.5 h-3.5" />
                              <span>重置筛选</span>
                            </button>

                            <button
                              type="button"
                              onClick={onGoWrite}
                              className="wb-pressable inline-flex items-center gap-1.5 h-8 px-4 rounded-capsule bg-wb-text-primary text-wb-bg-page text-xs font-semibold shadow-sm hover:opacity-90 cursor-pointer"
                            >
                              <Edit3 className="w-3.5 h-3.5" />
                              <span>{searchQuery.trim() ? `发布关于 “${searchQuery.trim()}” 的新帖` : '发布首篇技术帖'}</span>
                            </button>
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </motion.div>
          )}
          {/* 移动端入站必读与自治公约快捷入口 */}
          <div className="lg:hidden py-4 text-center">
            <span
              onClick={onGoRules}
              className="text-xs text-wb-text-muted hover:text-wb-text-primary hover:underline underline-offset-4 cursor-pointer select-none inline-flex items-center gap-1"
            >
              <span>阅读极客自治公约与入站必读规范</span>
              <span>&rarr;</span>
            </span>
          </div>
          </>
        )}
      </div>

      {/* 右侧极客专属侧边栏：专业版与经典版共用真实后端数据（热议榜、社区榜单、指标看板、公约、友链） */}
      <aside className={`shrink-0 space-y-4 ${
        uiMode === 'pro'
          ? 'hidden xl:block w-72 sticky top-16'
          : 'hidden lg:block w-[300px] sticky top-20'
      }`}>
        {/* 1. 极客个人名片 & 签到卡片 */}
        <div className="p-5 rounded-3xl surface border border-wb-border-subtle shadow-card space-y-4 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:border-wb-border-strong/60">
              {user ? (
                <div className="space-y-3.5">
                  <div className="flex items-center justify-between">
                    <div
                      onClick={() => onSelectUser?.(user.username)}
                      className="flex items-center gap-3 cursor-pointer hover:opacity-85 transition-opacity"
                      title="查看与编辑个人主页"
                    >
                      <UserAvatar
                        username={user.username}
                        avatarUrl={user.avatar_url}
                        size={40}
                        className="border border-wb-border-subtle shadow-sm flex-shrink-0"
                      />
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-sm text-wb-text-primary hover:underline">
                            {user.nickname || user.username}
                          </span>
                          {user.nickname && (
                            <span className="text-2xs text-wb-text-muted font-mono">
                              @{user.username}
                            </span>
                          )}
                          <LevelBadge level={user.level} status={user.status} role={user.role} size="sm" />
                        </div>

                        <span className="text-2xs text-wb-text-muted font-mono">
                          已活跃 {user.active_days} 天 · {user.experience} EXP
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={handleCheckin}
                      disabled={hasCheckedInToday || checkingIn}
                      className={`wb-pressable flex-1 flex items-center justify-center gap-1.5 h-10 px-4 rounded-capsule text-xs font-semibold shadow-sm transition-all ${
                        hasCheckedInToday
                          ? 'bg-wb-bg-subtle text-wb-text-muted border border-wb-border-subtle cursor-default opacity-85'
                          : 'bg-wb-text-primary text-wb-bg-page hover:opacity-90 disabled:opacity-50 cursor-pointer'
                      }`}
                    >
                      {hasCheckedInToday ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-wb-success" />
                          <span>今日已打卡</span>
                        </>
                      ) : (
                        <>
                          <Calendar className="w-3.5 h-3.5" />
                          <span>{checkingIn ? '打卡中...' : '每日签到 (+5 EXP)'}</span>
                        </>
                      )}
                    </button>

                    <button
                      type="button"
                      onClick={onGoWrite}
                      title="发布新帖"
                      className="wb-pressable flex items-center justify-center gap-1.5 px-4 h-10 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>创作</span>
                    </button>
                  </div>
                </div>
              ) : (
                <div className="space-y-3 text-center py-2">
                  <div className="w-10 h-10 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle mx-auto flex items-center justify-center text-wb-text-primary font-bold">
                    ★
                  </div>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-wb-text-primary">加入极客社区</h3>
                    <p className="text-xs text-wb-text-muted">
                      崇尚代码与理性，探索前沿技术与纯粹美学
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={onRequireAuth}
                    className="wb-pressable w-full h-10 rounded-capsule bg-wb-text-primary text-wb-bg-page text-xs font-semibold hover:opacity-90 shadow-sm flex items-center justify-center"
                  >
                    通行证登录 / 注册
                  </button>
                </div>
              )}
            </div>

        {/* 社区榜单 */}
        <Leaderboard onSelectUser={onSelectUser} />
        <SeriesPanel onSelectPost={onSelectPost} />

        {/* 权限差异化侧边栏：仅最高管理员可见社区运行看板 */}
        {isSuperAdmin ? (
          <div className="p-5 rounded-3xl surface border border-wb-border-subtle shadow-card space-y-3.5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:border-wb-border-strong/60">
            <div className="flex items-center justify-between pb-2 border-b border-wb-border-subtle">
              <div className="flex items-center gap-2">
                <Activity className="w-4 h-4 text-wb-text-primary" />
                <h4 className="text-xs font-bold text-wb-text-primary tracking-wide uppercase">
                  社区运行看板 (管理专属)
                </h4>
              </div>
              <span className="flex items-center gap-1.5 text-3xs text-wb-success dark:text-wb-success font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-wb-success animate-pulse" />
                系统运转良好
              </span>
            </div>

            <div className="text-2xs text-wb-text-muted font-mono flex items-center justify-between">
              <span>当前管理权限:</span>
              <span className="font-semibold text-wb-text-primary">
                最高管理员 (Super Admin)
              </span>
            </div>

            {/* 3 项全新高质感布局：首行突出实时活跃极客心跳，次行双列并排帖子与成员 */}
            <div className="space-y-2.5">
              <div className="p-3.5 rounded-2xl bg-wb-bg-subtle/70 border border-wb-border-subtle flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-xl bg-wb-success/10 text-wb-success dark:text-wb-success flex items-center justify-center">
                    <Users className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="text-2xs text-wb-text-muted font-medium">在线极客</div>
                    <div className="flex items-center gap-1.5 text-3xs text-wb-success dark:text-wb-success font-mono">
                      <span className="w-1.5 h-1.5 rounded-full bg-wb-success animate-pulse" />
                      5 分钟内活跃
                    </div>
                  </div>
                </div>
                <div className="text-2xl font-bold font-mono text-wb-text-primary">
                  {telemetry.online_users}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <div className="p-3 rounded-2xl bg-wb-bg-subtle/70 border border-wb-border-subtle space-y-1">
                  <div className="flex items-center gap-1.5 text-wb-text-muted text-2xs">
                    <MessageSquare className="w-3.5 h-3.5" />
                    <span>社区帖子</span>
                  </div>
                  <div className="text-lg font-bold font-mono text-wb-text-primary">
                    {telemetry.total_posts}
                  </div>
                </div>

                <div className="p-3 rounded-2xl bg-wb-bg-subtle/70 border border-wb-border-subtle space-y-1">
                  <div className="flex items-center gap-1.5 text-wb-text-muted text-2xs">
                    <Shield className="w-3.5 h-3.5" />
                    <span>注册成员</span>
                  </div>
                  <div className="text-lg font-bold font-mono text-wb-text-primary">
                    {telemetry.total_users}
                  </div>
                </div>
              </div>
            </div>

            {onGoAdmin && (
              <button
                type="button"
                onClick={onGoAdmin}
                className="wb-pressable w-full h-10 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle hover:border-wb-border-strong text-wb-text-primary text-xs font-semibold flex items-center justify-center gap-2 transition-colors"
              >
                <Terminal className="w-3.5 h-3.5" />
                <span>进入管理与治理工作台</span>
                <ArrowUpRight className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        ) : (
          <>

            {/* 极客贡献先锋卡已移除，由社区榜单替代 */}
          </>
        )}


        {/* 3. 24小时热议精选榜 (Top Discussions) */}
        <div className="p-5 rounded-3xl surface border border-wb-border-subtle shadow-card space-y-3.5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:border-wb-border-strong/60">
          <div className="flex items-center gap-2 pb-2 border-b border-wb-border-subtle">
            <Flame className="w-4 h-4 text-wb-text-primary" />
            <h4 className="text-xs font-bold text-wb-text-primary tracking-wide uppercase">
              24小时热议精选
            </h4>
          </div>

          <div className="space-y-2.5">
            {(isSuperAdmin ? telemetry.top_hot_posts : (publicStats.top_hot_posts?.length ? publicStats.top_hot_posts : telemetry.top_hot_posts)).map((hotPost, index) => (
              <div
                key={hotPost.id}
                onClick={() => onSelectPost(hotPost.id)}
                className="wb-pressable cursor-pointer flex items-start gap-2.5 p-2 rounded-xl hover:bg-wb-bg-subtle transition-colors group"
              >
                <span
                  className={`w-5 h-5 rounded-md flex items-center justify-center font-mono text-xs font-bold flex-shrink-0 ${
                    index < 3
                      ? 'bg-wb-text-primary text-wb-bg-page'
                      : 'bg-wb-bg-subtle text-wb-text-muted'
                  }`}
                >
                  {index + 1}
                </span>
                <div className="flex-1 min-w-0 space-y-1">
                  <p className="text-xs font-medium text-wb-text-primary line-clamp-2 leading-snug group-hover:underline">
                    {getSmartDailyTitle(hotPost.title)}
                  </p>
                  <div className="flex items-center gap-3 text-3xs text-wb-text-muted font-mono">
                    <span>{hotPost.comment_count} 讨论</span>
                    <span>·</span>
                    <span>{hotPost.like_count} 赞</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 4. 极客公约与规则 (专业版已迁移至左侧工作台下方，经典版保留在右侧底部) */}
        {uiMode !== 'pro' && (
          <div className="p-5 rounded-3xl bg-wb-bg-subtle/50 border border-wb-border-subtle space-y-3 text-xs">
            <div className="flex items-center justify-between">
              <h4 className="font-semibold text-wb-text-primary flex items-center gap-1.5">
                <Shield className="w-3.5 h-3.5 text-wb-text-muted" />
                <span>极客自治公约</span>
              </h4>
              <span className="text-3xs text-wb-text-muted font-mono">v4.2 现行</span>
            </div>
            <p className="text-wb-text-muted text-2xs leading-relaxed">
              以实证与代码为基石，坚决抵制网络戾气与人身攻击。纯粹技术探讨，守护开源交流净土。
            </p>
            <div className="pt-2 border-t border-wb-border-subtle/60 flex items-center justify-between">
              <span className="text-3xs text-wb-text-muted font-mono">规范与入站必读</span>
              <span
                onClick={onGoRules}
                className="text-2xs text-wb-text-primary hover:text-wb-text-secondary hover:underline underline-offset-4 cursor-pointer font-medium select-none"
              >
                阅读完整公约与入站必读 &rarr;
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-3xs text-wb-text-muted font-mono">条款与声明</span>
              <span
                onClick={onGoLegal}
                className="text-2xs text-wb-text-primary hover:text-wb-text-secondary hover:underline underline-offset-4 cursor-pointer font-medium select-none"
              >
                免责与法律声明 &rarr;
              </span>
            </div>
          </div>
        )}

        {/* 5. 友情链接 (专业版已迁移至左侧工作台下方，经典版保留在右侧底部) */}
        {uiMode !== 'pro' && (
          <FriendLinksCard onOpenAll={onOpenLinks} />
        )}
      </aside>

      {/* 极客摸鱼老板键伪装层 (Boss Key Overlay: Alt+B 触发) */}
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
            <p className="text-zinc-500">[vite] transforming (2728/2869 modules) [========================================] 100%</p>
            <p className="text-emerald-400">✓ 2728 modules transformed in 1.48s</p>
            <p className="text-zinc-400 pt-2">&gt; docker compose -f deploy/prod.yml up -d</p>
            <p className="text-zinc-300">[+] Running 8/8</p>
            <p className="text-emerald-500"> ✔ Network cluster_vpc_default       Created (0.0s)</p>
            <p className="text-emerald-500"> ✔ Container auth-service-gw         Running (0.2s)</p>
            <p className="text-emerald-500"> ✔ Container postgres-primary        Healthy (0.4s)</p>
            <p className="text-emerald-500"> ✔ Container redis-sentinel-01       Healthy (0.1s)</p>
            <p className="text-emerald-500"> ✔ Container edge-api-gateway        Running (0.3s)</p>
            <p className="text-zinc-400 pt-2">[k8s-pod] sync state: OK | heap memory: 3.82GB | load avg: 0.28, 0.35, 0.41</p>
            <p className="text-zinc-500">Listening for incoming gRPC microservices connections on port 9090...</p>
          </div>
          <div className="text-3xs text-zinc-600 flex justify-between items-center border-t border-zinc-800 pt-2">
            <span>DEBUG SESSION ACTIVE. Press [Alt+B] or [Esc] to return to community.</span>
            <span>PID: 40912 | UPTIME: 14d 08h</span>
          </div>
        </div>
      )}
    </div>
  );
};
