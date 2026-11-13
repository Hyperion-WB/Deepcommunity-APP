import { UserProfile } from './UserModels';

/**
 * 聊天会话模型
 */
export interface ConversationItem {
  id: string;
  peer_user_id?: string;
  peer_username?: string;
  peer_nickname?: string;
  peer_avatar?: string;
  target_user?: UserProfile;
  last_message_preview?: string;
  last_message_at?: string;
  unread_count: number;
  updated_at?: string;
}

/**
 * 社区群聊模型
 */
export interface GroupRoomItem {
  id: string;
  name: string;
  description?: string;
  avatar_url?: string;
  is_public: boolean;
  member_count: number;
  announcement?: string;
  creator_username?: string;
  my_role?: string;
  last_message_preview?: string;
  last_message_at?: string;
  unread_count: number;
  created_at?: string;
  updated_at?: string;
}

/**
 * 消息模型
 */
export interface ChatMessageItem {
  id: string;
  conversation_id?: string;
  room_id?: string;
  sender_id: string;
  sender_username: string;
  sender_nickname?: string;
  sender_avatar?: string;
  sender_level: number;
  content: string;
  msg_type: string;
  reply_to_id?: string;
  reply_to_content?: string;
  reply_to_username?: string;
  is_recalled: boolean;
  is_read: boolean;
  created_at: string;
}
