import React, { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, ChevronUp, Heart, Link2, Plus } from 'lucide-react';
import { apiClient } from '../api/client';
import type { FriendLink } from '../types';

export interface FriendLinksCardProps {
  onOpenAll?: () => void;
  variant?: 'default' | 'sidebar';
}

/** 折叠状态下展示的条数 */
const COLLAPSED_COUNT = 12;

const FALLBACK_COLORS = [
  'bg-wb-brand/15 text-wb-brand',
  'bg-wb-info/15 text-wb-info',
  'bg-wb-success/15 text-wb-success',
  'bg-wb-warn/15 text-wb-warn',
  'bg-wb-danger/15 text-wb-danger',
];

const colorFor = (name: string) => {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0;
  return FALLBACK_COLORS[Math.abs(hash) % FALLBACK_COLORS.length];
};

export const LinkGlyph: React.FC<{ link: FriendLink; size?: number }> = ({ link, size = 16 }) => {
  const [failed, setFailed] = useState(false);
  const showImage = link.icon_url && !failed;

  return (
    <span
      style={{ width: size, height: size }}
      className={`flex items-center justify-center rounded-[5px] overflow-hidden shrink-0 ${
        showImage ? 'bg-wb-bg-subtle' : colorFor(link.site_name)
      }`}
    >
      {showImage ? (
        <img
          src={link.icon_url}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="w-full h-full object-contain"
        />
      ) : (
        <span className="text-[9px] font-bold leading-none select-none">
          {(link.site_name || '?').slice(0, 1).toUpperCase()}
        </span>
      )}
    </span>
  );
};

/** 判断是否为特别推荐 / 深情友链 */
export const isDeepLoveLink = (link: FriendLink): boolean => {
  const text = `${link.site_name} ${link.description || ''}`.toLowerCase();
  return (
    text.includes('深情') ||
    text.includes('推荐') ||
    text.includes('特邀') ||
    text.includes('伙伴') ||
    text.includes('精选') ||
    (typeof link.sort_order === 'number' && link.sort_order >= 10)
  );
};

