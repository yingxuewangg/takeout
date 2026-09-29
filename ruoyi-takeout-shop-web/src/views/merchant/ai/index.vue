<template>
   <div class="app-container takeout-page">
      <!-- 上传区（拖拽 / 点击） -->
      <el-card class="upload-card">
         <template #header>
            <div class="card-head">
               <span>AI 知识库</span>
               <el-tooltip content="上传后自动解析文本 → 切片 → 向量化入库；用户端 AI 助手会基于这些内容回答顾客提问">
                  <el-icon>
                     <QuestionFilled />
                  </el-icon>
               </el-tooltip>
            </div>
         </template>
         <el-upload drag :action="uploadUrl" :headers="uploadHeaders" :show-file-list="false"
            accept=".txt,.md,.pdf,.docx" :before-upload="beforeUpload" :on-success="onUploadSuccess"
            :on-error="onUploadError">
            <el-icon class="el-icon--upload">
               <UploadFilled />
            </el-icon>
            <div class="el-upload__text">将文档拖到此处，或 <em>点击上传</em></div>
            <template #tip>
               <div class="el-upload__tip">
                  支持 txt / md / pdf / docx；建议内容为菜品介绍、食材说明、忌口与常见问答等（越具体，助手回答越准）
               </div>
            </template>
         </el-upload>
      </el-card>

      <!-- 列表 -->
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" style="margin-top: 16px" class="takeout-search">
         <el-form-item label="文件名称" prop="fileName">
            <el-input v-model="queryParams.fileName" placeholder="请输入文件名称" clearable style="width: 200px"
               @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="解析状态" prop="parseStatus">
            <el-select v-model="queryParams.parseStatus" placeholder="解析状态" clearable style="width: 160px">
               <el-option label="待解析" :value="0" />
               <el-option label="解析中" :value="1" />
               <el-option label="解析成功" :value="2" />
               <el-option label="解析失败" :value="3" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="takeout-toolbar">
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="list" class="takeout-table">
         <el-table-column label="文件名称" prop="fileName" :show-overflow-tooltip="true" />
         <el-table-column label="类型" align="center" prop="fileType" width="90" />
         <el-table-column label="大小" align="center" min-width="100">
            <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
         </el-table-column>
         <el-table-column label="解析状态" align="center" width="115">
            <template #default="scope">
               <el-tag :type="statusTagType(scope.row.parseStatus)">{{ statusText(scope.row.parseStatus) }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="切片数" align="center" prop="chunkCount" width="100" />
         <el-table-column label="失败原因" prop="failReason" :show-overflow-tooltip="true" />
         <el-table-column label="上传时间" align="center" prop="createTime" min-width="165" />
         <el-table-column label="操作" align="center" width="190" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Refresh" @click="handleReparse(scope.row)"
                  v-hasPermi="['merchant:ai:knowledge:reparse']">重新解析</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)"
                  v-hasPermi="['merchant:ai:knowledge:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize" @pagination="getList" />
   </div>
</template>

<script setup lang="ts" name="AiKnowledge">
import { listKnowledge, delKnowledge, reparseKnowledge, KNOWLEDGE_UPLOAD_URL } from "@/api/merchant/ai"
import type { AiKnowledge, KnowledgeQueryParams } from '@/types/api/merchant/goods'
import { getToken } from "@/utils/auth"

const { proxy } = getCurrentInstance()

const list = ref<AiKnowledge[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)

const uploadUrl = import.meta.env.VITE_APP_BASE_API + KNOWLEDGE_UPLOAD_URL
const uploadHeaders = ref({ Authorization: "Bearer " + getToken() })

const data = reactive({
   queryParams: {
      pageNum: 1,
      pageSize: 10,
      fileName: undefined,
      parseStatus: undefined
   } as KnowledgeQueryParams
})
const { queryParams } = toRefs(data)

/** 解析状态文案（0待解析 1解析中 2解析成功 3解析失败） */
function statusText(status?: number): string {
   const map: Record<number, string> = { 0: "待解析", 1: "解析中", 2: "解析成功", 3: "解析失败" }
   return map[status ?? -1] || "未知"
}

function statusTagType(status?: number): string {
   if (status === 2) return "success"
   if (status === 3) return "danger"
   if (status === 1) return "warning"
   return "info"
}

function formatSize(size?: number): string {
   if (!size) return "-"
   if (size < 1024) return size + " B"
   if (size < 1024 * 1024) return (size / 1024).toFixed(1) + " KB"
   return (size / 1024 / 1024).toFixed(2) + " MB"
}

/** 上传前校验（类型与大小，与后端规则一致） */
function beforeUpload(file: File) {
   const allowed = ["txt", "md", "pdf", "docx"]
   const ext = (file.name.split(".").pop() || "").toLowerCase()
   if (!allowed.includes(ext)) {
      proxy.$modal.msgError("仅支持 txt / md / pdf / docx 格式")
      return false
   }
   if (file.size > 10 * 1024 * 1024) {
      proxy.$modal.msgError("文件大小不能超过 10MB")
      return false
   }
   return true
}

function onUploadSuccess(res: any) {
   if (res.code === 200) {
      proxy.$modal.msgSuccess(`「${res.fileName}」上传成功，正在后台解析...`)
      getList()
   } else {
      proxy.$modal.msgError(res.msg || "上传失败")
   }
}

function onUploadError() {
   proxy.$modal.msgError("上传失败，请检查网络或文件大小")
}

/** 查询列表 */
function getList() {
   loading.value = true
   listKnowledge(queryParams.value).then(response => {
      list.value = response.rows
      total.value = response.total
      loading.value = false
   })
}

function handleQuery() {
   queryParams.value.pageNum = 1
   getList()
}

function resetQuery() {
   proxy.resetForm("queryRef")
   handleQuery()
}

/** 重新解析（先删旧向量再重新切片入库） */
function handleReparse(row: AiKnowledge) {
   proxy.$modal.confirm(`确认重新解析「${row.fileName}」吗？将先删除其已有向量再重新入库`).then(function () {
      return reparseKnowledge(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess("已触发重新解析")
      getList()
   }).catch(() => { })
}

/** 删除（同步删除向量与文件） */
function handleDelete(row: AiKnowledge) {
   proxy.$modal.confirm(`确认删除「${row.fileName}」吗？其全部向量数据将同步删除，AI 助手将不再引用该文档`).then(function () {
      return delKnowledge(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess("删除成功")
      getList()
   }).catch(() => { })
}

getList()
</script>

<style scoped>
.upload-card {
   margin-bottom: 8px;
}

.card-head {
   display: flex;
   align-items: center;
   gap: 6px;
   font-weight: 600;
}
</style>
