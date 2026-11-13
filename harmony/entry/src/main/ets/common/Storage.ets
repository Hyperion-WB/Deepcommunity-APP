import preferences from '@ohos.data.preferences';
import common from '@ohos.app.ability.common';
import { UserProfile } from '../model/UserModels';

/**
 * 鸿蒙原生首选项数据持久化管理器
 */
export class StorageManager {
  private static instance: StorageManager;
  private pref: preferences.Preferences | null = null;
  private memoryToken: string = '';
  private memoryUser: UserProfile | null = null;

  public static getInstance(): StorageManager {
    if (!StorageManager.instance) {
      StorageManager.instance = new StorageManager();
    }
    return StorageManager.instance;
  }

  async init(context: common.Context): Promise<void> {
    try {
      this.pref = await preferences.getPreferences(context, 'deep_community_prefs');
      this.memoryToken = (await this.pref.get('token', '')) as string;
      const userStr = (await this.pref.get('user', '')) as string;
      if (userStr) {
        this.memoryUser = JSON.parse(userStr) as UserProfile;
      }
    } catch (e) {
      console.error('StorageManager init failed: ' + JSON.stringify(e));
    }
  }

  getToken(): string {
    return this.memoryToken;
  }

  async setToken(token: string): Promise<void> {
    this.memoryToken = token;
    if (this.pref) {
      await this.pref.put('token', token);
      await this.pref.flush();
    }
  }

  getUser(): UserProfile | null {
    return this.memoryUser;
  }

  async setUser(user: UserProfile | null): Promise<void> {
    this.memoryUser = user;
    if (this.pref) {
      await this.pref.put('user', user ? JSON.stringify(user) : '');
      await this.pref.flush();
    }
  }

  async clear(): Promise<void> {
    this.memoryToken = '';
    this.memoryUser = null;
    if (this.pref) {
      await this.pref.clear();
      await this.pref.flush();
    }
  }
}
