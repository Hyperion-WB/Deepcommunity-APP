import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Navbar } from './components/Navbar';
import { AnnouncementTicker, type AnnouncementItem } from './components/AnnouncementTicker';
import { AnnouncementModal } from './components/AnnouncementModal';
import { SearchModal } from './components/SearchModal';
import { AuthModal } from './components/AuthModal';
import { FloatingBackToTop } from './components/FloatingBackToTop';
import { NotificationDrawer } from './components/NotificationDrawer';
import { BackgroundTextureLayer } from './components/BackgroundTextureLayer';
import { EasterEggs } from './components/EasterEggs';
import { EggUnlockOverlay } from './components/EggUnlockOverlay';
import { ForcePasswordChangeModal } from './components/ForcePasswordChangeModal';
import { ExternalLinkModal } from './components/ExternalLinkModal';
import { Home } from './pages/Home';
import { Links } from './pages/Links';
import { ToastProvider, useToast } from './components/Toast';
import { ConfirmProvider } from './components/ConfirmModal';
import { apiClient, broadcastLogout, refreshAccessToken } from './api/client';
import { GuestGate } from './components/GuestGate';
import { GameCommandBar } from './components/GameCommandBar';
import { CrazyThursdayOverlay } from './components/CrazyThursdayOverlay';
import type { UserProfile } from './types';
import { syncMutedFromCloud } from './utils/muted';
import { initCdn } from './utils/cdn';
import { CollectionModalHost } from './components/CollectionModalHost';
import { NotFoundGame } from './components/NotFoundGame';
import { FestivalCelebration, type FestivalPayload } from './components/FestivalCelebration';

// 路由级按需懒加载 (Code Splitting)，避免首页首包臃肿
const PostDetailPopup = React.lazy(() =>
  import('./pages/post-detail-popup').then((m) => ({ default: m.PostDetailPopup }))
);
const Editor = React.lazy(() => import('./pages/Editor').then((m) => ({ default: m.Editor })));
const Chat = React.lazy(() => import('./pages/Chat').then((m) => ({ default: m.Chat })));
const Profile = React.lazy(() => import('./pages/Profile').then((m) => ({ default: m.Profile })));
const Admin = React.lazy(() => import('./pages/Admin').then((m) => ({ default: m.Admin })));
const Feedback = React.lazy(() => import('./pages/Feedback').then((m) => ({ default: m.Feedback })));
const CommunityRules = React.lazy(() => import('./pages/CommunityRules').then((m) => ({ default: m.CommunityRules })));
const LegalDisclaimer = React.lazy(() => import('./pages/LegalDisclaimer').then((m) => ({ default: m.LegalDisclaimer })));
const Checkin = React.lazy(() => import('./pages/Checkin').then((m) => ({ default: m.Checkin })));

const PageFallback: React.FC = () => (
  <div className="py-24 flex flex-col items-center justify-center space-y-3">
    <div className="w-5 h-5 border-2 border-wb-text-primary border-t-transparent rounded-full animate-spin" />
    <span className="text-2xs text-wb-text-muted font-mono tracking-wider">LOADING MODULE...</span>
  </div>
);

// 可写入 URL 的页签白名单：刷新后仍停留在原页面
const DEMO_USER: UserProfile = {
  id: 'demo-preview-user',
  username: '汐爻',
  nickname: '秋风不予冬',
  email: 'xiyan@geek.community',
  avatar_url: '',
  bio: '崇尚代码与理性，探索前沿技术与纯粹美学。',
  role: 'super_admin',
  status: 'active',
  level: 6,
  experience: 203,
  active_days: 5,
  continuous_checkin_days: 5,
  has_checked_in_today: true,
  created_at: '2026-11-01T00:00:00Z',
  remaining_anon_quota: 10,
  followers_count: 128,
  following_count: 36,
  posts_count: 12,
};

const VALID_TABS = ['home', 'feedback', 'write', 'daily', 'chat', 'profile', 'admin', 'rules', 'legal', 'links', 'checkin'];

