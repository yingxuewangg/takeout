import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types'

/** 管理端退款单（审核用） */
export interface MerchantRefund {
  id?: number
  orderNo?: string
  orderId?: number
  /** 1堂食 2外卖 */
  deliveryType?: number
  /** 订单主状态 */
  orderStatus?: number
  /** 用户联系电话 */
  contactPhone?: string
  contactName?: string
  refundAmount?: number
  reason?: string
  /** 凭证完整 URL 数组 */
  evidenceImages?: string[]
  /** 0待审核 1已通过 2已驳回 */
  auditStatus?: number
  rejectReason?: string
  isAutoReject?: number
  refundTime?: string
  createTime?: string
}

export interface RefundQueryParams {
  pageNum: number
  pageSize: number
  auditStatus?: number
  orderNo?: string
}

// 分页查询退款单
export function listRefund(query: RefundQueryParams): Promise<TableDataInfo<MerchantRefund[]>> {
  return request({
    url: '/merchant/refund/list',
    method: 'get',
    params: query
  })
}

// 待审核数量（角标）
export function pendingCount(): Promise<AjaxResult & { count: number }> {
  return request({
    url: '/merchant/refund/pending-count',
    method: 'get'
  }) as Promise<AjaxResult & { count: number }>
}

// 退款单详情
export function getRefund(id: number): Promise<AjaxResult<MerchantRefund>> {
  return request({
    url: '/merchant/refund/' + id,
    method: 'get'
  })
}

// 审核通过（模拟退款成功，订单置终态）
export function approveRefund(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/refund/approve/' + id,
    method: 'put'
  })
}

// 驳回（理由必填、用户可见）
export function rejectRefund(id: number, reason: string): Promise<AjaxResult> {
  return request({
    url: '/merchant/refund/reject/' + id,
    method: 'put',
    data: { reason }
  })
}
