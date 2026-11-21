import React from 'react';
import { formatEast8Date } from '../utils/format';
import type { PostSimple } from '../types';

interface Props {
  newsPost?: PostSimple | null;
  pinnedPost?: PostSimple | null;
  onSelectPost: (postId: string) => void;
  onGoRules?: () => void;
}

export const PinnedAnnouncementBanner: React.FC<Props> = ({
  newsPost,
  pinnedPost,
  onSelectPost,
  onGoRules,
}) => {
  // 如果有早报或置顶声明帖，优先展示；完全对照最新界面规范
  const targetPost = newsPost || pinnedPost;
  const title = targetPost?.title || '【极客早报·11-13】黄仁勋正式辞任英伟达CEO，CFO临时接棒；谷歌连发Gemini 4 Hydrogen免费开放；SpaceX星舰第7次试飞成功完成受控软着陆...';
  const dateStr = targetPost?.created_at ? formatEast8Date(targetPost.created_at) : '2026-11-13';

  return (
    <div
      onClick={() => {
        if (targetPost) onSelectPost(targetPost.id);
        else onGoRules?.();
      }}
      className="wb-pressable cursor-pointer w-full bg-white rounded-xl border border-gray-100 p-4 sm:p-5 shadow-xs hover:border-gray-200 transition-all group"
    >
      <div className="flex items-center gap-2.5 mb-2.5">
        <span className="inline-block text-2xs font-semibold px-2 py-0.5 rounded-md bg-[#18181b] text-white select-none">
          每日早报·置顶要闻
        </span>
        <span className="text-xs text-gray-400 font-mono select-none">
          {dateStr}
        </span>
      </div>
      <div className="flex items-center justify-between gap-4">
        <h3 className="font-bold text-sm sm:text-base text-gray-900 group-hover:text-black line-clamp-1 flex-1 leading-snug">
          {title}
        </h3>
        <span className="text-xs text-gray-400 group-hover:text-gray-700 shrink-0 select-none flex items-center transition-colors">
          立即阅读 &gt;
        </span>
      </div>
    </div>
  );
};
