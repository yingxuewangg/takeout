import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types'
import type { MerchantOrder, OrderQueryParams } from '@/types/api/merchant/goods'

// 分页查询订单列表
export function listOrder(query: OrderQueryParams): Promise<TableDataInfo<MerchantOrder[]>> {
  return request({
    url: '/merchant/order/list',
    method: 'get',
    params: query
  })
}

// 查询订单详情（含明细）
export function getOrder(id: number): Promise<AjaxResult<{ order: MerchantOrder; items: any[] }>> {
  return request({
    url: '/merchant/order/' + id,
    method: 'get'
  })
}

// 接单（1 -> 2）
export function acceptOrder(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/order/accept/' + id,
    method: 'put'
  })
}

// 出餐（2 -> 3）
export function readyOrder(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/order/ready/' + id,
    method: 'put'
  })
}

// 骑手已取餐（3 -> 4，仅外卖；T18 语义明确，接口路径不变）
export function deliverOrder(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/order/deliver/' + id,
    method: 'put'
  })
}

// 确认完成（堂食 3 -> 5；外卖 4 -> 5）
export function completeOrder(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/order/complete/' + id,
    method: 'put'
  })
}
