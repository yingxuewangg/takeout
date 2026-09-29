<template>
   <div class="app-container takeout-page">
      <!-- 审核 tab（待审核带角标提醒，方案 3.3） -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
         <el-tab-pane name="all">
            <template #label>全部</template>
         </el-tab-pane>
         <el-tab-pane name="pending">
            <template #label>
               <el-badge :value="pendingNum" :hidden="pendingNum === 0" class="tab-badge">待审核</el-badge>
            </template>
         </el-tab-pane>
         <el-tab-pane label="已通过" name="approved" />
         <el-tab-pane label="已驳回" name="rejected" />
      </el-tabs>

      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="takeout-search">
         <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="takeout-toolbar">
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="refundList" class="takeout-table">
         <el-table-column label="订单号" prop="orderNo" min-width="190" :show-overflow-tooltip="true" />
         <el-table-column label="审核状态" align="center" width="115">
            <template #default="scope">
               <el-tag :type="auditTagType(scope.row.auditStatus)">
                  {{ auditText(scope.row) }}
               </el-tag>
            </template>
         </el-table-column>
         <el-table-column label="用户电话" prop="contactPhone" min-width="120" />
         <el-table-column label="联系人" prop="contactName" min-width="95" :show-overflow-tooltip="true" />
         <el-table-column label="退款金额(元)" align="center" prop="refundAmount" width="115" />
         <el-table-column label="退款原因" prop="reason" :show-overflow-tooltip="true" />
         <el-table-column label="凭证" align="center" width="90">
            <template #default="scope">
               <image-preview v-if="scope.row.evidenceImages && scope.row.evidenceImages.length" :src="scope.row.evidenceImages.join(',')" :width="40" :height="40" />
               <span v-else>-</span>
            </template>
         </el-table-column>
         <el-table-column label="申请时间" align="center" prop="createTime" min-width="165" />
         <el-table-column label="操作" align="center" width="190" class-name="small-padding fixed-width">
            <template #default="scope">
               <template v-if="scope.row.auditStatus === 0">
                  <el-button link type="primary" icon="CircleCheck" @click="doApprove(scope.row)" v-hasPermi="['merchant:refund:approve']">通过</el-button>
                  <el-button link type="danger" icon="CircleClose" @click="openReject(scope.row)" v-hasPermi="['merchant:refund:reject']">驳回</el-button>
               </template>
               <el-button link type="info" icon="View" @click="showDetail(scope.row)" v-hasPermi="['merchant:refund:query']">详情</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <!-- 驳回理由对话框 -->
      <el-dialog title="驳回退款申请" v-model="rejectOpen" width="480px" append-to-body>
         <el-form ref="rejectRef" :model="rejectForm" :rules="rejectRules" label-width="90px">
            <el-form-item label="订单号">
               <span>{{ rejectForm.orderNo }}</span>
            </el-form-item>
            <el-form-item label="驳回理由" prop="reason">
               <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="填写驳回理由（用户可见）" maxlength="200" show-word-limit />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitReject">确认驳回</el-button>
               <el-button @click="rejectOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 详情对话框 -->
      <el-dialog title="退款详情" v-model="detailOpen" width="640px" append-to-body>
         <el-descriptions :column="2" border v-if="detail.id">
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">{{ auditText(detail) }}</el-descriptions-item>
            <el-descriptions-item label="退款金额">¥{{ detail.refundAmount }}</el-descriptions-item>
            <el-descriptions-item label="用户电话">{{ detail.contactPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="2">{{ detail.reason }}</el-descriptions-item>
            <el-descriptions-item label="凭证" :span="2">
               <image-preview v-if="detail.evidenceImages && detail.evidenceImages.length" :src="detail.evidenceImages.join(',')" :width="80" :height="80" />
               <span v-else>无</span>
            </el-descriptions-item>
            <el-descriptions-item label="驳回理由" :span="2" v-if="detail.rejectReason">{{ detail.rejectReason }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="退款时间">{{ detail.refundTime || '-' }}</el-descriptions-item>
         </el-descriptions>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="Refund">
import { listRefund, pendingCount, getRefund, approveRefund, rejectRefund } from "@/api/merchant/refund"
import type { MerchantRefund, RefundQueryParams } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()

const refundList = ref<MerchantRefund[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const activeTab = ref<string>('all')
const pendingNum = ref<number>(0)
const detailOpen = ref<boolean>(false)
const detail = ref<MerchantRefund>({})
const rejectOpen = ref<boolean>(false)
const rejectForm = ref<{ id?: number; orderNo?: string; reason?: string }>({})
const rejectRules = {
   reason: [{ required: true, message: "驳回理由不能为空", trigger: "blur" }]
}

const data = reactive({
   queryParams: {
      pageNum: 1,
      pageSize: 10,
      auditStatus: undefined,
      orderNo: undefined
   } as RefundQueryParams
})

const { queryParams } = toRefs(data)

/** 审核状态文案（含超时自动驳回标记） */
function auditText(row: MerchantRefund): string {
   if (row.auditStatus === 0) return "待审核"
   if (row.auditStatus === 1) return "已通过"
   return row.isAutoReject === 1 ? "超时自动驳回" : "已驳回"
}

function auditTagType(status?: number): string {
   if (status === 0) return "warning"
   if (status === 1) return "success"
   return "info"
}

/** tab -> auditStatus 映射 */
function tabStatus(tab: string): number | undefined {
   if (tab === 'pending') return 0
   if (tab === 'approved') return 1
   if (tab === 'rejected') return 2
   return undefined
}

/** 查询列表 + 角标数量 */
function getList() {
   loading.value = true
   queryParams.value.auditStatus = tabStatus(activeTab.value)
   listRefund(queryParams.value).then(response => {
      refundList.value = response.rows
      total.value = response.total
      loading.value = false
   })
   pendingCount().then(res => {
      pendingNum.value = res.count || 0
   })
}

function handleTabChange() {
   queryParams.value.pageNum = 1
   getList()
}

function handleQuery() {
   queryParams.value.pageNum = 1
   getList()
}

function resetQuery() {
   proxy.resetForm("queryRef")
   handleQuery()
}

/** 审核通过（模拟退款成功；重复点击由后端条件更新幂等拦截） */
function doApprove(row: MerchantRefund) {
   proxy.$modal.confirm(`确认通过订单"${row.orderNo}"的退款申请吗？退款金额 ¥${row.refundAmount}（模拟退款，订单将置为已退款终态）`).then(function () {
      return approveRefund(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess("已通过（模拟退款成功）")
      getList()
   }).catch(() => {})
}

/** 打开驳回理由弹窗 */
function openReject(row: MerchantRefund) {
   rejectForm.value = { id: row.id, orderNo: row.orderNo, reason: '' }
   rejectOpen.value = true
}

function submitReject() {
   proxy.$refs["rejectRef"].validate((valid: boolean) => {
      if (valid) {
         rejectRefund(rejectForm.value.id!, rejectForm.value.reason!).then(() => {
            proxy.$modal.msgSuccess("已驳回（订单流转已恢复，用户可再次申请）")
            rejectOpen.value = false
            getList()
         })
      }
   })
}

/** 详情 */
function showDetail(row: MerchantRefund) {
   getRefund(row.id!).then(response => {
      detail.value = response.data || {}
      detailOpen.value = true
   })
}

getList()
</script>

<style scoped>
.tab-badge {
   margin-right: 8px;
}
</style>
