/**
 * ============================================================
 * 接口模块四：社区论坛（FM）
 * 对应需求：FM-01 帖子列表与详情 / FM-02 发帖 / FM-03 回帖
 *          FM-04 删帖 / FM-05 收藏帖子
 * ============================================================
 */
import { del, get, post, put } from './request'
import type { CursorPage, Post, PostCreateParams, Reply } from './types'

/** 帖子列表（支持板块筛选、排序、关键词） */
export function fetchPosts(params: {
  keyword?: string
  tag?: string
  sortBy?: 'LATEST' | 'HOT'
  cursor?: string
  size?: number
}) {
  return get<CursorPage<Post>>('/posts', params as Record<string, unknown>)
}

/** 帖子详情（会同时把当前用户加入浏览历史） */
export function fetchPostDetail(id: number) {
  return get<Post>(`/posts/${id}`)
}

/** 发帖（FM-02）：提交前后端做违禁词检测，命中则拒绝并返回原因 */
export function createPost(data: PostCreateParams) {
  return post<Post>('/posts', data)
}

/** 编辑帖子（FM-02）：发布后 24 小时内允许编辑 */
export function updatePost(id: number, data: Partial<PostCreateParams>) {
  return put<Post>(`/posts/${id}`, data)
}

/** 删除帖子（FM-04）：软删除，仅作者本人或管理员可操作 */
export function deletePost(id: number) {
  return del<void>(`/posts/${id}`)
}

/** 帖子回复列表（含二级回复的树形结构） */
export function fetchReplies(postId: number) {
  return get<Reply[]>(`/posts/${postId}/replies`)
}

/**
 * 回帖（FM-03）
 * @param parentId 回复某条回复时传，形成二级回复；直接回帖传 null
 */
export function createReply(postId: number, data: { content: string; parentId?: number | null }) {
  return post<Reply>(`/posts/${postId}/replies`, data)
}

/** 删除自己的回复 */
export function deleteReply(replyId: number) {
  return del<void>(`/replies/${replyId}`)
}

/** 点赞帖子（再次调用为取消点赞） */
export function toggleLike(postId: number, liked: boolean) {
  return liked ? del<void>(`/posts/${postId}/like`) : post<void>(`/posts/${postId}/like`)
}

/** 收藏帖子（FM-05） */
export function favoritePost(id: number) {
  return post<void>(`/posts/${id}/favorite`)
}

/** 取消收藏帖子（FM-05） */
export function unfavoritePost(id: number) {
  return del<void>(`/posts/${id}/favorite`)
}

/** 举报帖子 */
export function reportPost(id: number, data: { type: string; reason: string; evidence?: string[] }) {
  return post<void>(`/posts/${id}/report`, data)
}
