import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, X, FileText, Users, Clock, ArrowRight, CornerDownLeft, Sparkles } from 'lucide-react';
import { apiClient } from '../api/client';
import { UserAvatar } from './UserAvatar';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSearch: (query: string, target: 'posts' | 'users') => void;
  initialQuery?: string;
}

export const SearchModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onSearch,
  initialQuery = '',
}) => {
  const [query, setQuery] = useState(initialQuery);
  const [target, setTarget] = useState<'posts' | 'users'>('posts');
  const [history, setHistory] = useState<string[]>([]);
  const [suggest, setSuggest] = useState<{
    posts: { id: string; title: string }[];
    users: { username: string; nickname?: string; avatar_url?: string }[];
  }>({ posts: [], users: [] });

  const inputRef = useRef<HTMLInputElement | null>(null);

  // 读取历史记录
  useEffect(() => {
    if (isOpen) {
      try {
        const raw = localStorage.getItem('geek_search_history');
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed)) setHistory(parsed.slice(0, 10));
        }
      } catch {}
      setQuery(initialQuery);
      setTimeout(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      }, 50);
    }
  }, [isOpen, initialQuery]);

  // 快捷键 Esc
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  // 输入联想防抖
  useEffect(() => {
    const q = query.trim();
    if (!q) {
      setSuggest({ posts: [], users: [] });
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const res = await apiClient.get('/search/suggest', { params: { q, limit: 5 } });
        setSuggest({
          posts: res.data?.posts || [],
          users: res.data?.users || [],
        });
      } catch {
        setSuggest({ posts: [], users: [] });
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [query]);

  const saveHistory = (keyword: string) => {
    const clean = keyword.trim();
    if (!clean) return;
    try {
      const updated = [clean, ...history.filter((item) => item !== clean)].slice(0, 10);
      setHistory(updated);
      localStorage.setItem('geek_search_history', JSON.stringify(updated));
    } catch {}
  };

  const removeHistoryItem = (e: React.MouseEvent, item: string) => {
    e.stopPropagation();
    const updated = history.filter((h) => h !== item);
    setHistory(updated);
    try {
      localStorage.setItem('geek_search_history', JSON.stringify(updated));
    } catch {}
  };

  const clearHistory = () => {
    setHistory([]);
    try {
      localStorage.removeItem('geek_search_history');
    } catch {}
  };

  const handleSubmit = (finalQuery?: string, finalTarget?: 'posts' | 'users') => {
    const q = (finalQuery !== undefined ? finalQuery : query).trim();
    const t = finalTarget || target;
    if (q) {
      saveHistory(q);
      onSearch(q, t);
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-start justify-center pt-14 sm:pt-20 px-3 sm:px-4 bg-black/55 backdrop-blur-sm">
        {/* 点击遮罩关闭 */}
        <div className="fixed inset-0 -z-10" onClick={onClose} />

        <motion.div
          initial={{ opacity: 0, scale: 0.96, y: -10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.96, y: -10 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
          className="w-full max-w-2xl bg-wb-bg-surface border border-wb-border-subtle rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[82vh]"
        >
          {/* 顶部搜索输入条 */}
          <div className="p-3.5 sm:p-4 border-b border-wb-border-subtle flex items-center gap-3">
            <div className="relative flex-1 flex items-center">
              <Search className="w-5 h-5 text-wb-text-muted absolute left-3 pointer-events-none" />
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleSubmit();
                  }
                }}
                placeholder={target === 'posts' ? '搜索文章、技术主题、代码、标签...' : '搜索极客用户名、个性昵称、简介...'}
                className="w-full h-11 pl-10 pr-10 text-sm sm:text-base bg-wb-bg-subtle/80 border border-transparent rounded-2xl text-wb-text-primary placeholder:text-wb-text-muted focus:outline-none focus:border-wb-border-strong transition-all"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => setQuery('')}
                  className="absolute right-3 p-1 rounded-full text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-border-subtle transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* 目标切换：文章 / 极客 */}
            <div className="flex items-center p-1 bg-wb-bg-subtle rounded-2xl border border-wb-border-subtle shrink-0">
              <button
                type="button"
                onClick={() => setTarget('posts')}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors ${
                  target === 'posts'
                    ? 'bg-wb-text-primary text-wb-bg-page shadow-xs'
                    : 'text-wb-text-secondary hover:text-wb-text-primary'
                }`}
              >
                <FileText className="w-3.5 h-3.5" />
                <span>搜文章</span>
              </button>
              <button
                type="button"
                onClick={() => setTarget('users')}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors ${
                  target === 'users'
                    ? 'bg-wb-text-primary text-wb-bg-page shadow-xs'
                    : 'text-wb-text-secondary hover:text-wb-text-primary'
                }`}
              >
                <Users className="w-3.5 h-3.5" />
                <span>搜极客</span>
              </button>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="p-2 rounded-2xl text-wb-text-muted hover:text-wb-text-primary hover:bg-wb-bg-subtle transition-colors shrink-0"
              title="关闭 (Esc)"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* 搜索内容主体区域 */}
          <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4 text-xs">
            {/* 若输入了关键词，展示实时智能联想 */}
            {query.trim() ? (
              <div className="space-y-3">
                <button
                  type="button"
                  onClick={() => handleSubmit()}
                  className="w-full flex items-center justify-between p-3 rounded-2xl bg-wb-bg-subtle hover:bg-wb-border-subtle/80 text-wb-text-primary transition-colors text-left group"
                >
                  <div className="flex items-center gap-2.5 truncate">
                    <Search className="w-4 h-4 text-wb-brand shrink-0" />
                    <span className="truncate">
                      按回车在全站检索 “<strong className="text-wb-text-primary font-bold">{query.trim()}</strong>”
                    </span>
                  </div>
                  <span className="flex items-center gap-1 text-2xs text-wb-text-muted group-hover:text-wb-text-primary shrink-0">
                    <span>立即搜索</span>
                    <CornerDownLeft className="w-3 h-3" />
                  </span>
                </button>

                {/* 文章候选 */}
                {suggest.posts.length > 0 && (
                  <div className="space-y-1.5 pt-2">
                    <div className="text-2xs font-semibold text-wb-text-muted flex items-center gap-1.5">
                      <FileText className="w-3.5 h-3.5" />
                      <span>匹配文章</span>
                    </div>
                    {suggest.posts.map((post) => (
                      <button
                        key={post.id}
                        type="button"
                        onClick={() => handleSubmit(post.title, 'posts')}
                        className="w-full flex items-center justify-between p-2.5 rounded-xl hover:bg-wb-bg-subtle text-left transition-colors"
                      >
                        <span className="text-wb-text-primary truncate">{post.title}</span>
                        <ArrowRight className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                      </button>
                    ))}
                  </div>
                )}

                {/* 极客候选 */}
                {suggest.users.length > 0 && (
                  <div className="space-y-1.5 pt-2">
                    <div className="text-2xs font-semibold text-wb-text-muted flex items-center gap-1.5">
                      <Users className="w-3.5 h-3.5" />
                      <span>匹配极客</span>
                    </div>
                    {suggest.users.map((u) => (
                      <button
                        key={u.username}
                        type="button"
                        onClick={() => handleSubmit(u.username, 'users')}
                        className="w-full flex items-center justify-between p-2.5 rounded-xl hover:bg-wb-bg-subtle text-left transition-colors"
                      >
                        <div className="flex items-center gap-2 truncate">
                          <UserAvatar username={u.username} avatarUrl={u.avatar_url} size={24} />
                          <span className="font-semibold text-wb-text-primary truncate">@{u.username}</span>
                          {u.nickname && <span className="text-wb-text-muted truncate">({u.nickname})</span>}
                        </div>
                        <ArrowRight className="w-3.5 h-3.5 text-wb-text-muted shrink-0" />
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              /* 无输入时展示历史搜索与热门推荐 */
              <div className="space-y-5">
                {/* 历史搜索 */}
                {history.length > 0 && (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-wb-text-muted">
                      <div className="flex items-center gap-1.5 font-medium">
                        <Clock className="w-3.5 h-3.5" />
                        <span>最近搜索</span>
                      </div>
                      <button
                        type="button"
                        onClick={clearHistory}
                        className="text-2xs hover:text-wb-text-primary transition-colors"
                      >
                        清空
                      </button>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {history.map((term) => (
                        <div
                          key={term}
                          onClick={() => handleSubmit(term)}
                          className="wb-pressable inline-flex items-center gap-1.5 px-3 py-1.5 rounded-capsule bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-wb-text-primary text-xs cursor-pointer transition-colors"
                        >
                          <span>{term}</span>
                          <button
                            type="button"
                            onClick={(e) => removeHistoryItem(e, term)}
                            className="text-wb-text-muted hover:text-wb-danger p-0.5"
                          >
                            <X className="w-3 h-3" />
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 热门搜索推荐 */}
                <div className="space-y-2">
                  <div className="flex items-center gap-1.5 text-wb-text-muted font-medium">
                    <Sparkles className="w-3.5 h-3.5 text-wb-brand" />
                    <span>热门探索主题</span>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {['React 19', 'Next.js', 'AI Agent', 'FastAPI', 'PostgreSQL', 'Docker', '架构设计', '性能调优'].map((tag) => (
                      <button
                        key={tag}
                        type="button"
                        onClick={() => handleSubmit(tag, 'posts')}
                        className="wb-pressable px-3 py-1.5 rounded-capsule bg-wb-bg-surface hover:bg-wb-bg-subtle border border-wb-border-subtle text-wb-text-secondary hover:text-wb-text-primary text-xs transition-colors"
                      >
                        #{tag}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 底部快捷键提示 */}
          <div className="p-3 bg-wb-bg-subtle/50 border-t border-wb-border-subtle flex items-center justify-between text-3xs text-wb-text-muted font-mono px-5">
            <span>回车执行搜索 · Esc 关闭</span>
            <span>极客社区专业搜索系统</span>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