export const FriendLinksCard: React.FC<FriendLinksCardProps> = ({ onOpenAll, variant = 'default' }) => {
  const [links, setLinks] = useState<FriendLink[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    apiClient
      .get('/links')
      .then((res) => {
        if (alive && Array.isArray(res.data)) setLinks(res.data);
      })
      .catch(() => {
        /* 友链拉取失败时容错处理 */
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const visibleLinks = useMemo(() => {
    const list = links.filter((l) => l.visible !== false);
    // 深情/推荐友链置顶排序
    return list.sort((a, b) => {
      const aDeep = isDeepLoveLink(a);
      const bDeep = isDeepLoveLink(b);
      if (aDeep && !bDeep) return -1;
      if (!aDeep && bDeep) return 1;
      return (b.sort_order || 0) - (a.sort_order || 0);
    });
  }, [links]);

  const shown = expanded ? visibleLinks : visibleLinks.slice(0, COLLAPSED_COUNT);
  const canCollapse = visibleLinks.length > COLLAPSED_COUNT;

  // 专业版左侧边栏模式 (宽度较紧凑 w-52)
  if (variant === 'sidebar') {
    return (
      <div className="bg-wb-bg-surface rounded-xl border border-wb-border-subtle p-3 shadow-2xs space-y-2.5">
        <div className="flex items-center justify-between text-3xs font-bold text-wb-text-muted uppercase tracking-wider">
          <span className="flex items-center gap-1.5">
            <Link2 className="w-3.5 h-3.5 text-blue-500" />
            <span className="text-wb-text-primary">友情链接</span>
            {visibleLinks.length > 0 && (
              <span className="px-1 py-0.2 rounded text-[9px] bg-wb-bg-subtle text-wb-text-muted font-mono">
                {visibleLinks.length}
              </span>
            )}
          </span>
          <button
            type="button"
            onClick={onOpenAll}
            className="text-3xs text-wb-text-muted hover:text-wb-text-primary hover:underline cursor-pointer flex items-center gap-0.5"
            title="查看所有友情链接与申请"
          >
            <span>全部</span>
            <ChevronRight className="w-2.5 h-2.5" />
          </button>
        </div>

        {loading ? (
          <div className="py-2 text-center text-3xs text-wb-text-muted">加载中…</div>
        ) : visibleLinks.length === 0 ? (
          <div className="py-2 text-center space-y-1">
            <p className="text-3xs text-wb-text-muted">暂无友链，欢迎申请互换</p>
          </div>
        ) : (
          <div className="space-y-1 pt-0.5">
            {visibleLinks.slice(0, 8).map((link) => {
              const deep = isDeepLoveLink(link);
              return (
                <a
                  key={link.id}
                  href={`/api/links/${link.id}/visit`}
                  target="_blank"
                  rel="noopener noreferrer nofollow"
                  title={link.description ? `${link.site_name} · ${link.description}` : link.site_name}
                  className="flex items-center justify-between gap-1.5 px-2 py-1.5 rounded-lg bg-wb-bg-subtle/50 hover:bg-wb-bg-subtle border border-wb-border-subtle/60 text-3xs transition-colors group"
                >
                  <div className="flex items-center gap-1.5 min-w-0">
                    <LinkGlyph link={link} size={14} />
                    <span className="truncate text-wb-text-secondary group-hover:text-wb-text-primary font-medium">
                      {link.site_name}
                    </span>
                  </div>
                  {deep ? (
                    <span className="shrink-0 flex items-center gap-0.5 px-1 py-0.2 rounded text-[8px] font-semibold bg-rose-500/10 text-rose-500 border border-rose-500/20">
                      <Heart className="w-2 h-2 fill-current" /> 深情
                    </span>
                  ) : link.click_count && link.click_count > 0 ? (
                    <span className="shrink-0 text-[8px] text-wb-text-muted font-mono">
                      {link.click_count}次
                    </span>
                  ) : null}
                </a>
              );
            })}
          </div>
        )}

        <div className="pt-1.5 border-t border-wb-border-subtle/60 flex items-center justify-between text-3xs text-wb-text-muted">
          <span
            className="hover:text-wb-text-primary cursor-pointer flex items-center gap-1"
            onClick={onOpenAll}
          >
            <Plus className="w-2.5 h-2.5" /> 申请友链
          </span>
          <span
            className="hover:text-wb-text-primary cursor-pointer"
            onClick={onOpenAll}
          >
            互换友链 &rarr;
          </span>
        </div>
      </div>
    );
  }

  // 经典版默认卡片模式 (右侧边栏)
  if (visibleLinks.length === 0 && !loading) return null;

  return (
    <div className="p-4 rounded-3xl surface border border-wb-border-subtle shadow-card space-y-2.5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <Link2 className="w-3.5 h-3.5 text-wb-text-muted" />
          <h4 className="text-2xs font-bold text-wb-text-primary tracking-wide uppercase">友情链接</h4>
        </div>
        <span className="text-3xs text-wb-text-muted font-mono">{visibleLinks.length}</span>
      </div>

      {/* 胶囊列表：图标 + 站名 + 特色深情高亮 */}
      <div className="flex flex-wrap gap-1.5">
        {shown.map((link) => {
          const deep = isDeepLoveLink(link);
          return (
            <a
              key={link.id}
              href={`/api/links/${link.id}/visit`}
              target="_blank"
              rel="noopener noreferrer nofollow"
              title={link.description ? `${link.site_name} · ${link.description}` : link.site_name}
              className={`inline-flex items-center gap-1.5 h-6 pl-1 pr-2 max-w-full rounded-full border transition-colors ${
                deep
                  ? 'border-rose-400/40 bg-rose-500/10 text-rose-600 dark:text-rose-400'
                  : 'border-wb-border-subtle bg-wb-bg-subtle/40 hover:bg-wb-bg-subtle hover:border-wb-border-strong text-wb-text-secondary'
              }`}
            >
              <LinkGlyph link={link} />
              <span className="text-3xs truncate max-w-[72px]">{link.site_name}</span>
              {deep && <Heart className="w-2.5 h-2.5 fill-current text-rose-500 shrink-0" />}
            </a>
          );
        })}
      </div>

      {canCollapse && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="w-full flex items-center justify-center gap-1 py-0.5 rounded-lg text-3xs text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors"
        >
          {expanded ? (
            <>
              <ChevronUp className="w-3 h-3" /> 收起
            </>
          ) : (
            <>
              <ChevronDown className="w-3 h-3" /> 展开全部 {visibleLinks.length} 个
            </>
          )}
        </button>
      )}

      <div className="pt-1.5 border-t border-wb-border-subtle flex items-center justify-between text-3xs text-wb-text-muted">
        <span className="hover:text-wb-text-primary cursor-pointer" onClick={onOpenAll}>
          全部友链 →
        </span>
        <span className="hover:text-wb-text-primary cursor-pointer" onClick={onOpenAll}>
          申请友链
        </span>
      </div>
    </div>
  );
};
