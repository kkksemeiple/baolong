/**
 * 接口统一出口
 * 页面里统一从 '@/api' 引入，不直接引 '@/api/xxx'，便于将来调整目录结构。
 */
export * as authApi from './auth'
export * as taskApi from './task'
export * as messageApi from './message'
export * as forumApi from './forum'
export * as userApi from './user'
export * as adminApi from './admin'

export { BizError } from './request'
export * from './types'
