import { useState, useEffect } from 'react';

export type UiMode = 'pro' | 'classic';

const UI_MODE_STORAGE_KEY = 'wb_ui_mode';
const UI_MODE_EVENT = 'geek_ui_mode_change';

/** 获取当前界面布局版本模式，默认为专业版 (pro) */
export const getUiMode = (): UiMode => {
  try {
    const saved = localStorage.getItem(UI_MODE_STORAGE_KEY);
    if (saved === 'classic' || saved === 'pro') {
      return saved;
    }
  } catch {}
  return 'pro';
};

/** 切换界面布局版本模式，并向全站广播事件 */
export const setUiMode = (mode: UiMode) => {
  try {
    localStorage.setItem(UI_MODE_STORAGE_KEY, mode);
    window.dispatchEvent(new CustomEvent(UI_MODE_EVENT, { detail: mode }));
  } catch {}
};

/** React Hook：响应式监听与切换 UI 模式 */
export const useUiMode = (): [UiMode, (mode: UiMode) => void] => {
  const [mode, setModeState] = useState<UiMode>(getUiMode);

  useEffect(() => {
    const handler = (e: Event) => {
      const customEvent = e as CustomEvent<UiMode>;
      if (customEvent.detail) {
        setModeState(customEvent.detail);
      } else {
        setModeState(getUiMode());
      }
    };
    window.addEventListener(UI_MODE_EVENT, handler);
    return () => window.removeEventListener(UI_MODE_EVENT, handler);
  }, []);

  const changeMode = (newMode: UiMode) => {
    setUiMode(newMode);
    setModeState(newMode);
  };

  return [mode, changeMode];
};
