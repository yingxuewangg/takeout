import request from '@/utils/request'
import type { AjaxResult } from '@/types'
import type { BizShopInfo } from '@/types/api/merchant/goods'

// 查询店铺信息
export function getShopInfo(): Promise<AjaxResult<BizShopInfo>> {
  return request({
    url: '/merchant/shop',
    method: 'get'
  })
}

// 修改店铺信息
export function updateShopInfo(data: BizShopInfo): Promise<AjaxResult> {
  return request({
    url: '/merchant/shop',
    method: 'put',
    data: data
  })
}
