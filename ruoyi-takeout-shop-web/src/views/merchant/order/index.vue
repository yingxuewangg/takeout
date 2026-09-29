<template>
   <div class="app-container takeout-page">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="takeout-search">
         <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="订单状态" clearable style="width: 180px">
               <el-option v-for="(label, value) in statusMap" :key="value" :label="label" :value="Number(value)" />
            </el-select>
         </el-form-item>
         <el-form-item label="履约方式" prop="deliveryType">
            <el-select v-model="queryParams.deliveryType" placeholder="履约方式" clearable style="width: 160px">
               <el-option label="堂食" :value="1" />
               <el-option label="外卖配送" :value="2" />
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

      <el-table v-loading="loading" :data="orderList" class="takeout-table">
         <el-table-column label="订单号" prop="orderNo" min-width="190" :show-overflow-tooltip="true" />
         <el-table-column label="状态" align="center" width="110">
            <template #default="scope">
               <el-tag :type="statusTagType(scope.row)">{{ statusText(scope.row) }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="履约" align="center" width="80">
            <template #default="scope">
               {{ scope.row.deliveryType === 1 ? '堂食' : '外卖' }}
            </template>
         </el-table-column>
         <el-table-column label="桌号/地址" align="center" min-width="160" :show-overflow-tooltip="true">
            <template #default="scope">
               {{ scope.row.deliveryType === 1 ? (scope.row.tableNo + ' 号桌') : addressBrief(scope.row) }}
            </template>
         </el-table-column>
         <el-table-column label="联系人" prop="contactName" min-width="90" :show-overflow-tooltip="true" />
         <el-table-column label="联系电话" prop="contactPhone" min-width="120" />
         <el-table-column label="实付(元)" align="center" prop="payAmount" width="95" />
         <el-table-column label="下单时间" align="center" prop="createTime" min-width="160" />
         <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button v-if="scope.row.status === 1 && scope.row.refundStatus === 0" link type="primary" icon="Check" @click="doTransition(scope.row, 'accept', '接单')" v-hasPermi="['merchant:order:accept']">接单</el-button>
               <el-button v-if="scope.row.status === 2 && scope.row.refundStatus === 0" link type="primary" icon="Food" @click="doTransition(scope.row, 'ready', '出餐')" v-hasPermi="['merchant:order:ready']">出餐</el-button>
               <el-button v-if="scope.row.status === 3 && scope.row.deliveryType === 2 && scope.row.refundStatus === 0" link type="warning" icon="Van" @click="doTransition(scope.row, 'deliver', '骑手已取餐')" v-hasPermi="['merchant:order:deliver']">骑手已取餐</el-button>
               <el-button v-if="canComplete(scope.row)" link type="success" icon="CircleCheck" @click="doTransition(scope.row, 'complete', scope.row.deliveryType === 1 ? '确认完成' : '确认送达')" v-hasPermi="['merchant:order:complete']">{{ scope.row.deliveryType === 1 ? '确认完成' : '确认送达' }}</el-button>
               <el-button link type="primary" icon="Printer" @click="printOrder(scope.row)" v-hasPermi="['merchant:order:query']">打印</el-button>
               <el-button link type="info" icon="View" @click="showDetail(scope.row)" v-hasPermi="['merchant:order:query']">详情</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <!-- 详情对话框 -->
      <el-dialog title="订单详情" v-model="detailOpen" width="700px" append-to-body>
         <el-descriptions :column="2" border v-if="detail.order">
            <el-descriptions-item label="订单号">{{ detail.order.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusText(detail.order) }}</el-descriptions-item>
            <el-descriptions-item label="履约方式">{{ detail.order.deliveryType === 1 ? '堂食' : '外卖配送' }}</el-descriptions-item>
            <el-descriptions-item label="桌号">{{ detail.order.tableNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detail.order.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.order.contactPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="菜品合计">¥{{ detail.order.goodsAmount }}</el-descriptions-item>
            <el-descriptions-item label="配送费">¥{{ detail.order.deliveryFee }}</el-descriptions-item>
            <el-descriptions-item label="实付金额">¥{{ detail.order.payAmount }}</el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ detail.order.createTime }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.order.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ addressBrief(detail.order) || '-' }}</el-descriptions-item>
            <!-- T18 配送环节时间（有值才显示；堂食只有出餐时间） -->
            <el-descriptions-item v-if="detail.order.readyTime" label="出餐时间">{{ detail.order.readyTime }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.order.pickedUpTime" label="骑手取餐时间">{{ detail.order.pickedUpTime }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.order.deliveredTime" label="送达时间">{{ detail.order.deliveredTime }}</el-descriptions-item>
         </el-descriptions>
         <el-table :data="detail.items || []" size="small" style="margin-top: 12px">
            <el-table-column label="菜品" prop="goodsName" />
            <el-table-column label="规格口味" :show-overflow-tooltip="true">
               <template #default="scope">{{ specText(scope.row.specFlavorJson) }}</template>
            </el-table-column>
            <el-table-column label="单价" prop="unitPrice" width="80" />
            <el-table-column label="数量" prop="quantity" width="70" />
            <el-table-column label="小计" prop="subtotal" width="80" />
         </el-table>
         <template #footer>
            <el-button type="primary" icon="Printer" @click="detail.order && printOrder(detail.order)" v-hasPermi="['merchant:order:query']">打印小票</el-button>
            <el-button @click="detailOpen = false">关 闭</el-button>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="Order">
import { listOrder, getOrder, acceptOrder, readyOrder, deliverOrder, completeOrder } from "@/api/merchant/order"
import { orderPush } from '@/utils/orderPush'
import type { MerchantOrder, OrderQueryParams } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()

/** 状态字典（与 SQL 注释/用户端一致） */
const statusMap: Record<number, string> = {
   0: "待支付",
   1: "待接单",
   2: "已接单制作中",
   3: "已出餐待取餐",
   4: "配送中",
   5: "已完成",
   6: "已取消"
}

const orderList = ref<MerchantOrder[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const detailOpen = ref<boolean>(false)
const detail = ref<{ order?: MerchantOrder; items?: any[] }>({})

const data = reactive({
   queryParams: {
      pageNum: 1,
      pageSize: 10,
      orderNo: undefined,
      status: undefined,
      deliveryType: undefined
   } as OrderQueryParams
})

const { queryParams } = toRefs(data)

/** 状态展示：退款状态优先（方案 3.4/4.4） */
function statusText(row: MerchantOrder): string {
   if (row.refundStatus === 1) return "退款审核中"
   if (row.refundStatus === 2) return "已退款"
   return statusMap[row.status!] || "未知"
}

function statusTagType(row: MerchantOrder): string {
   if (row.refundStatus === 1) return "warning"
   if (row.refundStatus === 2) return "info"
   switch (row.status) {
      case 0: return "warning"
      case 1: return "primary"
      case 2: return "primary"
      case 3: return "success"
      case 4: return "warning"
      case 5: return "success"
      case 6: return "info"
      default: return "info"
   }
}

/** 确认完成按钮：堂食 3 -> 5；外卖 4 -> 5 */
function canComplete(row: MerchantOrder): boolean {
   if (row.refundStatus !== 0) return false
   return (row.deliveryType === 1 && row.status === 3) || (row.deliveryType === 2 && row.status === 4)
}

/** 查询列表 */
function getList() {
   loading.value = true
   listOrder(queryParams.value).then(response => {
      orderList.value = response.rows
      total.value = response.total
      loading.value = false
   })
}

/** 搜索 */
function handleQuery() {
   queryParams.value.pageNum = 1
   getList()
}

/** 重置 */
function resetQuery() {
   proxy.resetForm("queryRef")
   handleQuery()
}

/** 地址摘要（快照 JSON） */
function addressBrief(row: MerchantOrder): string {
   if (!row.addressSnapshot) return "-"
   try {
      const a = JSON.parse(row.addressSnapshot)
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join("")
   } catch {
      return "-"
   }
}

/** 规格口味快照展示 */
function specText(specFlavorJson?: string): string {
   if (!specFlavorJson) return "-"
   try {
      const obj = JSON.parse(specFlavorJson)
      const parts: string[] = []
      if (obj.spec && obj.spec.name) parts.push(obj.spec.name)
      if (Array.isArray(obj.flavors)) {
         obj.flavors.forEach((f: any) => {
            if (f.values && f.values.length) parts.push(f.values.join("/"))
         })
      }
      return parts.length ? parts.join(" · ") : "-"
   } catch {
      return "-"
   }
}

/** 流转操作（带确认；重复点击由后端条件更新幂等拦截） */
function doTransition(row: MerchantOrder, action: string, actionName: string) {
   proxy.$modal.confirm(`确认对订单"${row.orderNo}"执行【${actionName}】吗？`).then(function () {
      const api = action === "accept" ? acceptOrder : action === "ready" ? readyOrder : action === "deliver" ? deliverOrder : completeOrder
      return api(row.id!)
   }).then(() => {
      proxy.$modal.msgSuccess(actionName + "成功")
      getList()
   }).catch(() => {})
}

/** 查看详情 */
function showDetail(row: MerchantOrder) {
   getOrder(row.id!).then(response => {
      detail.value = response.data || {}
      detailOpen.value = true
   })
}

/** T17 打印小票：新开标签页打开独立打印页（数据由打印页自行调详情接口） */
function printOrder(row: MerchantOrder) {
   window.open('/print/order/' + row.id, '_blank')
}

// 新订单推送 WebSocket 重连成功后自动刷新列表（一期不做消息补发，T15）
function onPushReconnect() {
   getList()
}
window.addEventListener(orderPush.RECONNECT_EVENT, onPushReconnect)
onUnmounted(() => {
   window.removeEventListener(orderPush.RECONNECT_EVENT, onPushReconnect)
})

getList()
</script>
