import request from '@/utils/request'
import type { AjaxResult } from '@/types'
import type { BizCategory } from '@/types/api/merchant/goods'

// 查询分类列表（不分页）
export function listCategory(query?: Partial<BizCategory>): Promise<AjaxResult<BizCategory[]>> {
  return request({
    url: '/merchant/category/list',
    method: 'get',
    params: query
  })
}

// 查询分类详情
export function getCategory(id: number): Promise<AjaxResult<BizCategory>> {
  return request({
    url: '/merchant/category/' + id,
    method: 'get'
  })
}

// 新增分类
export function addCategory(data: BizCategory): Promise<AjaxResult> {
  return request({
    url: '/merchant/category',
    method: 'post',
    data: data
  })
}

// 修改分类
export function updateCategory(data: BizCategory): Promise<AjaxResult> {
  return request({
    url: '/merchant/category',
    method: 'put',
    data: data
  })
}

// 删除分类
export function delCategory(id: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/merchant/category/' + id,
    method: 'delete'
  })
}
