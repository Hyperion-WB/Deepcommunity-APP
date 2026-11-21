import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  ExternalLink,
  Heart,
  Link2,
  Loader2,
  Plus,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import { apiClient } from '../api/client';
import { isDeepLoveLink } from '../components/FriendLinksCard';
import { useToast } from '../components/Toast';
import { useUiMode } from '../utils/uiMode';
import type { FriendLink, UserProfile } from '../types';

interface Props {
  user: UserProfile | null;
  onBack: () => void;
  onRequireAuth: () => void;
}

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

const LinkLogo: React.FC<{ link: FriendLink; size?: number }> = ({ link, size = 44 }) => {
  const [failed, setFailed] = useState(false);
  const showImage = link.icon_url && !failed;
  return (
    <span
      style={{ width: size, height: size }}
      className={`flex items-center justify-center rounded-2xl overflow-hidden shrink-0 border border-wb-border-subtle ${
        showImage ? 'bg-wb-bg-subtle' : colorFor(link.site_name)
      }`}
    >
      {showImage ? (
        <img
          src={link.icon_url}
          alt=""
          loading="lazy"
          onError={() => setFailed(true)}
          className="w-full h-full object-contain p-1.5"
        />
      ) : (
        <span className="text-base font-bold select-none">
          {(link.site_name || '?').slice(0, 1).toUpperCase()}
        </span>
      )}
    </span>
  );
};

