import React, { useState } from 'react';
import {
  ArrowLeft,
  Calendar,
  Check,
  Edit3,
  Zap,
} from 'lucide-react';
import { apiClient } from '../api/client';
import { useToast } from '../components/Toast';
import { UserAvatar } from '../components/UserAvatar';
import { LevelBadge } from '../components/LevelBadge';
import { Leaderboard } from '../components/Leaderboard';
import { useUiMode } from '../utils/uiMode';
import type { UserProfile } from '../types';

interface Props {
  user: UserProfile | null;
  onBack: () => void;
  onGoWrite?: () => void;
  onRequireAuth?: () => void;
  onRefreshUser?: () => void;
  onSelectUser?: (username: string) => void;
}

export const Checkin: React.FC<Props> = ({
  user,
  onBack,
  onGoWrite,
  onRequireAuth,
  onRefreshUser,
  onSelectUser,
}) => {
  const toast = useToast();
  const [uiMode] = useUiMode();
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkedInLocally, setCheckedInLocally] = useState(false);

  const hasCheckedInToday = Boolean(user?.has_checked_in_today || checkedInLocally);
  const continuousDays = (user?.continuous_checkin_days || 0) + (checkedInLocally && !user?.has_checked_in_today ? 1 : 0);

  // 执行每日打卡
  const handleCheckin = async () => {
    if (!user) {
      onRequireAuth?.();
      return;
    }
    if (hasCheckedInToday) {
      toast.info('今日已经完成打卡，明天再来吧');
      return;
    }
    setCheckingIn(true);
    try {
      const res = await apiClient.post('/users/checkin');
      setCheckedInLocally(true);
      toast.success(`打卡成功！获得 +${res.data?.exp_gained || 5} EXP (已连续 ${res.data?.continuous_days || continuousDays} 天)`);
      onRefreshUser?.();
    } catch (err: any) {
      if (err.response?.data?.detail?.includes('已经签到') || err.response?.data?.detail?.includes('已签到')) {
        setCheckedInLocally(true);
      }
      toast.info(err.response?.data?.detail || '打卡已完成');
    } finally {
      setCheckingIn(false);
    }
  };

  // 生成过去 6 天 + 今天的星期和日期标签
  const daysOfWeek = ['日', '一', '二', '三', '四', '五', '六'];
  const weekNodes = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    const isToday = i === 6;
    const dayName = daysOfWeek[d.getDay()];
    const dateFormatted = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    // 假设连续签到覆盖的天数或者今天
    const checked = isToday ? hasCheckedInToday : i >= Math.max(0, 6 - (continuousDays - (hasCheckedInToday ? 1 : 0)));
    return {
      index: i,
      dayName,
      dateFormatted,
      isToday,
      checked,
    };
  });

  return (
    <div className="max-w-5xl mx-auto px-3 sm:px-4 py-4 sm:py-6 space-y-5 animate-fade-in">
      {/* 顶部二级导航（经典版保留，专业版由顶部全宽导航统一接管，避免双层顶栏重复） */}
      {uiMode !== 'pro' && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onBack}
              className="wb-pressable flex items-center gap-1.5 h-8 px-3 rounded-capsule bg-wb-bg-surface border border-wb-border-subtle hover:border-wb-border-strong text-wb-text-secondary hover:text-wb-text-primary text-xs font-medium transition-colors select-none shadow-xs"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>返回首页</span>
            </button>

            <div className="flex items-center gap-2 pl-2 border-l border-wb-border-subtle">
              <span className="font-bold text-xs tracking-wider text-wb-text-primary font-mono select-none">
                COMMUNITY
              </span>
              <span className="text-wb-text-muted text-xs">/</span>
              <span className="text-xs font-semibold text-wb-text-primary">每日签到</span>
            </div>
          </div>
        </div>
      )}

      {/* 页面双栏核心区 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 sm:gap-6 items-start">
        {/* 左侧大卡片：签到主体 (占 2 列) */}
        <div className="lg:col-span-2 p-6 sm:p-10 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle shadow-card flex flex-col items-center justify-between min-h-[460px] space-y-8">
          {/* 上部：连续签到天数 */}
          <div className="text-center space-y-2 pt-2">
            <div className="flex items-baseline justify-center gap-2">
              <span className="text-5xl sm:text-6xl font-extrabold font-mono text-wb-text-primary tracking-tight">
                {continuousDays || (user ? 1 : 0)}
              </span>
              <span className="text-sm sm:text-base font-medium text-wb-text-secondary">天连续签到</span>
            </div>
            <p className="text-xs text-wb-text-muted">
              {hasCheckedInToday ? '今日已签到，继续保持' : '今日尚未签到，打卡可领取经验值'}
            </p>
          </div>

          {/* 中部：一周签到进度节点 */}
          <div className="w-full max-w-md mx-auto">
            <div className="flex items-center justify-between gap-1.5 sm:gap-2">
              {weekNodes.map((node) => (
                <div key={node.index} className="flex flex-col items-center gap-2 flex-1">
                  <div
                    className={`w-9 h-9 sm:w-11 sm:h-11 rounded-full flex items-center justify-center font-medium text-xs sm:text-sm select-none transition-all ${
                      node.checked
                        ? 'bg-wb-text-primary text-wb-bg-page shadow-xs'
                        : node.isToday
                        ? 'border-2 border-wb-text-primary text-wb-text-primary bg-wb-bg-surface font-bold'
                        : 'bg-wb-bg-subtle text-wb-text-muted border border-wb-border-subtle'
                    }`}
                  >
                    {node.checked ? (
                      <span className="font-bold">&minus;</span>
                    ) : (
                      <span>{node.dayName}</span>
                    )}
                  </div>
                  <span className="text-3xs font-mono text-wb-text-muted whitespace-nowrap">
                    {node.isToday ? '今天' : node.dateFormatted}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* 签到打卡按钮 */}
          <div className="w-full max-w-xs mx-auto">
            <button
              type="button"
              onClick={handleCheckin}
              disabled={hasCheckedInToday || checkingIn}
              className={`wb-pressable w-full h-11 sm:h-12 rounded-2xl flex items-center justify-center gap-2 text-sm font-semibold shadow-md transition-all ${
                hasCheckedInToday
                  ? 'bg-wb-text-secondary/70 text-wb-bg-page cursor-default'
                  : 'bg-wb-text-primary text-wb-bg-page hover:opacity-90 active:scale-98 cursor-pointer'
              }`}
            >
              {hasCheckedInToday ? (
                <>
                  <Check className="w-4 h-4" />
                  <span>✓ 今日已签到</span>
                </>
              ) : (
                <>
                  <Calendar className="w-4 h-4" />
                  <span>{checkingIn ? '正在打卡...' : '立即打卡签到 (+5 EXP)'}</span>
                </>
              )}
            </button>
          </div>

          {/* 底部规则与口径三列 */}
          <div className="w-full grid grid-cols-1 sm:grid-cols-3 gap-4 pt-6 border-t border-wb-border-subtle/70 text-left">
            <div className="space-y-1">
              <span className="text-xs font-semibold text-wb-text-primary block">签到时间</span>
              <p className="text-2xs text-wb-text-muted leading-relaxed">
                每日 00:00 后开放签到。
              </p>
            </div>
            <div className="space-y-1">
              <span className="text-xs font-semibold text-wb-text-primary block">连续规则</span>
              <p className="text-2xs text-wb-text-muted leading-relaxed">
                连续签到累计活跃天数与 EXP，断签后重新计算。
              </p>
            </div>
            <div className="space-y-1">
              <span className="text-xs font-semibold text-wb-text-primary block">榜单口径</span>
              <p className="text-2xs text-wb-text-muted leading-relaxed">
                最早签到的五位进入「抢早签」，具体奖励以正式版本为准。
              </p>
            </div>
          </div>
        </div>

        {/* 右侧栏：个人卡片 + 社区榜单 */}
        <div className="space-y-5">
          {/* 个人名片小卡片 */}
          <div className="p-5 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle shadow-card space-y-4">
            {user ? (
              <div className="space-y-3.5">
                <div
                  onClick={() => onSelectUser?.(user.username)}
                  className="flex items-center gap-3 cursor-pointer hover:opacity-85 transition-opacity"
                  title="查看个人中心"
                >
                  <UserAvatar
                    username={user.username}
                    avatarUrl={user.avatar_url}
                    size={42}
                    className="border border-wb-border-subtle shadow-xs shrink-0"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="font-bold text-sm text-wb-text-primary truncate">
                        {user.nickname || user.username}
                      </span>
                      <LevelBadge level={user.level} status={user.status} role={user.role} size="sm" />
                    </div>
                    {user.nickname && (
                      <span className="text-2xs text-wb-text-muted font-mono block truncate">
                        @{user.username}
                      </span>
                    )}
                    <span className="text-2xs text-wb-text-muted font-mono block pt-0.5">
                      已活跃 {user.active_days} 天 · {user.experience} EXP
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2 pt-1">
                  <button
                    type="button"
                    onClick={handleCheckin}
                    disabled={hasCheckedInToday || checkingIn}
                    className={`wb-pressable flex-1 flex items-center justify-center gap-1.5 h-9 px-3 rounded-capsule text-xs font-semibold shadow-xs transition-all ${
                      hasCheckedInToday
                        ? 'bg-wb-bg-subtle text-wb-text-muted border border-wb-border-subtle cursor-default'
                        : 'bg-wb-text-primary text-wb-bg-page hover:opacity-90'
                    }`}
                  >
                    {hasCheckedInToday ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-wb-success" />
                        <span>今日已打卡</span>
                      </>
                    ) : (
                      <>
                        <Zap className="w-3.5 h-3.5" />
                        <span>快速打卡</span>
                      </>
                    )}
                  </button>

                  {onGoWrite && (
                    <button
                      type="button"
                      onClick={onGoWrite}
                      className="wb-pressable flex items-center justify-center gap-1.5 px-3.5 h-9 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle text-xs font-medium text-wb-text-secondary hover:text-wb-text-primary"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>创作</span>
                    </button>
                  )}
                </div>
              </div>
            ) : (
              <div className="space-y-3 text-center py-2">
                <div className="w-10 h-10 rounded-capsule bg-wb-bg-subtle border border-wb-border-subtle mx-auto flex items-center justify-center text-wb-text-primary font-bold">
                  ★
                </div>
                <div className="space-y-1">
                  <h3 className="text-sm font-bold text-wb-text-primary">加入极客社区</h3>
                  <p className="text-xs text-wb-text-muted">登录后签到打卡并参与先锋榜单</p>
                </div>
                <button
                  type="button"
                  onClick={onRequireAuth}
                  className="wb-pressable w-full h-9 rounded-capsule bg-wb-text-primary text-wb-bg-page text-xs font-semibold shadow-xs"
                >
                  通行证登录 / 注册
                </button>
              </div>
            )}
          </div>

          {/* 社区榜单 */}
          <Leaderboard onSelectUser={onSelectUser} />
        </div>
      </div>
    </div>
  );
};
