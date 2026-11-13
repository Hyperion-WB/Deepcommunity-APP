import { UserProfile } from './UserModels';

/**
 * 社区分类板块
 */
export interface CategoryItem {
  id: number;
  name: string;
  description?: string;
  icon?: string;
}

/**
 * 帖子项实体模型
 */
export interface PostItem {
  id: string;
  title: string;
  content_md?: string;
  content_html?: string;
  cover_image?: string;
  display_image?: string;
  image_urls?: string[];
  category_id?: number;
  category_name?: string;
  author_id?: string;
  author_username?: string;
  author_nickname?: string;
  author_level: number;
  author_avatar?: string;
  author?: UserProfile;
  like_count: number;
  comment_count: number;
  view_count: number;
  is_liked: boolean;
  is_pinned: boolean;
  is_essence: boolean;
  post_type: string;
  created_at: string;
  updated_at?: string;
}

/**
 * 评论实体模型
 */
export interface CommentItem {
  id: string;
  post_id: string;
  content: string;
  author_id?: string;
  author_username?: string;
  author_nickname?: string;
  author_avatar?: string;
  author_level: number;
  is_accepted?: boolean;
  is_featured?: boolean;
  like_count: number;
  is_liked: boolean;
  created_at: string;
}

/**
 * 榜单项目实体模型
 */
export interface LeaderboardItem {
  user_id: string;
  username: string;
  nickname?: string;
  avatar_url?: string;
  level: number;
  value: number;
  rank: number;
}