export const Links: React.FC<Props> = ({ user, onBack, onRequireAuth }) => {
  const toast = useToast();
  const [uiMode] = useUiMode();
  const [links, setLinks] = useState<FriendLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [applyOpen, setApplyOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [siteName, setSiteName] = useState('');
  const [siteUrl, setSiteUrl] = useState('');
  const [desc, setDesc] = useState('');
  const [contact, setContact] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/links');
      setLinks(Array.isArray(res.data) ? res.data : []);
    } catch {
      /* 静默：列表为空时展示空状态即可 */
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const { deepLoveLinks, regularLinks } = useMemo(() => {
    const visible = links.filter((l) => l.visible !== false);
    const deep: FriendLink[] = [];
    const regular: FriendLink[] = [];

    visible.forEach((l) => {
      if (isDeepLoveLink(l)) {
        deep.push(l);
      } else {
        regular.push(l);
      }
    });

    return { deepLoveLinks: deep, regularLinks: regular };
  }, [links]);

  const openApply = () => {
    if (!user) {
      onRequireAuth();
      return;
    }
    setApplyOpen(true);
  };

  const handleSubmit = async () => {
    if (!siteName.trim() || !siteUrl.trim()) {
      toast.warning('站名和网址必填');
      return;
    }
    setSubmitting(true);
    try {
      await apiClient.post('/links/apply', {
        site_name: siteName.trim(),
        url: siteUrl.trim(),
        description: desc.trim(),
        contact: contact.trim(),
      });
      toast.success('申请已提交，站长审核通过后会自动上线');
      setApplyOpen(false);
      setSiteName('');
      setSiteUrl('');
      setDesc('');
      setContact('');
    } catch (err: any) {
      toast.error(err?.response?.data?.detail || '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const isPro = uiMode === 'pro';

  return (
    <div
      className={`${
        isPro ? 'max-w-[1400px]' : 'max-w-4xl'
      } mx-auto px-4 sm:px-6 py-6 space-y-6 animate-fade-in`}
    >
      {/* 顶栏与动作 */}
      <div className="flex items-center justify-between gap-3 pb-2 border-b border-wb-border-subtle/60">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onBack}
            className={`wb-pressable inline-flex items-center justify-center p-2 text-wb-text-secondary hover:text-wb-text-primary ${
              isPro ? 'rounded-lg bg-wb-bg-surface border border-wb-border-subtle' : 'rounded-capsule bg-wb-bg-subtle'
            }`}
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h1 className="text-base sm:text-lg font-bold text-wb-text-primary flex items-center gap-2">
              <Link2 className="w-5 h-5 text-blue-500" />
              <span>全站友情链接与极客生态</span>
            </h1>
            <p className="text-2xs sm:text-xs text-wb-text-muted">
              连接全球极客、优质技术博客与开源社区 · 共建开放繁荣的知识网络
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={openApply}
          className={`wb-pressable inline-flex items-center gap-1.5 h-9 px-4 text-xs font-semibold shadow-sm shrink-0 ${
            isPro
              ? 'rounded-lg bg-blue-600 hover:bg-blue-500 text-white'
              : 'rounded-capsule bg-wb-text-primary text-wb-bg-page'
          }`}
        >
          <Plus className="w-3.5 h-3.5" /> 申请友链
        </button>
      </div>

      {loading ? (
        <div className="py-20 flex items-center justify-center gap-2 text-xs text-wb-text-muted font-mono">
          <Loader2 className="w-5 h-5 animate-spin text-wb-text-muted" /> 正在同步友情链接数据...
        </div>
      ) : links.length === 0 ? (
        <div className="p-12 sm:p-16 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle text-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-blue-500/10 text-blue-500 flex items-center justify-center mx-auto">
            <Link2 className="w-6 h-6" />
          </div>
          <h3 className="text-sm font-bold text-wb-text-primary">暂无友情链接</h3>
          <p className="text-2xs text-wb-text-muted max-w-sm mx-auto">
            目前社区尚未收录外部友链。欢迎提交你的技术博客、工具箱或开源主页，我们将第一时间审核上架！
          </p>
          <div className="pt-2">
            <button
              type="button"
              onClick={openApply}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 text-white text-xs font-semibold hover:bg-blue-500 shadow-sm transition-colors"
            >
              <Plus className="w-3.5 h-3.5" /> 提交第一个友链
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-8">
          {/* 1. 深情友链 / 特邀伙伴 */}
          {deepLoveLinks.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="p-1.5 rounded-lg bg-rose-500/10 text-rose-500">
                    <Heart className="w-4 h-4 fill-current" />
                  </span>
                  <div>
                    <h2 className="text-sm font-bold text-wb-text-primary flex items-center gap-1.5">
                      <span>深情友链</span>
                      <span className="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-rose-500/15 text-rose-500">
                        精选伙伴
                      </span>
                    </h2>
                    <p className="text-3xs text-wb-text-muted">本站特别推荐的深度技术好友与核心开源作者</p>
                  </div>
                </div>
                <span className="text-3xs text-wb-text-muted font-mono">{deepLoveLinks.length} 站</span>
              </div>

              <div
                className={`grid grid-cols-1 ${
                  isPro ? 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4' : 'sm:grid-cols-2'
                } gap-3`}
              >
                {deepLoveLinks.map((link) => (
                  <a
                    key={link.id}
                    href={`/api/links/${link.id}/visit`}
                    target="_blank"
                    rel="noopener noreferrer nofollow"
                    className="relative group p-4 rounded-2xl bg-gradient-to-br from-rose-500/[0.04] to-wb-bg-surface border border-rose-500/30 hover:border-rose-500/60 transition-all shadow-2xs hover:shadow-md flex flex-col justify-between overflow-hidden"
                  >
                    <div className="flex items-start gap-3">
                      <LinkLogo link={link} size={42} />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-1">
                          <span className="text-xs font-bold text-wb-text-primary truncate group-hover:text-rose-500 transition-colors">
                            {link.site_name}
                          </span>
                          <ExternalLink className="w-3.5 h-3.5 text-rose-400 opacity-60 group-hover:opacity-100 shrink-0" />
                        </div>
                        <p className="text-2xs text-wb-text-secondary line-clamp-2 mt-1 leading-relaxed">
                          {link.description || '博主常年输出优质技术思考'}
                        </p>
                      </div>
                    </div>

                    <div className="mt-3 pt-2.5 border-t border-rose-500/15 flex items-center justify-between text-3xs text-wb-text-muted">
                      <span className="flex items-center gap-1 text-rose-500 font-medium">
                        <Sparkles className="w-3 h-3" /> 站长深情推荐
                      </span>
                      {link.click_count ? (
                        <span className="font-mono text-wb-text-muted">{link.click_count} 次访问</span>
                      ) : null}
                    </div>
                  </a>
                ))}
              </div>
            </div>
          )}

          {/* 2. 技术伙伴与常规友链 */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-blue-500/10 text-blue-500">
                  <Users className="w-4 h-4" />
                </span>
                <div>
                  <h2 className="text-sm font-bold text-wb-text-primary">极客技术圈伙伴</h2>
                  <p className="text-3xs text-wb-text-muted">所有互换友链的优秀开发者站点</p>
                </div>
              </div>
              <span className="text-3xs text-wb-text-muted font-mono">{regularLinks.length} 站</span>
            </div>

            {regularLinks.length === 0 && deepLoveLinks.length > 0 ? (
              <div className="py-6 text-center text-2xs text-wb-text-muted border border-dashed border-wb-border-subtle rounded-2xl">
                暂无更多常规友链，欢迎随时申请
              </div>
            ) : (
              <div
                className={`grid grid-cols-1 ${
                  isPro ? 'sm:grid-cols-2 lg:grid-cols-3' : 'sm:grid-cols-2'
                } gap-3`}
              >
                {regularLinks.map((link) => (
                  <a
                    key={link.id}
                    href={`/api/links/${link.id}/visit`}
                    target="_blank"
                    rel="noopener noreferrer nofollow"
                    className="p-3.5 rounded-2xl bg-wb-bg-surface border border-wb-border-subtle hover:border-wb-border-strong hover:shadow-2xs transition-all flex items-start gap-3 group"
                  >
                    <LinkLogo link={link} size={38} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-1">
                        <span className="text-xs font-semibold text-wb-text-primary truncate group-hover:underline">
                          {link.site_name}
                        </span>
                        <ExternalLink className="w-3 h-3 text-wb-text-muted group-hover:text-wb-text-primary shrink-0" />
                      </div>
                      <p className="text-2xs text-wb-text-muted line-clamp-2 mt-0.5 leading-relaxed">
                        {link.description || link.url}
                      </p>
                      {link.click_count ? (
                        <div className="mt-1 text-[9px] text-wb-text-muted font-mono">
                          访问 {link.click_count} 次
                        </div>
                      ) : null}
                    </div>
                  </a>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 友链申请弹窗 */}
      {applyOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
          <div className="w-full max-w-md rounded-2xl bg-wb-bg-surface border border-wb-border-subtle shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-5 pt-4 pb-3 border-b border-wb-border-subtle">
              <h3 className="text-sm font-bold text-wb-text-primary flex items-center gap-1.5">
                <Link2 className="w-4 h-4 text-blue-500" /> 申请加入友情链接
              </h3>
              <button
                type="button"
                onClick={() => setApplyOpen(false)}
                className="p-1.5 rounded-lg text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-5 space-y-3">
              <div>
                <label className="block text-3xs font-semibold text-wb-text-muted mb-1">
                  站点名称 <span className="text-red-500">*</span>
                </label>
                <input
                  value={siteName}
                  onChange={(e) => setSiteName(e.target.value)}
                  placeholder="例如：极客小栈"
                  maxLength={64}
                  className="w-full px-3 py-2 text-xs rounded-lg border border-wb-border-subtle bg-wb-bg-subtle/50 text-wb-text-primary focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-3xs font-semibold text-wb-text-muted mb-1">
                  网站网址 <span className="text-red-500">*</span>
                </label>
                <input
                  value={siteUrl}
                  onChange={(e) => setSiteUrl(e.target.value)}
                  placeholder="https://yourblog.com"
                  className="w-full px-3 py-2 text-xs rounded-lg border border-wb-border-subtle bg-wb-bg-subtle/50 text-wb-text-primary focus:outline-none focus:ring-1 focus:ring-blue-500 font-mono"
                />
              </div>

              <div>
                <label className="block text-3xs font-semibold text-wb-text-muted mb-1">站点描述</label>
                <textarea
                  value={desc}
                  onChange={(e) => setDesc(e.target.value)}
                  placeholder="一句话介绍你的网站核心方向（如：专注 Rust、分布式架构与全栈开发）"
                  maxLength={200}
                  rows={2}
                  className="w-full px-3 py-2 text-xs rounded-lg border border-wb-border-subtle bg-wb-bg-subtle/50 text-wb-text-primary focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none"
                />
              </div>

              <div>
                <label className="block text-3xs font-semibold text-wb-text-muted mb-1">联系方式 (选填)</label>
                <input
                  value={contact}
                  onChange={(e) => setContact(e.target.value)}
                  placeholder="你的邮箱 / TG / 微信，便于沟通互换"
                  maxLength={120}
                  className="w-full px-3 py-2 text-xs rounded-lg border border-wb-border-subtle bg-wb-bg-subtle/50 text-wb-text-primary focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="p-3 rounded-xl bg-blue-500/5 border border-blue-500/10 text-3xs text-wb-text-muted leading-relaxed space-y-1">
                <p className="font-semibold text-blue-600 dark:text-blue-400">友链准入原则：</p>
                <p>1. 建议原创技术内容占比较高，博客稳定运行半年以上；</p>
                <p>2. 提交后系统会自动抓取网站高清 Favicon 图标并由站长统一审核；</p>
                <p>3. 优质高频技术博主将直接授予【深情友链】尊贵徽章。</p>
              </div>
            </div>

            <div className="px-5 py-3.5 border-t border-wb-border-subtle flex justify-end gap-2 bg-wb-bg-subtle/30">
              <button
                type="button"
                onClick={() => setApplyOpen(false)}
                className="px-4 py-2 rounded-lg text-xs text-wb-text-secondary hover:text-wb-text-primary transition-colors"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={submitting}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold disabled:opacity-50 transition-colors shadow-sm"
              >
                {submitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                提交审核
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
