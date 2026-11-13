/**
 * 用户与鉴权数据实体模型
 */
export interface UserProfile {
  id: string;
  username: string;
  nickname?: string;
  email?: string;
  role?: string;
  level: number;
  avatarUrl?: string;
  bio?: string;
  exp?: number;
  postCount?: number;
  followersCount?: number;
  followingCount?: number;
  isFollowing?: boolean;
}

export interface LoginResponse {
  access_token: string;
  refresh_token?: string;
  token_type: string;
  user?: UserProfile;
}

export interface CheckinStatus {
  has_checked_in: boolean;
  streak: number;
  bestStreak: number;
  makeupLeft: number;
}
