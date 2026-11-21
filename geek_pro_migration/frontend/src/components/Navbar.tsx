import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import {
  Compass,
  Search,
  Calendar,
  PenTool,
  MessageSquare,
  ShieldCheck,
  LogOut,
  User as UserIcon,
  Bell,
  Newspaper,
  Lightbulb,
  Volume2,
  ArrowLeft,
  Monitor,
  Layout,
} from 'lucide-react';
import { ThemeCapsule } from './ThemeCapsule';
import { UserAvatar } from './UserAvatar';
import { OnlineAvatar } from './OnlineAvatar';
import { useScrollVisibility } from '../hooks/useScrollVisibility';
import type { UserProfile } from '../types';
import { bumpEgg } from '../utils/eggs';
import { useUiMode } from '../utils/uiMode';
export const reportLogoClick = () => bumpEgg('logo', 10, 'egg_logo_tapper');

interface Props {
  user: UserProfile | null;
  currentTab: string;
  onSelectTab: (tab: string) => void;
  onOpenAuth: (pendingTab?: string) => void;
  onLogout: () => void;
  onOpenNotifications?: () => void;
  unreadCount?: number;
  unreadChatCount?: number;
  hasAnnouncements?: boolean;
  /** 是否存在尚未确认的新公告（决定黑色小角标是否显示） */
  hasUnreadAnnouncement?: boolean;
  isAnnouncementCollapsed?: boolean;
  onToggleAnnouncement?: () => void;
  onOpenSearch?: () => void;
}

