import http from '@ohos.net.http';
import { Constants } from './Constants';
import { StorageManager } from './Storage';

/**
 * 统一网络返回结果
 */
export interface ApiResponse<T> {
  isSuccess: boolean;
  data?: T;
  errorMsg?: string;
  errorCode?: number;
}

/**
 * 鸿蒙原生网络通信请求客户端 (基于 @ohos.net.http)
 * - 自动适配 api/ 前缀
 * - 统一 Bearer Token 拦截与鉴权
 * - 安全捕获与标准化 ApiResponse
 */
export class HttpUtils {
  private static async request<T>(
    method: http.RequestMethod,
    path: string,
    params?: Record<string, string | number | boolean>,
    body?: object
  ): Promise<ApiResponse<T>> {
    const httpRequest = http.createHttp();
    const token = StorageManager.getInstance().getToken();

    // 格式化与规范化 URL，自动确保拥有 /api/ 前缀
    let cleanPath = path.replace(/^\/+/, '');
    if (!cleanPath.startsWith('http')) {
      if (!cleanPath.startsWith('api/')) {
        cleanPath = `api/${cleanPath}`;
      }
    }

    let fullUrl = cleanPath.startsWith('http')
      ? cleanPath
      : `${Constants.BASE_URL.replace(/\/+$/, '')}/${cleanPath}`;

    if (params) {
      const queryParts: string[] = [];
      Object.keys(params).forEach((key) => {
        const val = params[key];
        if (val !== undefined && val !== null) {
          queryParts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(val))}`);
        }
      });
      if (queryParts.length > 0) {
        fullUrl += (fullUrl.includes('?') ? '&' : '?') + queryParts.join('&');
      }
    }

    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
    if (token) {
      header['Authorization'] = `Bearer ${token}`;
    }

    const options: http.HttpRequestOptions = {
      method: method,
      header: header,
      readTimeout: 15000,
      connectTimeout: 15000,
      extraData: body ? JSON.stringify(body) : undefined
    };

    try {
      const response = await httpRequest.request(fullUrl, options);
      httpRequest.destroy();

      if (response.responseCode >= 200 && response.responseCode < 300) {
        const resultStr = response.result as string;
        if (!resultStr || resultStr.trim() === '') {
          return { isSuccess: true, data: undefined };
        }
        try {
          const parsed = JSON.parse(resultStr) as T;
          return { isSuccess: true, data: parsed };
        } catch (_e) {
          return { isSuccess: true, data: (resultStr as unknown) as T };
        }
      } else {
        let msg = `请求失败 (${response.responseCode})`;
        try {
          const errObj = JSON.parse(response.result as string);
          if (errObj.detail) msg = errObj.detail;
          else if (errObj.message) msg = errObj.message;
        } catch (_ignore) {}
        return { isSuccess: false, errorCode: response.responseCode, errorMsg: msg };
      }
    } catch (err) {
      httpRequest.destroy();
      const message = err.message || '网络连接超时，请重试';
      return { isSuccess: false, errorCode: -1, errorMsg: message };
    }
  }

  static async get<T>(path: string, params?: Record<string, string | number | boolean>): Promise<ApiResponse<T>> {
    return HttpUtils.request<T>(http.RequestMethod.GET, path, params);
  }

  static async post<T>(path: string, body?: object): Promise<ApiResponse<T>> {
    return HttpUtils.request<T>(http.RequestMethod.POST, path, undefined, body);
  }

  static async put<T>(path: string, body?: object): Promise<ApiResponse<T>> {
    return HttpUtils.request<T>(http.RequestMethod.PUT, path, undefined, body);
  }

  static async delete<T>(path: string): Promise<ApiResponse<T>> {
    return HttpUtils.request<T>(http.RequestMethod.DELETE, path);
  }
}
