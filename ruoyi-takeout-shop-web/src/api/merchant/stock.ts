import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

/** 今日库存行（T12） */
export interface StockRow {
  id: number
  goodsId: number
  goodsName: string
  specId: number
  specName: string
  limitQty: number
  soldQty: number
  remainQty: number
  /** 手动售罄（0否 1是） */
  manualSoldOut: string
  /** 自动售罄（0否 1是；库存耗尽） */
  autoSoldOut: string
}

/** 查询今日库存列表 */
export function listTodayStock(): AxiosPromise<StockRow[]> {
  return request({
    url: '/merchant/stock/today',
    method: 'get'
  })
}

/** 手动重置当天库存（补货） */
export function resetStock(goodsId: number, specId?: number, limitQty?: number) {
  return request({
    url: '/merchant/stock/reset',
    method: 'put',
    params: { goodsId, specId, limitQty }
  })
}