export const Navbar: React.FC<Props> = ({
  user,
  currentTab,
  onSelectTab,
  onOpenAuth,
  onLogout,
  onOpenNotifications,
  unreadCount = 0,
  unreadChatCount = 0,
  hasAnnouncements = false,
  hasUnreadAnnouncement = false,
  isAnnouncementCollapsed = false,
  onToggleAnnouncement,
  onOpenSearch,
}) => {
  const isSuperAdmin = user?.role === 'super_admin' || user?.role === 'admin';
  const isAdmin = ['super_admin', 'admin', 'moderator', 'auditor'].includes(user?.role || '');

  // 桌面端导航列表
  const navItems = [
    { key: 'home', label: '发现', icon: Compass },
    { key: 'feedback', label: '反馈', icon: Lightbulb },
    { key: 'write', label: '写帖', icon: PenTool },
  ];

  if (isSuperAdmin) {
    navItems.push({ key: 'daily', label: '日报', icon: Newspaper });
  }

  navItems.push({ key: 'chat', label: '私聊', icon: MessageSquare });

  if (isAdmin) {
    navItems.push({ key: 'admin', label: '管理', icon: ShieldCheck });
  }

  // 移动端核心导航配置
  const mobileNavItems: {
    key: string;
    label: string;
    icon: any;
    requiresAuth: boolean;
    badge?: number;
  }[] = [
    { key: 'home', label: '发现', icon: Compass, requiresAuth: false },
    { key: 'feedback', label: '反馈', icon: Lightbulb, requiresAuth: false },
    { key: 'write', label: '写帖', icon: PenTool, requiresAuth: true },
    { key: 'chat', label: '私聊', icon: MessageSquare, requiresAuth: true, badge: unreadChatCount },
    {
      key: 'profile',
      label: user ? '我的' : '登录',
      icon: user ? null : UserIcon,
      requiresAuth: !user,
    },
  ];


  const activeMobileIndex = mobileNavItems.findIndex((item) => item.key === currentTab);
  const hasActiveMobile = activeMobileIndex !== -1;

  const [uiMode, setUiMode] = useUiMode();
  const currentMode: string = uiMode;
  const navHidden = useScrollVisibility();
  // 滚动后加深投影：内容顶到导航下方时，靠一道柔和暗影把两者分开
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);
  // 文章读完（滚到底）时把底栏带回来，否则用户只能退出详情才能换频道
  // Logo 彩蛋：每点 5 次转一圈，累计 10 次解锁「连点 Logo」彩蛋
  const logoClickRef = useRef(0);
  const [logoSpins, setLogoSpins] = useState(0);
  const handleLogoClick = () => {
    reportLogoClick();
    logoClickRef.current += 1;
    if (logoClickRef.current % 5 === 0) setLogoSpins((n) => n + 1);
  };
  const [atArticleEnd, setAtArticleEnd] = useState(false);
  useEffect(() => {
    const onEnd = (e: Event) => setAtArticleEnd(Boolean((e as CustomEvent).detail));
    window.addEventListener('geek:article-bottom', onEnd as EventListener);
    return () => window.removeEventListener('geek:article-bottom', onEnd as EventListener);
  }, []);
  useEffect(() => {
    setAtArticleEnd(false);
  }, [currentTab]);
  // 编辑器也必须保留底栏，否则页面内无法切换到其他频道；
  // 详情页只在读到底部时出现，其余时候让位给悬浮评论栏，避免两层叠加。
  const showDockBar = currentTab !== '' || atArticleEnd;


  return (
    <>
      {/* 顶部导航条：专业版使用传统全宽通栏社区顶栏，经典版使用悬浮胶囊 */}
      <header
        className={
          uiMode === 'pro'
            ? `sticky top-0 z-50 w-full bg-wb-bg-surface/95 dark:bg-zinc-900/95 backdrop-blur-md border-b border-wb-border-subtle transition-all duration-200 ${
                scrolled ? 'shadow-xs' : ''
              }`
            : 'sticky top-2.5 sm:top-3.5 z-50 max-w-5xl mx-auto px-2 sm:px-4 w-full'
        }
      >
        <div
          className={
            uiMode === 'pro'
              ? 'max-w-7xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between gap-4 w-full'
              : `flex items-center justify-between gap-1.5 sm:gap-2 px-2.5 sm:px-3.5 py-1.5 frost rounded-capsule max-w-full transition-shadow duration-300 ${
                  scrolled
                    ? 'border border-wb-border-subtle shadow-[0_10px_30px_-14px_rgba(15,23,42,0.28)] dark:shadow-[0_12px_34px_-14px_rgba(0,0,0,0.7)]'
                    : 'border border-wb-border-subtle/60 shadow-none'
                }`
          }
        >
          {uiMode === 'pro' ? (
            /* 专业版定制通栏导航（像素级还原设计规范） */
            currentTab === 'checkin' ? (
              /* 每日签到专属顶栏：[← 返回首页] [C] COMMUNITY | 每日签到 ...... [🔔] [👤] */
              <div className="flex items-center justify-between w-full">
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => onSelectTab('home')}
                    className="wb-pressable flex items-center gap-1.5 h-8 px-3 rounded-full bg-white border border-gray-200 hover:border-gray-300 text-gray-700 hover:text-black text-xs font-medium transition-colors select-none shadow-2xs"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>返回首页</span>
                  </button>
                  <div className="flex items-center gap-2 pl-2">
                    <div className="w-6 h-6 rounded-md bg-[#18181b] text-white flex items-center justify-center font-bold text-xs select-none">
                      C
                    </div>
                    <span className="font-bold text-xs tracking-wider text-black font-mono select-none">
                      COMMUNITY
                    </span>
                    <span className="text-gray-300 text-xs select-none">|</span>
                    <span className="text-xs font-semibold text-gray-800 select-none">每日签到</span>
                  </div>
                </div>

                <div className="flex items-center gap-2.5">
                  {/* 全站公告入口 */}
                  {hasAnnouncements && (
                    <button
                      type="button"
                      onClick={onToggleAnnouncement}
                      title={hasUnreadAnnouncement ? '有新的社区公告 (点击查看)' : '全站公告与更新日志'}
                      className="relative w-8 h-8 rounded-full flex items-center justify-center text-gray-500 hover:text-black hover:bg-gray-100 transition-colors cursor-pointer"
                    >
                      <Volume2 className="w-4 h-4" />
                      {hasUnreadAnnouncement && (
                        <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 animate-pulse" />
                      )}
                    </button>
                  )}
                  {/* 主题切换 */}
                  <ThemeCapsule />
                  {/* 消息通知 */}
                  <button
                    type="button"
                    onClick={onOpenNotifications}
                    title="系统与互动通知"
                    className="relative w-8 h-8 rounded-full flex items-center justify-center text-gray-500 hover:text-black hover:bg-gray-100 transition-colors cursor-pointer"
                  >
                    <Bell className="w-4 h-4" />
                    {unreadCount > 0 && (
                      <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 ring-2 ring-white animate-pulse" />
                    )}
                  </button>
                  <div
                    onClick={() => onSelectTab('profile')}
                    className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center cursor-pointer overflow-hidden border border-gray-100 hover:opacity-85 transition-opacity"
                    title={user ? user.nickname || user.username : '登录'}
                  >
                    {user?.avatar_url ? (
                      <img src={user.avatar_url} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <UserIcon className="w-4 h-4 text-gray-500" />
                    )}
                  </div>
                </div>
              </div>
            ) : (
              /* 专业版主页与常规导航：Logo 居左，主导航与搜索框居中，右侧包含[公告/主题/通知/签到/模式切换] */
              <div className="flex items-center justify-between gap-4 w-full">
                {/* 左侧：Logo 标准居左 */}
                <div
                  onClick={handleLogoClick}
                  title="COMMUNITY 深度极客社区"
                  className="flex items-center gap-1.5 sm:gap-2 cursor-pointer select-none group shrink-0"
                >
                  <div className="w-7 h-7 rounded-lg bg-wb-text-primary text-wb-bg-page flex items-center justify-center font-bold text-xs select-none shadow-xs group-hover:scale-105 transition-transform">
                    C
                  </div>
                  <span className="font-extrabold text-xs tracking-wider text-wb-text-primary font-mono select-none hidden sm:inline">
                    COMMUNITY
                  </span>
                </div>

                {/* 中间：桌面端主导航 Tabs + 弹性搜索条 (移动端隐藏，统一通过底栏浮岛访问，杜绝顶栏溢出) */}
                <div className="hidden md:flex items-center gap-3 flex-1 max-w-2xl justify-center">
                  <nav className="flex items-center gap-1 select-none shrink-0">
                    {navItems.map((item) => {
                      const Icon = item.icon;
                      const isActive = currentTab === item.key;
                      return (
                        <button
                          key={item.key}
                          type="button"
                          onClick={() => {
                            if (!user && (item.key === 'write' || item.key === 'chat')) {
                              onOpenAuth(item.key);
                            } else {
                              onSelectTab(item.key);
                            }
                          }}
                          className={`relative h-8 px-3 rounded-md text-xs font-medium flex items-center gap-1.5 transition-all select-none whitespace-nowrap cursor-pointer ${
                            isActive
                              ? 'bg-wb-text-primary text-wb-bg-page font-bold shadow-xs'
                              : 'text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle'
                          }`}
                        >
                          <Icon className="w-3.5 h-3.5" />
                          <span>{item.label}</span>
                          {item.key === 'chat' && unreadChatCount > 0 && (
                            <span className="w-2 h-2 rounded-full bg-red-500 absolute top-1.5 right-1.5 animate-pulse" />
                          )}
                        </button>
                      );
                    })}
                  </nav>

                  {/* 全局搜索栏：自然集成在导航右侧 */}
                  <button
                    type="button"
                    onClick={onOpenSearch}
                    className="hidden xl:flex items-center gap-2 h-8 px-3 rounded-full bg-wb-bg-subtle hover:bg-wb-border-subtle/50 border border-wb-border-subtle text-wb-text-muted hover:text-wb-text-primary text-xs transition-colors select-none cursor-pointer flex-1 max-w-[220px]"
                    title="搜索帖子、技术讨论与极客 (Ctrl+K)"
                  >
                    <Search className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                    <span className="truncate text-3xs">搜索讨论与帖子...</span>
                    <kbd className="font-mono text-3xs px-1 py-0.5 rounded bg-wb-bg-surface border border-wb-border-subtle text-wb-text-muted shrink-0 shadow-2xs">
                      Ctrl K
                    </kbd>
                  </button>
                </div>

                {/* 右侧：虚拟全站公告 (模式切换左侧) + 模式切换 + 公告 + 主题 + 单一通知 + 签到按钮 + 头像 */}
                <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
                  {/* 虚拟全站公告：放置在“专业/经典版切换”左侧，使信息层级自然过渡 */}
                  <div
                    onClick={onToggleAnnouncement}
                    className="hidden lg:flex items-center gap-1.5 h-8 px-2.5 rounded-full bg-amber-500/10 hover:bg-amber-500/15 border border-amber-500/20 text-amber-600 dark:text-amber-400 text-3xs font-medium cursor-pointer transition-colors max-w-[270px] shrink-0 select-none group"
                    title="点击查看全站公告与更新日志"
                  >
                    <span className="relative flex h-2 w-2 shrink-0">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500" />
                    </span>
                    <Volume2 className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                    <span className="truncate group-hover:underline">【全站公告】极客社区 V2.0 升级上线</span>
                    <span className="px-1 py-0.2 rounded bg-amber-500/20 text-3xs font-mono font-bold shrink-0">NEW</span>
                  </div>

                  {/* 模式切换：精致单图标切换器 (占用空间小、美观极简，点击即在专业版与经典版之间平滑切换) */}
                  <button
                    type="button"
                    onClick={() => setUiMode(currentMode === 'pro' ? 'classic' : 'pro')}
                    title={
                      currentMode === 'pro'
                        ? '当前：专业版门户 · 点击切换至经典版悬浮布局'
                        : '当前：经典版悬浮 · 点击切换至专业版门户布局'
                    }
                    className="relative w-8 h-8 rounded-md flex items-center justify-center text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle border border-wb-border-subtle transition-all cursor-pointer group shrink-0"
                  >
                    {currentMode === 'pro' ? (
                      <Monitor className="w-4 h-4 text-emerald-500 group-hover:scale-110 transition-transform" />
                    ) : (
                      <Layout className="w-4 h-4 text-blue-500 group-hover:scale-110 transition-transform" />
                    )}
                    {/* 微型极客模式指示灯 */}
                    <span
                      className={`absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full border border-wb-bg-surface ${
                        currentMode === 'pro' ? 'bg-emerald-500' : 'bg-blue-500'
                      }`}
                    />
                  </button>

                  {/* 1. 公告入口 */}
                  {hasAnnouncements && (
                    <button
                      type="button"
                      onClick={onToggleAnnouncement}
                      title={hasUnreadAnnouncement ? '有新的社区公告 (点击查看)' : '全站公告与更新日志'}
                      className="relative w-8 h-8 rounded-md flex items-center justify-center text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
                    >
                      <Volume2 className="w-4 h-4" />
                      {hasUnreadAnnouncement && (
                        <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 animate-pulse" />
                      )}
                    </button>
                  )}

                  {/* 2. 主题切换器 */}
                  <ThemeCapsule />

                  {/* 3. 消息通知铃铛（唯一一个） */}
                  <button
                    type="button"
                    onClick={onOpenNotifications}
                    title="查看系统与互动消息"
                    className="relative w-8 h-8 rounded-md flex items-center justify-center text-wb-text-secondary hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors cursor-pointer"
                  >
                    <Bell className="w-4 h-4" />
                    {unreadCount > 0 && (
                      <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 ring-2 ring-wb-bg-surface animate-pulse" />
                    )}
                  </button>

                  {/* 4. 每日签到按钮 (桌面端展示，移动端由底部 Dock 与个人页统一承载以防顶栏挤压误触) */}
                  <button
                    type="button"
                    onClick={() => onSelectTab('checkin')}
                    className="hidden sm:flex items-center gap-1.5 h-8 px-2.5 sm:px-3 rounded-md bg-wb-text-primary hover:opacity-90 text-wb-bg-page text-xs font-semibold transition-opacity cursor-pointer select-none shadow-xs"
                    title="进入每日签到与榜单"
                  >
                    <Calendar className="w-3.5 h-3.5" />
                    <span>签到</span>
                  </button>

                  {/* 5. 个人头像 / 登录 */}
                  <div
                    onClick={() => {
                      if (!user) onOpenAuth();
                      else onSelectTab('profile');
                    }}
                    className="w-8 h-8 rounded-full bg-wb-bg-subtle flex items-center justify-center cursor-pointer overflow-hidden border border-wb-border-subtle hover:border-wb-text-primary transition-colors shrink-0"
                    title={user ? user.nickname || user.username : '通行证登录'}
                  >
                    {user?.avatar_url ? (
                      <img src={user.avatar_url} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <UserIcon className="w-4 h-4 text-wb-text-muted" />
                    )}
                  </div>
                </div>
              </div>
            )
          ) : (
            /* 经典版保留完全原汁原味的悬浮胶囊 NavBar */
            <>
              {/* 左侧：Logo */}
              <div
                onClick={handleLogoClick}
                title="点一点试试"
                className="flex items-center gap-1.5 sm:gap-2 cursor-pointer select-none group shrink-0"
              >
                <img loading="lazy" decoding="async"
                  key={`logo-${logoSpins}`}
                  src="/logo.svg"
                  alt="Logo"
                  className={`w-7 h-7 rounded-xl object-contain transition-transform duration-200 group-hover:scale-105 ${logoSpins > 0 ? 'wb-logo-spin' : ''}`}
                />
                <span className="font-bold text-xs tracking-wider text-wb-text-primary font-mono select-none hidden md:inline">
                  COMMUNITY
                </span>
              </div>

              {/* 桌面端核心导航: 严格统一 h-9 胶囊 */}
              <nav className="hidden sm:flex items-center h-9 p-1 bg-wb-capsule-track rounded-capsule border border-wb-border-subtle relative select-none flex-shrink-0">
                {navItems.map((item) => {
                  const Icon = item.icon;
                  const isActive = currentTab === item.key;
                  return (
                    <button
                      key={item.key}
                      type="button"
                      onClick={() => {
                        if (!user && (item.key === 'write' || item.key === 'chat')) {
                          onOpenAuth(item.key);
                        } else {
                          onSelectTab(item.key);
                        }
                      }}
                      className={`relative z-10 h-7 px-3 text-xs font-medium rounded-capsule transition-colors duration-200 flex items-center gap-1.5 select-none whitespace-nowrap flex-shrink-0 ${
                        isActive
                          ? 'text-wb-text-primary font-semibold'
                          : 'text-wb-text-secondary hover:text-wb-text-primary'
                      }`}
                    >
                      {isActive && (
                        <motion.div
                          layoutId="navbar-pill-desktop"
                          transition={{ type: 'tween', duration: 0.22, ease: 'easeOut' }}
                          className="absolute inset-0 bg-wb-capsule-thumb rounded-capsule shadow-capsule -z-10"
                        />
                      )}
                      <div className="relative flex items-center justify-center flex-shrink-0">
                        <Icon className="w-3.5 h-3.5" />
                        {item.key === 'chat' && unreadChatCount > 0 && (
                          <span className="absolute -top-1 -right-1 flex h-2 w-2 pointer-events-none">
                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-wb-danger opacity-75" />
                            <span className="relative inline-flex rounded-full h-2 w-2 bg-wb-danger ring-2 ring-wb-bg-surface" />
                          </span>
                        )}
                      </div>
                      <span className="whitespace-nowrap">{item.label}</span>
                    </button>
                  );
                })}
              </nav>

              {/* 右侧交互区: 公告收纳球 + 主题切换 + 消息中心 + 用户头像 */}
              <div className="flex items-center gap-1 sm:gap-2 shrink-0">
                {hasAnnouncements && (isAnnouncementCollapsed || hasUnreadAnnouncement) && (
                  <motion.button
                    type="button"
                    initial={{ scale: 0.6, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    exit={{ scale: 0.6, opacity: 0 }}
                    whileHover={{ scale: 1.08 }}
                    whileTap={{ scale: 0.94 }}
                    onClick={onToggleAnnouncement}
                    title={
                      hasUnreadAnnouncement
                        ? '有新的社区公告 (点击查看)'
                        : '全站公告与更新日志 (点击展开)'
                    }
                    className="wb-pressable relative flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary transition-colors shrink-0 cursor-pointer"
                  >
                    <Volume2 className="w-3.5 h-3.5 text-wb-text-primary" />
                    {hasUnreadAnnouncement && (
                      <span className="absolute top-1.5 right-1.5 sm:top-2 sm:right-2 flex h-2 w-2 pointer-events-none">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-wb-text-primary opacity-40" />
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-wb-text-primary" />
                      </span>
                    )}
                  </motion.button>
                )}

                {/* 顶栏快捷模式切换：精致单图标切换器 (极简不占空间，点击一键切换专业/经典版) */}
                <button
                  type="button"
                  onClick={() => setUiMode(currentMode === 'pro' ? 'classic' : 'pro')}
                  title={
                    currentMode === 'pro'
                      ? '当前：专业版门户 · 点击切换至经典版悬浮布局'
                      : '当前：经典版悬浮 · 点击切换至专业版门户布局'
                  }
                  className="wb-pressable relative flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary transition-all shrink-0 cursor-pointer group"
                >
                  {currentMode === 'pro' ? (
                    <Monitor className="w-4 h-4 text-emerald-500 group-hover:scale-110 transition-transform" />
                  ) : (
                    <Layout className="w-4 h-4 text-blue-500 group-hover:scale-110 transition-transform" />
                  )}
                  {/* 微型极客模式指示灯 */}
                  <span
                    className={`absolute top-1 right-1 sm:top-1.5 sm:right-1.5 w-1.5 h-1.5 rounded-full ${
                      currentMode === 'pro' ? 'bg-emerald-500' : 'bg-blue-500'
                    }`}
                  />
                </button>

                <ThemeCapsule />

                {user && (
                  <button
                    type="button"
                    onClick={onOpenNotifications}
                    title="查看系统与互动消息"
                    className="wb-pressable relative flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary transition-colors shrink-0"
                  >
                    <Bell className="w-3.5 h-3.5" />
                    {unreadCount > 0 && (
                      <span className="absolute top-1.5 right-1.5 sm:top-2 sm:right-2 w-2 h-2 rounded-full bg-wb-danger ring-2 ring-wb-bg-surface animate-pulse" />
                    )}
                  </button>
                )}

                {user ? (
                  <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => onSelectTab('profile')}
                      title="查看与修改个人档案"
                      className={`wb-pressable flex items-center justify-center sm:gap-2 h-8 w-8 sm:h-9 sm:w-auto sm:px-2.5 rounded-capsule border transition-colors select-none ${
                        currentTab === 'profile'
                          ? 'bg-wb-capsule-track border-wb-border-strong text-wb-text-primary font-semibold'
                          : 'bg-wb-bg-subtle border-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary'
                      }`}
                    >
                      <OnlineAvatar online size={22} ringClassName="ring-wb-bg-page">
                        <UserAvatar username={user.username} avatarUrl={user.avatar_url} size={22} />
                      </OnlineAvatar>
                      <span className="text-xs max-w-[80px] truncate hidden md:inline font-medium">
                        {user.nickname || user.username}
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={onLogout}
                      title="退出登录"
                      aria-label="退出登录"
                      className="wb-pressable flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-muted hover:text-wb-danger hover:border-wb-danger/30 transition-colors shrink-0"
                    >
                      <LogOut className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => onOpenAuth()}
                    className="wb-pressable flex items-center justify-center h-8 sm:h-9 px-3 sm:px-4 text-xs font-semibold rounded-capsule bg-wb-text-primary text-wb-bg-page hover:opacity-90 shadow-sm"
                  >
                    登录
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </header>

      {/* 手机端灵动流体导航浮岛 (Mobile Dock - 纯绝对横向滑移，物理锁定于屏幕底部，杜绝跳动与位移) */}
            {showDockBar && (
<nav
        className="sm:hidden fixed bottom-3 inset-x-4 z-50 h-14 p-1.5 frost border border-wb-border-strong/30 rounded-capsule shadow-[0_12px_32px_rgba(0,0,0,0.15)] dark:shadow-[0_16px_40px_rgba(0,0,0,0.6)] flex items-center justify-between select-none overflow-hidden transition-transform duration-300 ease-out will-change-transform"
        style={{
          bottom: 'calc(0.75rem + env(safe-area-inset-bottom, 0px))',
          transform: navHidden ? 'translateY(calc(100% + 1.5rem))' : 'translateY(0)',
        }}
      >
        {/* 单一纯横向滑动药丸滑块 (无需 layoutId，不受页面高度塌陷、滚动或重新挂载影响，100% 纯横向贴地平移) */}
        {hasActiveMobile && (
          <div className="absolute inset-1.5 pointer-events-none -z-0">
            <motion.div
              className="h-full rounded-capsule bg-wb-bg-elevated shadow-sm border border-wb-border-strong/20"
              style={{ width: `${100 / mobileNavItems.length}%` }}
              initial={false}
              animate={{
                x: `${activeMobileIndex * 100}%`,
              }}
              transition={{ type: 'spring', stiffness: 420, damping: 36 }}
            />
          </div>
        )}

        {mobileNavItems.map((item) => {
          const isActive = currentTab === item.key;
          const Icon = item.icon;
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => {
                if (item.requiresAuth && !user) {
                  onOpenAuth(item.key);
                } else {
                  onSelectTab(item.key);
                }
              }}
              className="relative z-10 flex flex-col items-center justify-center flex-1 h-full py-1 rounded-capsule select-none transition-transform active:scale-90 duration-100"
            >
              <div className="relative flex items-center justify-center">
                {item.key === 'profile' && user ? (
                  <UserAvatar
                    username={user.username}
                    avatarUrl={user.avatar_url}
                    size={18}
                    className="mb-0.5"
                  />
                ) : Icon ? (
                  <Icon
                    className={`w-4 h-4 mb-0.5 transition-colors ${
                      isActive
                        ? 'text-wb-text-primary'
                        : 'text-wb-text-muted'
                    }`}
                  />
                ) : null}
                {item.badge && item.badge > 0 ? (
                  <span className="absolute -top-1 -right-1.5 flex h-2.5 w-2.5 pointer-events-none">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-wb-danger opacity-75" />
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-wb-danger ring-2 ring-wb-bg-surface" />
                  </span>
                ) : null}
              </div>
              <span
                className={`text-3xs tracking-tight transition-colors ${
                  isActive
                    ? 'text-wb-text-primary font-bold'
                    : 'text-wb-text-muted'
                }`}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>
      )}
    </>
  );
};
