/**
 * 深度社区全局常量配置 (HarmonyOS NEXT)
 */
export class Constants {
  static readonly BASE_URL: string = 'http://api.deepcommunity.top';
  static readonly WS_URL: string = 'ws://api.deepcommunity.top/api/chat/ws';

  // 格式化图片 URL
  static formatImageUrl(path: string | undefined | null): string {
    if (!path || path.trim() === '') {
      return '';
    }
    const clean = path.trim();
    if (clean.startsWith('http://') || clean.startsWith('https://')) {
      return clean;
    }
    const base = Constants.BASE_URL.replace(/\/+$/, '');
    const relative = clean.replace(/^\/+/, '');
    return `${base}/${relative}`;
  }
}

export class DeepColors {
  static readonly BACKGROUND: string = '#0A0A0C';
  static readonly SURFACE: string = '#141416';
  static readonly SURFACE_ELEVATED: string = '#1C1C1E';
  static readonly PRIMARY: string = '#3B82F6';
  static readonly PRIMARY_LIGHT: string = '#60A5FA';
  static readonly TEXT_PRIMARY: string = '#F8FAFC';
  static readonly TEXT_SECONDARY: string = '#94A3B8';
  static readonly TEXT_TERTIARY: string = '#64748B';
  static readonly BORDER: string = '#27272A';
  static readonly ACCENT_ERROR: string = '#EF4444';
  static readonly ACCENT_SUCCESS: string = '#10B981';
  static readonly ACCENT_GOLD: string = '#F59E0B';
  static readonly ACCENT_SILVER: string = '#94A3B8';
  static readonly ACCENT_BRONZE: string = '#B45309';

  // 驼峰命名兼容
  static readonly Background: string = DeepColors.BACKGROUND;
  static readonly Surface: string = DeepColors.SURFACE;
  static readonly SurfaceRaised: string = DeepColors.SURFACE_ELEVATED;
  static readonly Primary: string = DeepColors.PRIMARY;
  static readonly PrimaryLight: string = DeepColors.PRIMARY_LIGHT;
  static readonly TextPrimary: string = DeepColors.TEXT_PRIMARY;
  static readonly TextSecondary: string = DeepColors.TEXT_SECONDARY;
  static readonly TextTertiary: string = DeepColors.TEXT_TERTIARY;
  static readonly GlassBorder: string = DeepColors.BORDER;
  static readonly Success: string = DeepColors.ACCENT_SUCCESS;
  static readonly Gold: string = DeepColors.ACCENT_GOLD;
}
