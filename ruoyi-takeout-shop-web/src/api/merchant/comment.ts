import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types'

/** 管理端留言 */
export interface MerchantComment {
  id?: number
  goodsId?: number
  goodsName?: string
  /** 用户ID（昵称属用户专属表，管理端展示"用户#ID"） */
  memberId?: number
  nickname?: string
  /** 关联订单ID（已购留言必有） */
  orderId?: number
  orderNo?: string
  content?: string
  images?: string[]
  score?: number
  reply?: string
  replyTime?: string
  /** 0正常 1隐藏 */
  status?: string
  createTime?: string
}

export interface CommentQueryParams {
  pageNum: number
  pageSize: number
  goodsName?: string
  /** true=仅已购关联订单留言 false=无关联 */
  hasOrder?: boolean
}

// 分页查询留言列表（含隐藏）
export function listComment(query: CommentQueryParams): Promise<TableDataInfo<MerchantComment[]>> {
  return request({
    url: '/merchant/comment/list',
    method: 'get',
    params: query
  })
}

// 商家回复（可再次编辑覆盖）
export function replyComment(id: number, replyContent: string): Promise<AjaxResult> {
  return request({
    url: '/merchant/comment/reply/' + id,
    method: 'put',
    data: { replyContent }
  })
}

// 隐藏违规留言
export function hideComment(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/comment/hide/' + id,
    method: 'put'
  })
}

// 恢复显示
export function showComment(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/comment/show/' + id,
    method: 'put'
  })
}

// 删除留言
export function delComment(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/comment/' + id,
    method: 'delete'
  })
}
