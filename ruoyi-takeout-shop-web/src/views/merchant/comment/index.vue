<template>
   <div class="app-container takeout-page">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="takeout-search">
         <el-form-item label="商品名称" prop="goodsName">
            <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="是否已购" prop="hasOrder">
            <el-select v-model="queryParams.hasOrder" placeholder="关联订单" clearable style="width: 180px">
               <el-option label="已购（关联订单）" :value="true" />
               <el-option label="无关联订单" :value="false" />
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

      <el-table v-loading="loading" :data="commentList" class="takeout-table">
         <el-table-column label="商品" prop="goodsName" min-width="150" :show-overflow-tooltip="true" />
         <el-table-column label="留言用户" align="center" width="105">
            <template #default="scope">{{ scope.row.nickname }}</template>
         </el-table-column>
         <el-table-column label="评分" align="center" width="105">
            <template #default="scope">
               <el-rate :model-value="scope.row.score" disabled />
            </template>
         </el-table-column>
         <el-table-column label="内容" prop="content" :show-overflow-tooltip="true" />
         <el-table-column label="图片" align="center" width="85">
            <template #default="scope">
               <image-preview v-if="scope.row.images && scope.row.images.length" :src="scope.row.images.join(',')" :width="40" :height="40" />
               <span v-else>-</span>
            </template>
         </el-table-column>
         <el-table-column label="已购" align="center" width="65">
            <template #default="scope">
               <el-tag v-if="scope.row.orderId" type="success" size="small">已购</el-tag>
               <span v-else>-</span>
            </template>
         </el-table-column>
         <el-table-column label="关联订单号" prop="orderNo" min-width="175" :show-overflow-tooltip="true">
            <template #default="scope">{{ scope.row.orderNo && scope.row.orderNo !== '-' ? scope.row.orderNo : '-' }}</template>
         </el-table-column>
         <el-table-column label="商家回复" :show-overflow-tooltip="true">
            <template #default="scope">{{ scope.row.reply || '-' }}</template>
         </el-table-column>
         <el-table-column label="状态" align="center" width="85">
            <template #default="scope">
               <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
                  {{ scope.row.status === '0' ? '正常' : '已隐藏' }}
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="留言时间" align="center" prop="createTime" min-width="165" />
         <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="ChatDotRound" @click="openReply(scope.row)" v-hasPermi="['merchant:comment:reply']">回复</el-button>
               <el-button v-if="scope.row.status === '0'" link type="warning" icon="Hide" @click="doHide(scope.row)" v-hasPermi="['merchant:comment:hide']">隐藏</el-button>
               <el-button v-else link type="success" icon="View" @click="doShow(scope.row)" v-hasPermi="['merchant:comment:hide']">恢复</el-button>
               <el-button link type="danger" icon="Delete" @click="doDelete(scope.row)" v-hasPermi="['merchant:comment:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <!-- 回复对话框 -->
      <el-dialog title="商家回复" v-model="replyOpen" width="520px" append-to-body>
         <el-form ref="replyRef" :model="replyForm" :rules="replyRules" label-width="90px">
            <el-form-item label="留言内容">
               <span>{{ replyForm.content }}</span>
            </el-form-item>
            <el-form-item label="回复内容" prop="replyContent">
               <el-input v-model="replyForm.replyContent" type="textarea" :rows="3" placeholder="填写商家回复（用户可见，可再次编辑覆盖）" maxlength="500" show-word-limit />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitReply">发 布</el-button>
               <el-button @click="replyOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="Comment">
import { listComment, replyComment, hideComment, showComment, delComment } from "@/api/merchant/comment"
import type { MerchantComment, CommentQueryParams } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()

const commentList = ref<MerchantComment[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const replyOpen = ref<boolean>(false)
const replyForm = ref<{ id?: number; content?: string; replyContent?: string }>({})
const replyRules = {
   replyContent: [{ required: true, message: "回复内容不能为空", trigger: "blur" }]
}

const data = reactive({
   queryParams: {
      pageNum: 1,
      pageSize: 10,
      goodsName: undefined,
      hasOrder: undefined
   } as CommentQueryParams
})

const { queryParams } = toRefs(data)

/** 查询留言列表（含隐藏） */
function getList() {
   loading.value = true
   listComment(queryParams.value).then(response => {
      commentList.value = response.rows
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

/** 回复 */
function openReply(row: MerchantComment) {
   replyForm.value = { id: row.id, content: row.content, replyContent: row.reply || '' }
   replyOpen.value = true
}

function submitReply() {
   proxy.$refs["replyRef"].validate((valid: boolean) => {
      if (valid) {
         replyComment(replyForm.value.id!, replyForm.value.replyContent!).then(() => {
            proxy.$modal.msgSuccess("回复成功")
            replyOpen.value = false
            getList()
         })
      }
   })
}

/** 隐藏违规留言 */
function doHide(row: MerchantComment) {
   proxy.$modal.confirm(`确定隐藏该条留言吗？隐藏后用户端不可见`).then(function () {
      return hideComment(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess("已隐藏")
      getList()
   }).catch(() => {})
}

/** 恢复显示 */
function doShow(row: MerchantComment) {
   showComment(row.id!).then(() => {
      proxy.$modal.msgSuccess("已恢复显示")
      getList()
   })
}

/** 删除 */
function doDelete(row: MerchantComment) {
   proxy.$modal.confirm(`确定删除该条留言吗？删除后不可恢复`).then(function () {
      return delComment(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess("删除成功")
      getList()
   }).catch(() => {})
}

getList()
</script>
