/**
 * ============================================================
 * axios 封装
 * ------------------------------------------------------------
 * 统一处理四件事：
 * 1. 注入登录凭证（Authorization: Bearer xxx）
 * 2. 注入幂等键（Idempotency-Key），防止用户连点造成重复提交
 * 3. 拆包：后端返回 { code, message, data }，调用方直接拿到 data
 * 4. 统一报错：业务错误、HTTP 错误、401 未登录分别处理
 * ============================================================
 */
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

import { getToken, clearToken } from '@/utils/auth'
import type { ApiResult } from './types'
import { mockAdapter } from '@/mock'

/** 业务异常：调用方可以 catch 后用 code 做进一步判断 */
export class BizError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

/** 生成一个简单的 UUID，用于幂等键 */
function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

/* ------------------------------------------------------------
 * 请求拦截器
 * ---------------------------------------------------------- */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 1. 注入凭证
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 2. 写操作默认带一个幂等键。
    //    如果调用方通过 config.headers 显式传了 key（同一业务重试场景），则保留调用方的。
    const method = (config.method ?? 'get').toLowerCase()
    if (['post', 'put', 'patch', 'delete'].includes(method)) {
      if (!config.headers['Idempotency-Key']) {
        config.headers['Idempotency-Key'] = uuid()
      }
    }

    return config
  },
  (error) => Promise.reject(error),
)

/* ------------------------------------------------------------
 * 响应拦截器
 * ---------------------------------------------------------- */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const body = response.data

    // 后端约定 code === 0 为成功
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '操作失败')
        return Promise.reject(new BizError(body.code, body.message))
      }
      // 拆包：直接把 data 返回给调用方
      return body.data as never
    }

    // 兼容非标准响应（例如文件下载）
    return response.data as never
  },
  (error) => {
    // 401：凭证失效或未登录
    if (error?.response?.status === 401) {
      clearToken()
      ElMessage.error('登录已失效，请重新登录')
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.href = `/login?redirect=${redirect}`
      return Promise.reject(error)
    }

    // HTTP 状态码 → 友好提示
    const statusMap: Record<number, string> = {
      400: '请求参数有误',
      403: '没有权限执行该操作',
      404: '请求的资源不存在',
      429: '操作过于频繁，请稍后再试',
      500: '服务器内部错误',
      502: '网关异常',
      503: '服务暂不可用',
    }

    let message = statusMap[error?.response?.status] ?? '网络异常，请检查网络后重试'

    // 后端若在错误响应里带了 message，优先使用
    if (error?.response?.data?.message) {
      message = error.response.data.message
    }

    // 超时单独提示
    if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    }

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/* ------------------------------------------------------------
 * 启用 Mock
 * ------------------------------------------------------------
 * VITE_USE_MOCK=true 时，请求不发出网络，全部由本地 mock 适配器响应，
 * 这样没有后端也能跑通所有页面（答辩演示推荐打开）。
 * ---------------------------------------------------------- */
if (import.meta.env.VITE_USE_MOCK === 'true') {
  service.defaults.adapter = mockAdapter
}

/* ------------------------------------------------------------
 * 对外暴露的请求方法（已带泛型，调用方直接拿到 data 的类型）
 * ---------------------------------------------------------- */

/** 通用请求 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

/** GET */
export function get<T = unknown>(
  url: string,
  params?: Record<string, unknown>,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ url, method: 'get', params, ...config })
}

/** POST */
export function post<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ url, method: 'post', data, ...config })
}

/** PUT */
export function put<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ url, method: 'put', data, ...config })
}

/** PATCH */
export function patch<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ url, method: 'patch', data, ...config })
}

/** DELETE（有些删除接口需要传 body，所以保留 data 参数） */
export function del<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ url, method: 'delete', data, ...config })
}

/**
 * 文件上传
 * 注意：上传接口不要手动设置 Content-Type，
 * 让浏览器自动带上 multipart/form-data 的 boundary，否则后端解析失败。
 */
export function upload<T = unknown>(url: string, file: File, extra?: Record<string, string>) {
  const form = new FormData()
  form.append('file', file)
  if (extra) {
    Object.entries(extra).forEach(([k, v]) => form.append(k, v))
  }
  return request<T>({
    url,
    method: 'post',
    data: form,
  })
}

export default service
