<template>
   <div class="app-container takeout-page">
      <el-row :gutter="10" class="takeout-toolbar">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Refresh" @click="getList">刷新</el-button>
         </el-col>
         <right-toolbar :showSearch="false" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-alert
         title="每日限量库存：库存耗尽自动售罄，释放（超时关单/取消/退款）后自动恢复；手动售罄优先级更高，不会被自动逻辑覆盖。"
         type="info"
         :closable="false"
         style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="stockList" class="takeout-table">
         <el-table-column label="菜品" prop="goodsName" min-width="180" :show-overflow-tooltip="true" />
         <el-table-column label="规格" prop="specName" align="center" min-width="130" />
         <el-table-column label="今日限量" prop="limitQty" align="center" width="100" />
         <el-table-column label="已售" prop="soldQty" align="center" width="90" />
         <el-table-column label="剩余" align="center" width="100">
            <template #default="scope">
               <el-tag :type="scope.row.remainQty > 0 ? 'success' : 'danger'">{{ scope.row.remainQty }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="售罄状态" align="center" min-width="150">
            <template #default="scope">
               <el-tag v-if="scope.row.manualSoldOut === '1'" type="warning" style="margin-right: 6px">手动售罄</el-tag>
               <el-tag v-if="scope.row.autoSoldOut === '1'" type="danger">自动售罄</el-tag>
               <span v-if="scope.row.manualSoldOut !== '1' && scope.row.autoSoldOut !== '1'" style="color: #909399">正常</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Refresh" @click="handleReset(scope.row)" v-hasPermi="['merchant:stock:reset']">重置补货</el-button>
            </template>
         </el-table-column>
      </el-table>
   </div>
</template>

<script setup lang="ts" name="Stock">
import { listTodayStock, resetStock } from '@/api/merchant/stock'
import type { StockRow } from '@/api/merchant/stock'

const { proxy } = getCurrentInstance()

const loading = ref<boolean>(true)
const stockList = ref<StockRow[]>([])

/** 查询今日库存 */
function getList() {
   loading.value = true
   listTodayStock().then(response => {
      stockList.value = response.data || []
      loading.value = false
   }).catch(() => {
      loading.value = false
   })
}

/** 手动重置当天库存（临时补货） */
function handleReset(row: StockRow) {
   proxy.$modal.confirm(
      '确认重置「' + row.goodsName + ' ' + row.specName + '」的今日库存？<br/>已售数量将归零（用于临时补货）。'
   ).then(() => {
      return proxy.$prompt('请输入今日限量值（留空则沿用配置值）', '重置库存', {
         inputPlaceholder: '如 20',
         inputPattern: /^\d*$/,
         inputErrorMessage: '请输入非负整数'
      })
   }).then(({ value }: { value: string }) => {
      const limitQty = value === '' || value === undefined ? undefined : Number(value)
      return resetStock(row.goodsId, row.specId, limitQty)
   }).then(() => {
      proxy.$modal.msgSuccess('重置成功')
      getList()
   }).catch(() => {})
}

getList()
</script>
