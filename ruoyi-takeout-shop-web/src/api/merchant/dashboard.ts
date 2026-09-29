import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

/** 时间范围预设（T14） */
export type DashboardRange = 'today' | 'yesterday' | 'last7' | 'last30' | 'month' | 'custom'

/** 看板查询参数 */
export interface DashboardQuery {
  range: DashboardRange
  /** range=custom 时必填（yyyy-MM-dd） */
  startDate?: string
  endDate?: string
  /** 履约方式筛选：不传=全部，1堂食，2外卖 */
  deliveryType?: number
}

/** 概览指标 */
export interface DashboardOverview {
  /** 营业额（已完成订单实付合计，含配送费） */
  revenue: number
  /** 菜品收入 */
  goodsRevenue: number
  /** 配送费收入 */
  deliveryRevenue: number
  /** 退款金额（展示口径：全部已退款订单实付合计） */
  refundedAmount: number
  /** 退款扣减额（仅"已完成且已退款"，实际从营业额扣回的部分） */
  refundedInRevenue: number
  /** 退款中金额（refund_status=1，仅展示不扣减） */
  refundingAmount: number
  /** 净营业额 = 营业额 - 退款扣减额 */
  netRevenue: number
  /** 有效订单数（已完成） */
  orderCount: number
  /** 客单价 */
  avgOrderAmount: number
  /** 已取消订单数 */
  cancelledCount: number
  /** 退款成功订单数 */
  refundedCount: number
  /** 退款率（T19：退款成功订单数/(有效订单数+退款成功订单数)，百分数） */
  refundRate?: number
}

/** 趋势点 */
export interface DashboardTrendPoint {
  timeLabel: string
  revenue: number
  orderCount: number
  /** T19：该点客单价（营业额/订单量） */
  avgOrderAmount?: number
  /** T19：该点退款成功订单数 */
  refundedCount?: number
  /** T19：该点退款率（百分数） */
  refundRate?: number
}

/** 菜品排行项 */
export interface DashboardGoodsRank {
  goodsId: number
  goodsName: string
  quantity: number
  amount: number
  /** T19：该菜品所在已退款订单的明细件数合计 */
  refundCount?: number
}

/** T19 高峰时段项（hour 0-23，跨天累计；peak=订单数最高的小时） */
export interface DashboardPeakHour {
  hour: number
  orderCount: number
  revenue: number
  peak: boolean
}

/** T19 出餐效率（各段平均秒数，null=无样本；samples=样本量） */
export interface DashboardEfficiency {
  avgAcceptSeconds?: number | null
  acceptSamples?: number
  avgMakeSeconds?: number | null
  makeSamples?: number
  avgPickupWaitSeconds?: number | null
  pickupWaitSamples?: number
  avgDeliverSeconds?: number | null
  deliverSamples?: number
}

/** 分类占比项 */
export interface DashboardCategory {
  categoryId: number | null
  categoryName: string
  amount: number
  quantity: number
  percent: number
}

/** 概览卡片 */
export function getOverview(params: DashboardQuery): AxiosPromise<DashboardOverview> {
  return request({ url: '/merchant/dashboard/overview', method: 'get', params })
}

/** 趋势（granularity=auto 时短区间按小时） */
export function getTrend(params: DashboardQuery & { granularity?: string }): AxiosPromise<DashboardTrendPoint[]> {
  return request({ url: '/merchant/dashboard/trend', method: 'get', params })
}

/** 菜品销量排行 */
export function getGoodsRank(params: DashboardQuery & { sortBy?: string; topN?: number }): AxiosPromise<DashboardGoodsRank[]> {
  return request({ url: '/merchant/dashboard/goodsRank', method: 'get', params })
}

/** 分类营业额占比 */
export function getCategoryStat(params: DashboardQuery): AxiosPromise<DashboardCategory[]> {
  return request({ url: '/merchant/dashboard/categoryStat', method: 'get', params })
}

/** T19 高峰时段：跨天按小时聚合有效订单与营业额（0-23 补齐 + 峰值标记） */
export function getPeakHours(params: DashboardQuery): AxiosPromise<DashboardPeakHour[]> {
  return request({ url: '/merchant/dashboard/peakHours', method: 'get', params })
}

/** T19 出餐效率：接单/制作/取餐等待/配送四段平均耗时（秒）与样本量 */
export function getEfficiency(params: DashboardQuery): AxiosPromise<DashboardEfficiency> {
  return request({ url: '/merchant/dashboard/efficiency', method: 'get', params })
}
