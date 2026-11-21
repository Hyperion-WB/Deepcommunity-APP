export type UserRole = 'super_admin' | 'admin' | 'moderator' | 'auditor' | 'user';
export type UserStatus = 'probation' | 'active' | 'banned' | 'deleting';

export interface UserProfile {
  id: string;
  must_change_password?: boolean;
  open_post_in_new_tab?: boolean;
  username: string;
  nickname?: string;
  email: string;
  avatar_url: string;
  bio: string;
  role: UserRole;
  status: UserStatus;
  level: number;
  experience: number;
  active_days: number;
  created_at: string;
  register_channel?: 'public' | 'invite' | 'github' | 'gitee';
  // 邮箱验证与第三方绑定状态（仅本人可见）
  email_verified?: boolean;
  github_bound?: boolean;
  gitee_bound?: boolean;
  remaining_anon_quota: number;
  remaining_nickname_changes?: number;
  followers_count?: number;
  following_count?: number;
  posts_count?: number;
  is_following?: boolean;
  is_blocked?: boolean;
  is_blocked_by?: boolean;
  dnd_enabled?: boolean;
  hide_following?: boolean;
  hide_followers?: boolean;
  hide_favorites?: boolean;
  hide_ip_location?: boolean;
  hide_online_status?: boolean;
  is_online?: boolean;
  has_checked_in_today?: boolean;
  equipped_title?: string | null;
  continuous_checkin_days?: number;
  likes_received_count?: number;
  comments_count?: number;
  gender?: 'male' | 'female' | 'secret';
  tech_stack?: string;
  github_url?: string;
  blog_url?: string;
  ip_location?: string;
}

export interface FriendLink {
  id: string;
  site_name: string;
  url: string;
  description: string;
  icon_url: string;
  icon_status: 'ok' | 'failed' | 'pending';
  status: 'approved' | 'pending' | 'rejected';
  sort_order: number;
  visible: boolean;
  click_count?: number;
  applicant_contact?: string;
  reject_reason?: string;
}

export interface BlockedUserItem {
  id: string;
  username: string;
  nickname?: string;
  avatar_url: string;
  blocked_at: string;
}

export interface UserAdminItem {
  id: string;
  username: string;
  nickname?: string;
  email: string;
  avatar_url: string;
  role: UserRole;
  status: UserStatus;
  level: number;
  experience: number;
  active_days: number;
  created_at: string;
  last_login_at?: string;
  // 注销缓冲期：申请与预计彻底清除时间
  deletion_requested_at?: string | null;
  purge_after?: string | null;
}

export interface SystemSettingItem {
  key: string;
  value: string;
  description?: string;
  updated_at?: string;
}


export interface PostSimple {
  id: string;
  title: string;
  post_type: 'post' | 'news';
  status: string;
  is_pinned: boolean;
  is_essence?: boolean; // 精华贴 / 推荐标识 (Staff Pick)
  view_count: number;
  like_count: number;
  comment_count: number;
  cover_image?: string;
  // 列表展示图：优先用户封面，没有则取正文第一张插图
  display_image?: string | null;
  created_at: string;
  reading_time_minutes: number;
  category_id?: number | null;
  author_username: string;
  author_nickname?: string;
  author_avatar: string;
  author_level: number;
  category_name?: string;
  is_collected?: boolean;
  tags?: string[];
  summary?: string;
  match_snippet?: string | null;
}

export interface TOCItem {
  id: string;
  text: string;
  level: number;
}

export interface PostDetail extends PostSimple {
  content_md: string;
  content_html: string;
  content_type?: string;
  /** 好文分享：original 原创 / repost 搬运（转载） */
  source_type?: string | null;
  source_note?: string | null;
  comment_policy: string;
  is_liked: boolean;
  is_collected: boolean;
  accepted_comment_id?: string; // 采纳的最佳解答评论 ID
  toc: TOCItem[];
  edits_count: number;
  poll?: PollDetail;
  poll_id?: string;
  match_snippet?: string | null;
  /** 作者在线状态（90 秒心跳），作者名片头像绿点使用 */
  author_is_online?: boolean;
}

export interface CommentItem {
  id: string;
  post_id: string;
  content: string;
  image_url?: string;
  image_kind?: 'photo' | 'sticker' | '';
  is_anonymous: boolean;
  anonymous_alias?: string;
  is_featured: boolean;
  is_accepted?: boolean; // 采纳的最佳解答
  like_count: number;
  is_liked: boolean;
  is_deleted?: boolean;
  created_at: string;
  author_username?: string;
  author_nickname?: string;
  author_avatar?: string;
  author_level?: number;
  /** 该主楼下回复总数（首屏只带前 2 条，其余按需拉取） */
  reply_total?: number;
  replies: CommentItem[];
}


