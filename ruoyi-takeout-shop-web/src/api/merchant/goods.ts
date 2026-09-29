import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types'
import type { BizGoods, GoodsQueryParams } from '@/types/api/merchant/goods'

// 分页查询菜品列表
export function listGoods(query: GoodsQueryParams): Promise<TableDataInfo<BizGoods[]>> {
  return request({
    url: '/merchant/goods/list',
    method: 'get',
    params: query
  })
}

// 查询菜品详情（含规格/口味）
export function getGoods(id: number): Promise<AjaxResult<BizGoods>> {
  return request({
    url: '/merchant/goods/' + id,
    method: 'get'
  })
}

// 新增菜品
export function addGoods(data: BizGoods): Promise<AjaxResult> {
  return request({
    url: '/merchant/goods',
    method: 'post',
    data: data
  })
}

// 修改菜品
export function updateGoods(data: BizGoods): Promise<AjaxResult> {
  return request({
    url: '/merchant/goods',
    method: 'put',
    data: data
  })
}

// 删除菜品
export function delGoods(id: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/merchant/goods/' + id,
    method: 'delete'
  })
}

// 上架/下架（1在售 0下架）
export function changeGoodsStatus(id: number, status: string): Promise<AjaxResult> {
  return request({
    url: `/merchant/goods/status/${id}/${status}`,
    method: 'put'
  })
}

// 批量设置售罄标记（1售罄 0取消售罄）
export function changeSoldOut(ids: number[], soldOut: string): Promise<AjaxResult> {
  return request({
    url: '/merchant/goods/soldOut',
    method: 'put',
    params: { ids: ids.join(','), soldOut: soldOut }
  })
}