export const AppContent: React.FC = () => {
  const [user, setUser] = useState<UserProfile | null>(null);
  // 身份校验完成后才允许渲染游客门禁，避免已登录用户闪一下弹窗
  const [authChecked, setAuthChecked] = useState(false);
  void authChecked;
  const [currentTab, setCurrentTab] = useState<string>(() => {
    try {
      const tab = new URLSearchParams(window.location.search).get('tab') || '';
      if (VALID_TABS.includes(tab)) return tab;
    } catch {}
    return 'home';
  });
  const [editingDraftId, setEditingDraftId] = useState<string | null>(null);
  // 评论深链：通知中心点击、分享链接都写成 ?post=...&comment=...
  // 从 URL 初始化，刷新后依然能定位到那条评论
  // 通知中心每次点击都递增，保证重复点同一条评论也能重新定位
  const [commentFocusSeq, setCommentFocusSeq] = useState(0);
  // 一次性令牌：通知中心点评论类通知时设置，文章页消费后清空。
  // 用令牌而不是递增序号，是因为从首页点通知时文章页是「新挂载」的，
  // 组件内的 ref 会直接用新值初始化，导致「序号没变化」而无法触发开抽屉。
  const [commentsAutoOpenToken, setCommentsAutoOpenToken] = useState<number | null>(null);
  const [targetCommentId, setTargetCommentId] = useState<string | null>(() => {
    try {
      return new URLSearchParams(window.location.search).get('comment') || null;
    } catch {
      return null;
    }
  });
  // 支持 URL 参数深度链接（新开网页多看、直接访问文章）
  const [selectedPostId, setSelectedPostId] = useState<string | null>(() => {
    try {
      const params = new URLSearchParams(window.location.search);
      return params.get('post') || null;
    } catch {
      return null;
    }
  });

  // 记录首页滚动位置，防止返回时刷新到最开始的位置
  const homeScrollYRef = useRef<number>(0);

  // 当前页签写入 URL：用 replace 而不是 push，避免返回键被页签切换污染
  useEffect(() => {
    try {
      const url = new URL(window.location.href);
      if (currentTab && currentTab !== 'home') url.searchParams.set('tab', currentTab);
      else url.searchParams.delete('tab');
      if (url.toString() !== window.location.href) {
        window.history.replaceState({ tab: currentTab }, '', url.toString());
      }
    } catch {}
  }, [currentTab]);

  const handleSelectPost = (postId: string, commentId?: string | null, openComments = false) => {
    if (!selectedPostId && currentTab === 'home') {
      homeScrollYRef.current = window.scrollY;
    }
    setSelectedPostId(postId);
    setTargetCommentId(commentId || null);
    if (commentId) setCommentFocusSeq((n) => n + 1);
    if (openComments) setCommentsAutoOpenToken(Date.now());
    try {
      const url = new URL(window.location.href);
      url.searchParams.set('post', postId);
      if (commentId) url.searchParams.set('comment', commentId);
      else url.searchParams.delete('comment');
      window.history.pushState({ postId, commentId }, '', url.toString());
    } catch {}
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const handleBackFromPost = () => {
    setSelectedPostId(null);
    setTargetCommentId(null);
    try {
      const url = new URL(window.location.href);
      url.searchParams.delete('post');
      url.searchParams.delete('comment');
      // 页内返回用 replace：再 push 一条会让浏览器返回键把用户弹回文章
      window.history.replaceState({}, '', url.toString());
    } catch {}
    requestAnimationFrame(() => {
      window.scrollTo({ top: homeScrollYRef.current || 0, behavior: 'instant' });
    });
  };

  const handleGoRules = () => {
    setSelectedPostId(null);
    setCurrentTab('rules');
    try {
      const url = new URL(window.location.href);
      url.searchParams.set('tab', 'rules');
      url.searchParams.delete('post');
      window.history.pushState({ tab: 'rules' }, '', url.toString());
    } catch {}
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const handleGoLegal = () => {
    setSelectedPostId(null);
    setCurrentTab('legal');
    try {
      const url = new URL(window.location.href);
      url.searchParams.set('tab', 'legal');
      url.searchParams.delete('post');
      window.history.pushState({ tab: 'legal' }, '', url.toString());
    } catch {}
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const handleBackFromRules = () => {
    setCurrentTab('home');
    try {
      const url = new URL(window.location.href);
      url.searchParams.delete('tab');
      window.history.replaceState({}, '', url.toString());
    } catch {}
    requestAnimationFrame(() => {
      window.scrollTo({ top: homeScrollYRef.current || 0, behavior: 'instant' });
    });
  };

  const toast = useToast();
  const [festival, setFestival] = useState<FestivalPayload | null>(null);
  useEffect(() => {
    // Gitee 绑定回调：清掉 fragment 并提示，避免刷新后重复弹
    if (window.location.hash.startsWith('#gitee_bound=1')) {
      toast.success('Gitee 账号绑定成功');
      try { window.history.replaceState({}, '', window.location.pathname + window.location.search); } catch {}
    }
  }, []);

  useEffect(() => {
    void initCdn();
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      try {
        const params = new URLSearchParams(window.location.search);
        const postId = params.get('post');
        const tab = params.get('tab');
        const cid = params.get('comment') || null;
        setTargetCommentId(cid);
        // 前进/后退到带评论的链接时，抽屉内部靠 focusSeq 变化才会重新滚动定位
        if (cid) setCommentsAutoOpenToken((n) => (n ?? 0) + 1);
        if (postId) {
          setSelectedPostId(postId);
        } else {
          setSelectedPostId(null);
          // 返回/前进到任意合法 tab 都要同步页面状态，否则 URL 与内容脱节
          const validTabs = new Set([
            'home', 'chat', 'write', 'profile', 'admin', 'rules', 'legal',
            'feedback', 'links', 'search', 'notifications', 'checkin',
          ]);
          if (tab === 'rules') {
            setCurrentTab('rules');
          } else if (!tab) {
            setCurrentTab('home');
          } else if (validTabs.has(tab)) {
            setCurrentTab(tab);
          }
          requestAnimationFrame(() => {
            window.scrollTo({ top: homeScrollYRef.current || 0, behavior: 'instant' });
          });
        }
      } catch {}
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const [selectedUsername, setSelectedUsername] = useState<string | null>(null);
  const [initialChatPeer, setInitialChatPeer] = useState<{ id: string; username: string } | null>(null);
  const [initialGroupId, setInitialGroupId] = useState<string | null>(null);
  // 桌面通知点击后要打开的私聊会话
  const [initialConvId, setInitialConvId] = useState<string | null>(null);
  const [authModalOpen, setAuthModalOpen] = useState<boolean>(false);
  const [searchModalOpen, setSearchModalOpen] = useState(false);

  const handleGlobalSearch = (q: string, target: 'posts' | 'users') => {
    try {
      const url = new URL(window.location.href);
      url.searchParams.set('q', q);
      if (target === 'users') url.searchParams.set('stype', 'users');
      else url.searchParams.delete('stype');
      url.searchParams.delete('tab');
      window.history.pushState({ q }, '', url.toString());
    } catch {}
    setSelectedPostId(null);
    setCurrentTab('home');
    window.dispatchEvent(new CustomEvent('geek:trigger-search', { detail: { query: q, target } }));
  };



  // 最新登录态快照：异步回调（如登出事件）里读它，避免闭包拿到旧值
  const userRef = useRef<UserProfile | null>(null);

  // 登录态同步 + 兜底关窗：刷新时可能先弹登录框、随后自动登录，
  // 只要 user 有值就强制把弹窗收掉
  useEffect(() => {
    userRef.current = user;
    if (user) setAuthModalOpen(false);
  }, [user]);
  // 游客点「写帖 / 私聊」时记录目标页，登录成功后直接送达，免去再点一次
  const [pendingTab, setPendingTab] = useState<string | null>(null);
  const [notificationsOpen, setNotificationsOpen] = useState<boolean>(false);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [unreadChatCount, setUnreadChatCount] = useState<number>(0);
  // 聊天页常驻：第一次进入后保持挂载，切到别的 tab 只是隐藏。
  // 否则每次切回都会卸载重挂 → 重建 WebSocket + 全量重拉会话/群/消息，弱机明显卡顿。
  const [chatMounted, setChatMounted] = useState<boolean>(false);

  useEffect(() => {
    if (currentTab === 'chat') setChatMounted(true);
  }, [currentTab]);
  const [oauthError, setOauthError] = useState<string>('');

  // 多标签页未读同步：任一标签页更新未读数时，通过 BroadcastChannel 广播到其他标签页，
  // 消除此前最长 20 秒的轮询滞后
  const broadcastUnread = (notifUnread: number | null, chatUnread: number | null) => {
    try {
      if (typeof BroadcastChannel !== 'undefined') {
        if (!broadcastUnreadRef.current) {
          broadcastUnreadRef.current = new BroadcastChannel('unread_sync');
        }
        broadcastUnreadRef.current.postMessage({ notifUnread, chatUnread, src: tabIdRef.current });
      }
    } catch {}
  };
  const broadcastUnreadRef = useRef<BroadcastChannel | null>(null);
  const tabIdRef = useRef(Math.random().toString(36).slice(2));

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const ch = new BroadcastChannel('unread_sync');
    ch.onmessage = (e) => {
      const data = e.data || {};
      const { notifUnread, chatUnread } = data;
      // 忽略本标签自己发的广播（同源双实例会自收），避免旧值回跳
      if (!data || data.src === tabIdRef.current) return;
      if (typeof notifUnread === 'number') setUnreadCount(notifUnread);
      if (typeof chatUnread === 'number') setUnreadChatCount(chatUnread);
    };
    return () => ch.close();
  }, []);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [isAnnouncementCollapsed, setIsAnnouncementCollapsed] = useState<boolean>(false);
  const [announcementModalOpen, setAnnouncementModalOpen] = useState<boolean>(false);
  // 已确认（看过 / 主动收起）的最大公告 id：只有出现更大的 id 才算「有新公告」，
  // 不再按“每天”重置，避免用户确认过之后第二天又被弹一次
  const [ackedAnnouncementId, setAckedAnnouncementId] = useState<number>(() => {
    try {
      return (
        parseInt(
          localStorage.getItem('geek_announcement_acked_id') ||
            // 兼容旧版本记录：老用户此前的“收起/已读”状态继续生效，升级后不会被重复弹窗
            localStorage.getItem('geek_announcement_dismiss_latest_id') ||
            '0',
          10
        ) || 0
      );
    } catch {
      return 0;
    }
  });
  const ackedAnnouncementIdRef = useRef<number>(ackedAnnouncementId);
  useEffect(() => {
    ackedAnnouncementIdRef.current = ackedAnnouncementId;
  }, [ackedAnnouncementId]);

  const latestAnnouncementId =
    announcements.length > 0 ? Math.max(...announcements.map((a) => a.id)) : 0;
  const hasUnreadAnnouncement = latestAnnouncementId > ackedAnnouncementId;

  const fetchAnnouncements = async () => {
    try {
      const res = await apiClient.get('/admin/announcements/active');
      if (res.data && Array.isArray(res.data) && res.data.length > 0) {
        setAnnouncements(res.data);
      } else {
        const singleRes = await apiClient.get('/admin/announcement/current');
        if (singleRes.data && singleRes.data.content) {
          setAnnouncements([singleRes.data]);
        } else {
          setAnnouncements([]);
        }
      }
    } catch {
      setAnnouncements([]);
    }
  };

  useEffect(() => {
    fetchAnnouncements();
    const handleRefresh = () => fetchAnnouncements();
    window.addEventListener('announcement_updated', handleRefresh);
    return () => window.removeEventListener('announcement_updated', handleRefresh);
  }, []);

  // 核心生命周期：用户确认/收起后长期保持收起；只有出现「更大 id 的新公告」才重新展开
  // 只在公告 id 变化时触发，手动展开不会被这里立刻收回
  useEffect(() => {
    if (latestAnnouncementId === 0) return;
    setIsAnnouncementCollapsed(latestAnnouncementId <= ackedAnnouncementIdRef.current);
  }, [latestAnnouncementId]);

  // 确认公告：收起横幅并清掉角标（记录已确认的最大 id）
  const acknowledgeAnnouncements = () => {
    setIsAnnouncementCollapsed(true);
    if (latestAnnouncementId > 0) {
      setAckedAnnouncementId(latestAnnouncementId);
      try {
        localStorage.setItem('geek_announcement_acked_id', String(latestAnnouncementId));
        // 旧键同步写入，兼容回滚到旧版本时的判断逻辑
        localStorage.setItem('geek_announcement_dismiss_latest_id', String(latestAnnouncementId));
        localStorage.setItem('geek_announcement_dismiss_date', new Date().toISOString().slice(0, 10));
      } catch {}
    }
  };

  // 首页横幅右上角「收起」
  const handleCollapseAnnouncement = () => {
    acknowledgeAnnouncements();
  };

  // 弹窗底部「我知道了」：与收起行为一致，只是顺带关掉弹窗
  const handleAcknowledgeAnnouncement = () => {
    acknowledgeAnnouncements();
    setAnnouncementModalOpen(false);
  };

  const handleToggleAnnouncement = () => {
    if (selectedPostId || currentTab !== 'home') {
      // 在文章阅读或非首页时，直接弹窗展示公告与更新历史，避免跳走中断当前浏览
      setAnnouncementModalOpen(true);
    } else {
      // 首页点开：展开横幅即视为已查看，角标随之消失，但横幅保持展开到用户主动收起
      setIsAnnouncementCollapsed(false);
      if (latestAnnouncementId > 0 && latestAnnouncementId > ackedAnnouncementId) {
        setAckedAnnouncementId(latestAnnouncementId);
        try {
          localStorage.setItem('geek_announcement_acked_id', String(latestAnnouncementId));
        } catch {}
      }
    }
  };

  const fetchCurrentUser = async (): Promise<boolean> => {
    const token = localStorage.getItem('access_token');
    // 如果没有真实 token 或标记为演示，直接使用演示账号，确保本地预览畅通无阻
    if (!token) {
      setUser(DEMO_USER);
      setAuthChecked(true);
      return true;
    }
    try {
      const res = await apiClient.get('/auth/me');
      if (res.data && res.data.id) {
        setUser(res.data);
      // 账号级偏好同步到本地，Home 点击文章时无需再等接口
      try { localStorage.setItem('wb_open_post_in_new_tab', String(Boolean((res.data as any).open_post_in_new_tab))); } catch {}
        // 入站必读：每位用户仅在首次登录时自动展示一次（浏览器维度记住），
        // 之后仅通过导航主动点击进入，不再自动弹出
        try {
          if (!localStorage.getItem('geek_rules_seen_v1')) {
            localStorage.setItem('geek_rules_seen_v1', '1');
            // 带 ?post= / ?comment= / ?tab= 的分享链接是有明确目标的，
            // 不能被「首次登录看规则」覆盖，否则返回时会落到规则页。
            const params = new URLSearchParams(window.location.search);
            const hasDeepLink = Boolean(params.get('post') || params.get('comment') || params.get('tab'));
            if (!hasDeepLink) setCurrentTab('rules');
          }
        } catch {}
        return true;
      } else {
        setUser(null);
        return false;
      }
    } catch (err: any) {
      // 只有凭证确实失效（401）才清除登录态；
      // 超时/网关错误/5xx 保留 token，避免一次网络抖动就把用户静默登出
      if (err?.response?.status === 401) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        setUser(null);
      }
      return false;
    }
  };

  const fetchUnreadChatCount = async () => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      setUnreadChatCount(0);
      return;
    }
    try {
      // 本地免打扰设置随请求带上，服务端据此把这些会话/群的未读排除在红点之外
      let muted = '';
      try {
        const groups = JSON.parse(localStorage.getItem('geek_chat_muted_groups') || '[]');
        const convs = JSON.parse(localStorage.getItem('geek_chat_muted_convs') || '[]');
        muted = [...(Array.isArray(groups) ? groups : []), ...(Array.isArray(convs) ? convs : [])].join(',');
      } catch {}
      const res = await apiClient.get('/chat/unread-count', { params: muted ? { muted } : {} });
      if (typeof res.data?.unread_count === 'number') {
        setUnreadChatCount(res.data.unread_count);
        broadcastUnread(null, res.data.unread_count);
      }
    } catch {}
  };

  // 所有来源（App WS / Chat WS / 轮询 / 广播）都汇聚到这一个「重取服务端未读数」入口，
  // 避免本地 +1、服务端 GET、BroadcastChannel 三套口径互相覆盖导致红点抖动
  const lastUnreadRefreshRef = React.useRef(0);
  const refreshChatUnreadThrottled = () => {
    const now = Date.now();
    if (now - lastUnreadRefreshRef.current < 600) return;
    lastUnreadRefreshRef.current = now;
    void fetchUnreadChatCount();
  };

  const fetchUnreadNotificationsCount = async () => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      setUnreadCount(0);
      return;
    }
    try {
      const res = await apiClient.get('/users/me/notifications/unread-count');
      if (typeof res.data?.unread_count === 'number') {
        setUnreadCount(res.data.unread_count);
        broadcastUnread(res.data.unread_count, null);
      }
    } catch {}
  };

  useEffect(() => {
    // GitHub OAuth 回调：后端以 URL fragment (#) 回传令牌（不进服务器日志），
    // 此处落库后立即清除 hash，再拉取用户信息完成登录
    try {
      const hash = window.location.hash;
      // GitHub / Gitee 共用同一套回调约定：令牌放在 fragment，错误放在 *_error
      if (/#(github|gitee)_auth=1/.test(hash)) {
        const params = new URLSearchParams(hash.replace(/^#/, ''));
        const at = params.get('access_token');
        // refresh token 已改由后端 Set-Cookie（HttpOnly）下发，fragment 里只有 access token
        if (at) {
          localStorage.setItem('access_token', at);
        }
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
      } else if (/#(github|gitee)_error=/.test(hash)) {
        const params = new URLSearchParams(hash.replace(/^#/, ''));
        const err = params.get('github_error') || params.get('gitee_error');
        if (err) {
          setOauthError(decodeURIComponent(err));
        }
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
      }
    } catch {}

    void fetchCurrentUser().finally(() => setAuthChecked(true));
    // 利用浏览器空闲阶段静默预加载高频页面，彻底消除切页和打开文章时的加载等待感
    const runIdle = (window as any).requestIdleCallback || ((cb: any) => setTimeout(cb, 1200));
    runIdle(() => {
      import('./pages/Chat');
      import('./pages/Profile');
      import('./pages/Editor');
      import('./pages/Feedback');
    });
  }, []);

  // 令牌彻底失效（改密/封禁/刷新凭证过期）：保留当前地址，仅弹登录框，不做硬跳转
  useEffect(() => {
    const handleAuthExpired = async () => {
      // 先静默再试一次续签：偶发 401（并发刷新、网络抖动、后台标签页被节流）
      // 并不代表登录态真的失效。以前这里直接置空用户并弹登录框，
      // 表现就是「明明还登录着，突然弹出登录窗口」。
      try {
        const token = await refreshAccessToken();
        if (token) return;
      } catch {}
      // 再给一个极短的观察窗口：并发的另一个请求可能刚好续签成功，
      // 此时用户已经自动登录，不该再弹窗
      await new Promise((r) => setTimeout(r, 600));
      if (userRef.current) return;
      setUser(null);
      setAuthModalOpen(true);
    };
    window.addEventListener('auth:expired', handleAuthExpired);
    return () => window.removeEventListener('auth:expired', handleAuthExpired);
  }, []);

  useEffect(() => {
    if (user) {
      void syncMutedFromCloud();
      // 节日彩蛋：当天首次进入自动解锁并播放庆祝动画
      apiClient.get('/achievements/festival')
        .then((res) => {
          if (res.data?.active && res.data?.first_visit) setFestival(res.data);
        })
        .catch(() => undefined);
      fetchUnreadChatCount();
      fetchUnreadNotificationsCount();
      const timer = setInterval(() => {
        fetchUnreadChatCount();
        fetchUnreadNotificationsCount();
      }, 20000);
      return () => clearInterval(timer);
    } else {
      setUnreadChatCount(0);
      setUnreadCount(0);
    }
  }, [user]);

  // 页面访问埋点：供超级管理员「运维管理」面板统计 PV / UV / 热门页面
  useEffect(() => {
    const path = selectedPostId
      ? `/p/${selectedPostId}`
      : selectedUsername
        ? `/u/${selectedUsername}`
        : `/${currentTab || 'home'}`;
    apiClient.post('/ops/visit', { path }).catch(() => undefined);
  }, [currentTab, selectedPostId, selectedUsername]);

  // 多标签同步：另一个标签登录 / 退出 / 续签后，本标签的 user 也要跟着变，
  // 否则会出现「A 标签已退出，B 标签还显示着头像和通知」的错位。
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== 'access_token' && e.key !== null) return;
      if (!e.newValue) {
        setUser(null);
        setSelectedPostId(null);
        setTargetCommentId(null);
        return;
      }
      void fetchCurrentUser();
      void syncMutedFromCloud();
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 桌面通知：页面在后台时弹系统通知，点击回到站点并跳到对应聊天
  const pushDesktopNotice = React.useCallback(
    (title: string, body: string, onClick: () => void) => {
      try {
        if (typeof Notification === 'undefined') return;
        if (Notification.permission !== 'granted') return;
        // 前台浏览时只亮红点，不弹系统通知，避免打扰
        if (document.visibilityState === 'visible') return;
        const notice = new Notification(title, {
          body,
          icon: '/notify-icon.png',
          tag: 'deepcommunity-chat',
        });
        notice.onclick = () => {
          try {
            window.focus();
          } catch {}
          onClick();
          notice.close();
        };
      } catch {}
    },
    []
  );

  // 首次交互时申请通知权限（浏览器要求必须在用户手势内调用）
  useEffect(() => {
    if (typeof Notification === 'undefined') return;
    if (Notification.permission !== 'default') return;
    const ask = () => {
      void Notification.requestPermission();
      document.removeEventListener('click', ask, true);
    };
    document.addEventListener('click', ask, true);
    return () => document.removeEventListener('click', ask, true);
  }, []);

  // 通知实时通道：复用聊天用的 WS 端点，只处理 notification 帧。
  // 这样评论、@、点赞等提醒能立刻亮红点，不必等 20 秒轮询。
  useEffect(() => {
    if (!user) return;

    let closed = false;
    let ws: WebSocket | null = null;
    let retry = 0;
    let reconnectTimer: number | null = null;
    let pingTimer: number | null = null;
    // 最近一次收到服务端任何帧的时间：用于识别「半开连接」
    let lastSeen = Date.now();

    const scheduleReconnect = () => {
      if (closed) return;
      retry += 1;
      // 长期断网时降为低频兜底，既有上限也不会无限打后端
      const base = retry > 10 ? 60000 : Math.min(1000 * Math.pow(1.5, retry), 30000);
      const delay = Math.round(base * (0.8 + Math.random() * 0.4));
      reconnectTimer = window.setTimeout(connect, delay);
    };

    const connect = () => {
      if (closed) return;
      // 已有连接时直接复用：online / focus / visibility 都会触发补连，必须幂等
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
      // 关键：每次重连都重新读 token。401 自动续签 / 其他标签广播后，
      // localStorage 里的 access_token 已经换了，闭包里的旧值会被服务端拒绝
      const token = localStorage.getItem('access_token');
      if (!token) {
        scheduleReconnect();
        return;
      }
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      let socket: WebSocket;
      try {
        socket = new WebSocket(`${protocol}//${window.location.host}/api/chat/ws`, ['access_token', token]);
      } catch {
        scheduleReconnect();
        return;
      }
      // 持有本次连接的局部引用：旧连接迟到的 close 事件不能误伤新连接
      ws = socket;
      socket.onopen = () => {
        if (ws !== socket) return;
        retry = 0;
        lastSeen = Date.now();
        if (pingTimer) window.clearInterval(pingTimer);
        try {
          socket.send(JSON.stringify({ type: 'ping' }));
        } catch {}
        pingTimer = window.setInterval(() => {
          if (ws !== socket || socket.readyState !== WebSocket.OPEN) return;
          try {
            socket.send(JSON.stringify({ type: 'ping' }));
          } catch {}
          // 90 秒没有任何回帧才判定半开并重连，避免通知通道静默失联
          if (Date.now() - lastSeen > 90000) {
            try {
              socket.close();
            } catch {}
          }
        }, 25000);
      };
      socket.onmessage = (ev) => {
        lastSeen = Date.now();
        try {
          const data = JSON.parse(ev.data);
          if (data?.type === 'game_invite') {
            // 棋类对战邀请：刷新通知红点（邀请卡片在通知抽屉里，404 页不挂这条 WS）
            void fetchUnreadNotificationsCount();
          } else if (data?.type === 'game_state' || data?.type === 'game_end') {
            // 对局状态由 404 页自己轮询，这里只保持连接活跃
          } else if (data?.type === 'notification') {
            void fetchUnreadNotificationsCount();
          } else if (data?.type === 'mention' || data?.type === 'mention_all') {
            // 群聊被 @ / @全体成员：只有该群没设免打扰才弹桌面提醒（免打扰存在本地，服务端不知道）
            const isMentionAll = data.type === 'mention_all';
            let muted: string[] = [];
            try {
              muted = JSON.parse(localStorage.getItem('geek_chat_muted_groups') || '[]');
            } catch {}
            // 未读数无论是否免打扰都要刷新
            refreshChatUnreadThrottled();
            if (!muted.includes(data.room_id)) {
              pushDesktopNotice(
                isMentionAll
                  ? `${data.room_name || '群聊'} · ${data.sender || '管理员'} @全体成员`
                  : `${data.room_name || '群聊'} · ${data.sender || '有人'} @了你`,
                isMentionAll ? '群里有 @全体成员 的新消息' : data.snippet || '你被提及了',
                () => {
                  setCurrentTab('chat');
                  setInitialGroupId(data.room_id);
                }
              );
            }
          } else if (data?.type === 'new_msg') {
            // 私聊新消息：同样尊重会话级的免打扰设置
            let muted: string[] = [];
            try {
              muted = JSON.parse(localStorage.getItem('geek_chat_muted_convs') || '[]');
            } catch {}
            if (!data.conversation_id || !muted.includes(data.conversation_id)) {
              refreshChatUnreadThrottled();
              pushDesktopNotice(
                '收到一条私聊消息',
                data.content || '点此查看',
                () => {
                  setCurrentTab('chat');
                  if (data.conversation_id) setInitialConvId(data.conversation_id);
                }
              );
            }
          }
        } catch {}
      };
      socket.onclose = async (ev) => {
        // 已被新连接替换的旧 socket：迟到的 close 不能清掉新连接、也不能再触发重连
        if (ws !== socket) return;
        ws = null;
        if (pingTimer) {
          clearInterval(pingTimer);
          pingTimer = null;
        }
        if (closed) return;
        // 账号被拒：不再重连
        if (ev?.code === 4003) return;
        // 令牌失效：先触发一次 HTTP 请求让拦截器自动续签并写回 localStorage，再重连
        if (ev?.code === 4001) {
          try {
            await apiClient.get('/auth/me');
          } catch {}
        }
        if (closed) return;
        scheduleReconnect();
      };
      socket.onerror = () => {
        // 状态统一交给 onclose 处理，避免 error + close 双事件重复重连
        try {
          socket.close();
        } catch {}
      };
    };

    // 后台也保持通知连接（否则切走期间收不到提醒）；
    // 回到前台 / 网络恢复 / 窗口聚焦时立即补连，connect 内部有重复连接保护
    const ensureConnected = () => {
      if (closed) return;
      retry = 0;
      connect();
    };
    const onVisibility = () => {
      if (!document.hidden) ensureConnected();
    };
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('online', ensureConnected);
    window.addEventListener('focus', ensureConnected);

    connect();
    return () => {
      closed = true;
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('online', ensureConnected);
      window.removeEventListener('focus', ensureConnected);
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (pingTimer) clearInterval(pingTimer);
      const socket = ws;
      ws = null;
      if (socket) {
        try {
          socket.close();
        } catch {}
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const handleLogout = () => {
    // 通知后端吊销当前设备会话：refresh token 在 HttpOnly Cookie 里，由浏览器自动携带，
    // 后端顺手返回 Set-Cookie 清除它
    apiClient.post('/auth/logout', {}, { withCredentials: true }).catch(() => {});
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    // 同步其他标签页一起退出，避免它们拿已吊销的 refresh token 继续刷新
    broadcastLogout();
    setUser(null);
    setCurrentTab('home');
    // 退出登录后不应继续停在文章详情（游客态又看得到）
    setSelectedPostId(null);
    setTargetCommentId(null);
    // 常驻聊天组件必须卸载：否则它仍以隐藏状态挂载，
    // user 变 null 会触发 onRequireAuth → 刚登出就弹登录框，且保留上个账号数据
    setChatMounted(false);
    setInitialChatPeer(null);
    setInitialGroupId(null);
    setInitialConvId(null);
    setSelectedUsername(null);
    setPendingTab(null);
    setEditingDraftId(null);
    setCommentsAutoOpenToken(null);
    try {
      const url = new URL(window.location.href);
      url.searchParams.delete('post');
      url.searchParams.delete('comment');
      window.history.replaceState({}, '', url.toString());
    } catch {}
  };

  const handleOpenAuth = (targetTab?: string) => {
    if (targetTab) setPendingTab(targetTab);
    setAuthModalOpen(true);
  };

  const handleAuthSuccess = async () => {
    // 必须等 user 就绪再切页：Chat/Editor 挂载时若 user 仍为 null 会再次弹登录框
    const ok = await fetchCurrentUser();
    if (!ok || !pendingTab) return;
    setSelectedPostId(null);
    setCurrentTab(pendingTab);
    setPendingTab(null);
  };

  // 离开文章视图时统一清理 ?post=，避免地址栏与实际页面不一致（刷新又跳回旧文章）
  const clearPostQueryParam = () => {
    try {
      const url = new URL(window.location.href);
      if (url.searchParams.has('post') || url.searchParams.has('comment')) {
        url.searchParams.delete('post');
        url.searchParams.delete('comment');
        window.history.replaceState({}, '', url.toString());
      }
    } catch {}
  };

  const handleSelectUser = (username: string) => {
    clearPostQueryParam();
    setSelectedUsername(username);
    setSelectedPostId(null);
    setCurrentTab('profile');
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const handleStartChat = (targetUserId: string, targetUsername: string, groupId?: string) => {
    if (groupId) {
      setInitialGroupId(groupId);
      setInitialChatPeer(null);
    } else {
      setInitialChatPeer({ id: targetUserId, username: targetUsername });
      setInitialGroupId(null);
    }
    clearPostQueryParam();
    setSelectedPostId(null);
    setCurrentTab('chat');
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const pageKey = selectedPostId
    ? `post-${selectedPostId}`
    : currentTab === 'profile' && selectedUsername
    ? `profile-${selectedUsername}`
    : currentTab;

  return (
    // 背景交由 body 绘制，根容器透明，这样「背景质感」才能真正透出来
    // 注意：这里必须用 overflow-x-clip，不能用 overflow-x-hidden。
    // hidden 会把这一层变成滚动容器，导致内部所有 position: sticky（文章页左侧目录）失效，
    // 表现为「目录随页面一起滚走，必须回到顶部才能再次跳转」；clip 只裁剪不创建滚动容器。
    <div className="min-h-screen w-full max-w-full overflow-x-clip bg-transparent text-wb-text-primary flex flex-col antialiased">
      {/* 隐藏彩蛋触发器（不渲染 UI） */}
      <EasterEggs enabled={Boolean(user)} />
      <EggUnlockOverlay />

      {/* 背景动效层（仅「闪光弹」质感时渲染） */}
      <BackgroundTextureLayer />

      {/* 统一顶层悬浮导航栏 */}
      <Navbar
        user={user}
        currentTab={selectedPostId ? '' : currentTab}
        onSelectTab={(tab) => {
          setSelectedPostId(null);
          setTargetCommentId(null);
          try {
            const url = new URL(window.location.href);
            url.searchParams.delete('post');
            url.searchParams.delete('comment');
            // 切页签压一条站内历史：浏览器后退 / 鼠标侧键回到上一个页签，
            // 而不是直接离开站点。URL 里同步写好目标 tab，popstate 才能恢复。
            url.searchParams.set('tab', tab);
            window.history.pushState({ tab }, '', url.toString());
          } catch {}
          if (tab === 'profile') {
            setSelectedUsername(null);
          }
          setCurrentTab(tab);
          if (tab === 'home') {
            requestAnimationFrame(() => {
              window.scrollTo({ top: homeScrollYRef.current || 0, behavior: 'instant' });
            });
          } else {
            window.scrollTo({ top: 0, behavior: 'instant' });
          }
        }}
        onOpenAuth={handleOpenAuth}
        onLogout={handleLogout}
        onOpenNotifications={() => setNotificationsOpen(true)}
        onOpenSearch={() => setSearchModalOpen(true)}
        unreadCount={unreadCount}
        unreadChatCount={unreadChatCount}
        hasAnnouncements={announcements.length > 0}
        hasUnreadAnnouncement={hasUnreadAnnouncement}
        isAnnouncementCollapsed={isAnnouncementCollapsed}
        onToggleAnnouncement={handleToggleAnnouncement}
      />

      {/* 全站悬浮滚动公告栏 (仅在首页信息流展示，未收起时展开) */}
      {!selectedPostId && currentTab === 'home' && announcements.length > 0 && !isAnnouncementCollapsed && (
        <AnnouncementTicker
          announcements={announcements}
          isCollapsed={isAnnouncementCollapsed}
          isAdmin={['super_admin', 'admin'].includes(user?.role || '')}
          onOpenAdmin={() => setCurrentTab('admin')}
          onCollapse={handleCollapseAnnouncement}
          onOpenModal={() => setAnnouncementModalOpen(true)}
          onAcknowledge={handleAcknowledgeAnnouncement}
        />
      )}

      {/* 全站公告详情与更新历史弹窗 */}
      <AnnouncementModal
        isOpen={announcementModalOpen}
        onClose={() => setAnnouncementModalOpen(false)}
        onAcknowledge={handleAcknowledgeAnnouncement}
        activeAnnouncements={announcements}
      />

      {/* GitHub OAuth 回调错误横幅 */}
      {oauthError && (
        <div className="max-w-4xl mx-auto px-4 pt-3">
          <div className="p-3 rounded-2xl bg-wb-danger/10 border border-wb-danger/30 text-xs text-wb-danger dark:text-wb-danger flex items-center justify-between gap-3">
            <span>GitHub 登录失败：{oauthError}</span>
            <button onClick={() => setOauthError('')} className="font-semibold underline shrink-0">知道了</button>
          </div>
        </div>
      )}

      {/* 核心主视口: 极简丝滑无缝过渡 */}
      <main className="flex-1 pb-20 sm:pb-0 min-h-[calc(100dvh-120px)]">
        {/* 游客门禁已解除：允许用户直接跳过登录预览全站所有页面 */}
        {false && (
          <GuestGate
            onOpenAuth={() => setAuthModalOpen(true)}
            authOpen={authModalOpen}
            onGoRules={handleGoRules}
            onGoLegal={handleGoLegal}
          />
        )}
        {selectedPostId ? (
          <motion.div
            key={`post-${selectedPostId}`}
            initial={{ opacity: 0.92 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.12, ease: 'easeOut' }}
            className="w-full"
          >
            <React.Suspense fallback={<PageFallback />}>
              {/* 文章详情固定使用弹窗版式：右侧胶囊操作栏 + 评论侧边抽屉 */}
              <PostDetailPopup
                postId={selectedPostId}
                targetCommentId={targetCommentId}
                commentFocusSeq={commentFocusSeq}
                commentsAutoOpenToken={commentsAutoOpenToken}
                onConsumeAutoOpen={() => setCommentsAutoOpenToken(null)}
                user={user}
                onSelectPost={handleSelectPost}
                onBack={handleBackFromPost}
                onRequireAuth={() => setAuthModalOpen(true)}
                onSelectUser={handleSelectUser}
              />
            </React.Suspense>
          </motion.div>
        ) : null}

        {/* 首页组件保持持久挂载，返回时无重新加载白屏，完美保持滚动距离与列表状态 */}
        <div style={{ display: !selectedPostId && currentTab === 'home' ? 'block' : 'none' }}>
          <Home
            isActive={!selectedPostId && currentTab === 'home'}
            onSelectPost={handleSelectPost}
            user={user}
            onRequireAuth={() => setAuthModalOpen(true)}
            onGoWrite={() => setCurrentTab('write')}
            onGoWriteDaily={() => setCurrentTab('daily')}
            onGoAdmin={() => setCurrentTab('admin')}
            onRefreshUser={fetchCurrentUser}
            onSelectUser={handleSelectUser}
            onGoRules={handleGoRules}
            onGoLegal={handleGoLegal}
            onOpenLinks={() => setCurrentTab('links')}
          />
        </div>

        {/* 聊天页常驻挂载：第一次进入后保持，切到别的 tab 只是隐藏。
            否则每次切回都会卸载重挂 → 重建 WebSocket、重拉会话/群/消息，
            再加上首屏平滑滚动，弱机上就是「消息疯狂加载、界面卡顿」。 */}
        {chatMounted && (
          <div
            className="w-full"
            style={{ display: !selectedPostId && currentTab === 'chat' ? 'block' : 'none' }}
          >
            <React.Suspense fallback={<PageFallback />}>
              <Chat
                user={user}
                isActive={!selectedPostId && currentTab === 'chat'}
                onRequireAuth={() => setAuthModalOpen(true)}
                initialPeer={initialChatPeer}
                onClearInitialPeer={() => setInitialChatPeer(null)}
                initialGroupId={initialGroupId}
                onClearInitialGroupId={() => setInitialGroupId(null)}
                initialConvId={initialConvId}
                onClearInitialConvId={() => setInitialConvId(null)}
                onSelectUser={handleSelectUser}
                onUnreadChange={() => refreshChatUnreadThrottled()}
              />
            </React.Suspense>
          </div>
        )}

        {!selectedPostId && currentTab !== 'home' && (
          <motion.div
            key={pageKey}
            initial={{ opacity: 0.88 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.1, ease: 'easeOut' }}
            className="w-full"
          >
            <React.Suspense fallback={<PageFallback />}>
                            {currentTab === 'checkin' && (
                <Checkin
                  user={user}
                  onBack={() => setCurrentTab('home')}
                  onGoWrite={() => setCurrentTab('write')}
                  onRequireAuth={() => setAuthModalOpen(true)}
                  onRefreshUser={fetchCurrentUser}
                  onSelectUser={handleSelectUser}
                />
              )}

              {(currentTab === 'write' || currentTab === 'daily') && (
                <Editor
                  user={user}
                  initialMode={currentTab === 'daily' ? 'daily' : 'post'}
                  initialDraftId={editingDraftId}
                  onClearInitialDraftId={() => setEditingDraftId(null)}
                  onSuccess={(newId) => {
                    setEditingDraftId(null);
                    handleSelectPost(newId);
                  }}
                  onRequireAuth={() => setAuthModalOpen(true)}
                />
              )}
              {currentTab === 'profile' && (
                <Profile
                  user={user}
                  targetUsername={selectedUsername}
                  onBack={() => {
                    setSelectedUsername(null);
                    setCurrentTab('home');
                  }}
                  onRefresh={fetchCurrentUser}
                  onUserUpdated={(u) => setUser(u)}
                  onSelectPost={handleSelectPost}
                  onGoWrite={() => {
                    setEditingDraftId(null);
                    setCurrentTab('write');
                  }}
                  onEditDraft={(draftId) => {
                    setEditingDraftId(draftId);
                    setCurrentTab('write');
                  }}
                  onStartChat={handleStartChat}
                  onRequireAuth={() => setAuthModalOpen(true)}
                  onSelectUser={handleSelectUser}
                />
              )}
              {currentTab === 'feedback' && (
                <Feedback
                  user={user}
                  onRequireAuth={() => setAuthModalOpen(true)}
                  onSelectUser={handleSelectUser}
                />
              )}
              {currentTab === 'admin' && (
                <Admin user={user} onSelectUser={handleSelectUser} />
              )}
              {currentTab === 'links' && (
                <Links
                  user={user}
                  onBack={() => setCurrentTab('home')}
                  onRequireAuth={() => setAuthModalOpen(true)}
                />
              )}
              {currentTab === 'rules' && (
                <CommunityRules
                  user={user}
                  onBack={handleBackFromRules}
                  onRequireAuth={() => setAuthModalOpen(true)}
                  onSelectUser={handleSelectUser}
                  onGoLegal={handleGoLegal}
                />
              )}
              {currentTab === 'legal' && (
                <LegalDisclaimer onBack={handleBackFromRules} onGoRules={handleGoRules} canEdit={user?.role === 'super_admin'} />
              )}
            </React.Suspense>
          </motion.div>
        )}
      </main>

      {/* 一键返回顶部悬浮按钮 */}
      <FloatingBackToTop />

      {/* 登录/注册通行证弹窗 */}
      <AuthModal
        isOpen={authModalOpen}
        onClose={() => {
          setAuthModalOpen(false);
          // 主动关闭则丢弃未完成的跳转意图，避免下次登录被"意外"带到他页
          setPendingTab(null);
        }}
        onSuccess={handleAuthSuccess}
      />

      {/* 首次登录（仍使用初始口令）强制改密 */}
            <SearchModal
        isOpen={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
        onSearch={handleGlobalSearch}
      />

      <ForcePasswordChangeModal
        open={Boolean(user?.must_change_password)}
        onSuccess={() => {
          localStorage.removeItem('access_token');
          localStorage.removeItem('refresh_token');
          setUser(null);
          setAuthModalOpen(true);
        }}
      />

      {/* 节日限定勋章庆祝（当天首次进入触发一次） */}
      <FestivalCelebration data={festival} onClose={() => setFestival(null)} />

      {/* 消息与通知中心抽屉 */}
      <NotificationDrawer
        isOpen={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
        onSelectPost={(id, commentId, openComments) => {
          handleSelectPost(id, commentId, openComments);
          setNotificationsOpen(false);
        }}
        onSelectUser={handleSelectUser}
        onSelectChat={(username, groupId) => {
          setNotificationsOpen(false);
          handleStartChat('', username, groupId);
        }}
        onNotificationsChange={(cnt) => { setUnreadCount(cnt); broadcastUnread(cnt, null); }}
        selfUsername={user?.username}
        selfUserId={user?.id}
      />

      {/* 站外安全跳转拦截与确认模态框 */}
      <ExternalLinkModal />
    </div>
  );
};

export const App: React.FC = () => {
  // 游戏入口改为「明确的命令上下文」：只有显式打开命令条并回车才跳转。
  // 放在 App 顶层保证任意页面都能唤起；正文里写 404 / game 不会误触发。
  const [cmdOpen, setCmdOpen] = useState(false);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = document.activeElement as HTMLElement | null;
      const tag = el?.tagName;
      const inField = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el?.isContentEditable;
      // Ctrl/⌘ + K：任何位置都能打开命令条
      if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setCmdOpen((v) => !v);
        return;
      }
      // 非输入态按 / 进入命令模式；输入框里的 "/" 是正常打字，不拦截
      if (!inField && !e.ctrlKey && !e.metaKey && !e.altKey && e.key === '/') {
        e.preventDefault();
        setCmdOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // 游戏大厅的正式入口固定为 https://站点/404（nginx 的 try_files 会把它回落到 index.html，
  // 地址栏仍保留 /404）。其它未知路径也继续渲染游戏页，进入后由 NotFoundGame
  // 把地址栏规范成 /404，避免"随便输个错路径才能进游戏"。
  const path = window.location.pathname;
  if (path !== '/' && path !== '/index.html') {
    return <><NotFoundGame /><CrazyThursdayOverlay /></>;
  }
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AppContent />
        <CollectionModalHost />
        <CrazyThursdayOverlay />
        <GameCommandBar open={cmdOpen} onClose={() => setCmdOpen(false)} />
      </ConfirmProvider>
    </ToastProvider>
  );
};

export default App;
