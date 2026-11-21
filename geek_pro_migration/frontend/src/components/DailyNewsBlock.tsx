import React from 'react';
import { ChevronRight, Sparkles } from 'lucide-react';
import { formatEast8Date } from '../utils/format';
import type { PostSimple } from '../types';

interface Props {
  newsPost: PostSimple;
  onSelectPost: (postId: string) => void;
  onGoWriteDaily?: () => void;
  isAdmin?: boolean;
}

/**
 * 极客日报条目解析（CSDN 早报风格）：
 * 将日报标题或摘要根据分号、顿号、换行或数字序号切分为独立新闻条目
 */
export const parseNewsItems = (title: string, summary?: string): string[] => {
  // 优先从标题提取核心内容
  let raw = title.replace(/^【[^】]+】\s*/, '').trim();
  if (summary && summary.length > raw.length) {
    raw = summary;
  }
  // 按分号、换行、或者形如 "1." "1、" "一、" 分隔
  const splitRegex = /(?:[；;]|\n+|\d+[\.、]\s*)/;
  const parts = raw
    .split(splitRegex)
    .map((item) => item.trim())
    .filter((item) => item.length > 5);

  if (parts.length > 1) {
    return parts.slice(0, 5);
  }
  return [raw];
};

export const DailyNewsBlock: React.FC<Props> = ({
  newsPost,
  onSelectPost,
  onGoWriteDaily,
  isAdmin,
}) => {
  const items = parseNewsItems(newsPost.title, newsPost.summary);

  return (
    <div
      onClick={() => onSelectPost(newsPost.id)}
      className="wb-pressable cursor-pointer p-4 sm:p-5 rounded-3xl bg-wb-bg-surface border border-wb-border-subtle shadow-card hover:border-wb-border-strong transition-all group space-y-3.5"
    >
      {/* 顶部标题行 */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pb-2 border-b border-wb-border-subtle">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="inline-flex items-center gap-1 text-2xs font-semibold px-2.5 py-0.5 rounded-capsule bg-wb-text-primary text-wb-bg-page">
            <Sparkles className="w-3 h-3" /> 每日早报 · 科技要闻
          </span>
          <span className="text-xs text-wb-text-muted font-mono">
            {formatEast8Date(newsPost.created_at)}
          </span>
          <span className="text-2xs text-wb-text-muted">CSDN 风格序号排版</span>
        </div>

        <div className="flex items-center gap-2 self-end sm:self-auto">
          {isAdmin && onGoWriteDaily && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onGoWriteDaily();
              }}
              className="wb-pressable h-6 px-2.5 rounded-capsule bg-wb-bg-subtle hover:bg-wb-border-subtle border border-wb-border-subtle text-wb-text-primary text-3xs font-semibold flex items-center gap-1 transition-colors"
            >
              <span>写新日报</span>
            </button>
          )}
          <span className="text-xs font-semibold text-wb-text-secondary group-hover:text-wb-text-primary flex items-center gap-0.5 transition-colors">
            <span>阅读完整日报</span>
            <ChevronRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-0.5" />
          </span>
        </div>
      </div>

      {/* CSDN 风格序号间隔新闻列表 */}
      <div className="grid grid-cols-1 gap-2">
        {items.map((item, idx) => (
          <div
            key={idx}
            className="flex items-start gap-2.5 p-2 rounded-xl bg-wb-bg-subtle/50 hover:bg-wb-bg-subtle border border-transparent hover:border-wb-border-subtle transition-all"
          >
            {/* 序号方块 */}
            <span className="w-5 h-5 rounded-md bg-wb-text-primary text-wb-bg-page font-mono text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">
              {idx + 1}
            </span>
            <p className="text-xs sm:text-sm text-wb-text-primary leading-relaxed font-normal flex-1">
              {item}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
};
