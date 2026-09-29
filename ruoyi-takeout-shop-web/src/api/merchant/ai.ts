import request from '@/utils/request'
import type { AjaxResult, TableDataInfo } from '@/types'

/** AI 知识库文档 */
export interface AiKnowledge {
  id?: number
  fileName?: string
  filePath?: string
  fileType?: string
  fileSize?: number
  /** 切片数量（解析成功后写入） */
  chunkCount?: number
  /** 0待解析 1解析中 2解析成功 3解析失败 */
  parseStatus?: number
  failReason?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

export interface KnowledgeQueryParams {
  pageNum: number
  pageSize: number
  fileName?: string
  parseStatus?: number
}

// 知识库文档分页列表
export function listKnowledge(query: KnowledgeQueryParams): Promise<TableDataInfo<AiKnowledge[]>> {
  return request({
    url: '/merchant/ai/knowledge/list',
    method: 'get',
    params: query
  })
}

// 删除文档（同步删除其全部向量与物理文件）
export function delKnowledge(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/ai/knowledge/' + id,
    method: 'delete'
  })
}

// 重新解析（先删旧向量再重新切片入库）
export function reparseKnowledge(id: number): Promise<AjaxResult> {
  return request({
    url: '/merchant/ai/knowledge/reparse/' + id,
    method: 'put'
  })
}

// 上传接口地址（供 el-upload 使用）
export const KNOWLEDGE_UPLOAD_URL = '/merchant/ai/knowledge/upload'