export interface PollOption {
  id: number;
  option_text: string;
  vote_count: number;
  percentage: number;
  is_voted: boolean;
}

export interface PollDetail {
  id: string;
  title: string;
  is_multiple: boolean;
  max_choices: number;
  expires_at: string;
  is_expired: boolean;
  total_votes: number;
  has_voted: boolean;
  options: PollOption[];
}

export interface ConversationItem {
  id: string;
  peer_user_id: string;
  peer_username: string;
  peer_nickname?: string;
  peer_avatar?: string;
  last_message_preview: string;
  last_message_at: string;
  unread_count?: number;
  is_blocked?: boolean;
  is_blocked_by_peer?: boolean;
}

export interface ChatMessage {
  id: string;
  conversation_id: string;
  sender_id: string;
  msg_type: 'text' | 'image';
  content: string;
  seq_id: number;
  is_read: boolean;
  is_recalled: boolean;
  created_at: string;
  /** 引用回复快照 */
  reply_to_id?: string | null;
  reply_sender?: string;
  reply_snippet?: string;
}

export interface GroupRoomItem {
  id: string;
  name: string;
  description: string;
  avatar_url: string;
  creator_id?: string;
  creator_username?: string;
  is_public: boolean;
  announcement?: string;
  announcement_updated_at?: string;
  member_count: number;
  is_member?: boolean;
  my_role?: 'owner' | 'admin' | 'member' | null;
  has_pending_apply?: boolean;
  unread_count?: number;
  last_message_preview: string;
  last_message_at: string;
  created_at: string;
}

export interface GroupQuotaInfo {
  can_create_public: boolean;
  public_created_count: number;
  public_max_limit: number;
  private_created_count: number;
  private_max_limit: number;
  user_role: string;
}

export interface GroupMemberItem {
  user_id: string;
  username: string;
  nickname?: string;
  avatar_url?: string;
  level: number;
  role: 'owner' | 'admin' | 'member';
  joined_at: string;
  group_level?: number;
  group_exp?: number;
  badge_id?: number | null;
  badge_name?: string;
  special_badge_name?: string | null;
  badge_kind?: string;
  muted_until?: string | null;
}

export interface GroupJoinRequestItem {
  id: string;
  room_id: string;
  user_id: string;
  username: string;
  nickname?: string;
  avatar_url?: string;
  level: number;
  reason?: string;
  status: 'pending' | 'approved' | 'rejected';
  created_at: string;
}

export interface GroupChatMessage {
  id: string;
  room_id: string;
  sender_id: string;
  sender_username: string;
  sender_nickname?: string;
  sender_avatar?: string;
  sender_level: number;
  content: string;
  msg_type: string;
  seq_id?: number;
  is_recalled?: boolean;
  /** 是否 @全体成员 消息 */
  is_mention_all?: boolean;
  created_at: string;
  /** 引用回复：被引用消息 id 与快照（发送者昵称 + 内容摘要） */
  reply_to_id?: string | null;
  reply_sender?: string;
  reply_snippet?: string;
}

/** 群公告（历史 + 已读状态） */
export interface GroupAnnouncementItem {
  id: string;
  room_id: string;
  content: string;
  created_by?: string | null;
  created_by_username?: string | null;
  created_by_nickname?: string | null;
  created_at: string;
  is_read: boolean;
  read_at?: string | null;
}

export interface NotificationItem {
  id: string;
  action_type: 'level_up' | 'comment' | 'reply' | 'like_post' | 'like_comment' | 'follow' | 'system' | string;
  target_id?: string | null;
  comment_id?: string | null;
  brief: string;
  is_read: boolean;
  created_at: string;
  sender_username?: string;
  sender_nickname?: string;
  sender_avatar?: string;
}

export interface AchievementBadge {
  id: string;
  title: string;
  subtitle: string;
  icon: string;
  symbol: string;
  description: string;
  category: 'creation' | 'tech' | 'activity' | 'social' | 'easter_egg' | 'guardian';
  tier?: 'bronze' | 'silver' | 'gold' | 'cyber';
  associatedTitle?: string;
  unlocked: boolean;
  progressText?: string;
  percent?: number;
}

export interface SeriesItem {
  id: number;
  post_id: string;
  post_title: string;
  sort_order: number;
}

export interface PostSeries {
  id: string;
  author_id: string;
  author_username: string;
  author_nickname?: string;
  title: string;
  description: string;
  cover_image?: string;
  created_at: string;
  chapter_count: number;
  items: SeriesItem[];
}

