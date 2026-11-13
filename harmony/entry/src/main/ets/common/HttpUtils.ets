import http from '@ohos.net.http';
import promptAction from '@ohos.promptAction';
import { Constants } from './Constants';
import { StorageManager } from './Storage';

/**
 * 鸿蒙原生网络通信请求客户端 (基于 @ohos.net.http)
 */
export class HttpUtils {
  private static async request<T>(
    method: http.RequestMethod,
    path: string,
    params?: Record<string, string | number | boolean>,
    body?: object
  ): Promise<T> {
    const httpRequest = http.createHttp();
    const token = StorageManager.getInstance().getToken();

    let fullUrl = path.startsWith('http') ? path : `${Constants.BASE_URL.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
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
          return {} as T;
        }
        return JSON.parse(resultStr) as T;
      } else {
        let msg = `请求失败 (${response.responseCode})`;
        try {
          const errObj = JSON.parse(response.result as string);
          if (errObj.detail) msg = errObj.detail;
          else if (errObj.message) msg = errObj.message;
        } catch (_ignore) {}
        console.error(`Http error [${fullUrl}]: ${msg}`);
        throw new Error(msg);
      }
    } catch (err) {
      httpRequest.destroy();
      const message = err.message || '网络连接超时，请重试';
      console.error(`Http failed: ${message}`);
      throw new Error(message);
    }
  }

  static async get<T>(path: string, params?: Record<string, string | number | boolean>): Promise<T> {
    return HttpUtils.request<T>(http.RequestMethod.GET, path, params);
  }

  static async post<T>(path: string, body?: object): Promise<T> {
    return HttpUtils.request<T>(http.RequestMethod.POST, path, undefined, body);
  }

  static async put<T>(path: string, body?: object): Promise<T> {
    return HttpUtils.request<T>(http.RequestMethod.PUT, path, undefined, body);
  }

  static async delete<T>(path: string): Promise<T> {
    return HttpUtils.request<T>(http.RequestMethod.DELETE, path);
  }
}
