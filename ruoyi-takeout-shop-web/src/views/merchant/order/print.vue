<template>
   <div class="print-page">
      <!-- 工具栏（打印时隐藏） -->
      <div class="no-print toolbar">
         <el-button type="primary" icon="Printer" :disabled="loading || !!error" @click="doPrint">打 印</el-button>
         <el-button icon="Close" @click="closeWindow">关闭页面</el-button>
         <span class="tip">打印时请选择目标打印机，无打印机可选「另存为 PDF」</span>
      </div>

      <div v-if="loading" class="no-print state">小票加载中…</div>
      <div v-else-if="error" class="no-print state error">{{ error }}</div>

      <!-- 小票主体（@media print 只输出此节点） -->
      <div v-else-if="order" class="receipt">
         <div class="shop-name">{{ shopName }}</div>
         <div class="sub-title">厨房出餐单</div>

         <div class="divider" />
         <div class="order-no">{{ order.orderNo }}</div>
         <div class="row">
            <span>尾号 <b class="tail">{{ tailNo }}</b></span>
            <span>打印 {{ printTime }}</span>
         </div>

         <div class="divider" />
         <div class="row">
            <span>{{ order.deliveryType === 1 ? '堂食' : '外卖配送' }}</span>
            <span>{{ order.createTime }}</span>
         </div>
         <div class="line" v-if="order.deliveryType === 1">桌号：{{ order.tableNo }} 号桌</div>
         <div class="line" v-else>地址：{{ addressText }}</div>
         <div class="line">联系人：{{ order.contactName || '-' }} {{ order.contactPhone || '' }}</div>
         <div class="line">状态：{{ statusText }}</div>

         <div class="divider" />
         <div class="item" v-for="(it, idx) in items" :key="idx">
            <div class="item-name">{{ idx + 1 }}. {{ it.goodsName }} x{{ it.quantity }}</div>
            <div class="item-spec" v-if="specText(it)">[{{ specText(it) }}]</div>
         </div>

         <div class="divider" />
         <div class="amount-row"><span>菜品合计</span><span>¥{{ order.goodsAmount }}</span></div>
         <div class="amount-row" v-if="order.deliveryType === 2"><span>配送费</span><span>¥{{ order.deliveryFee }}</span></div>
         <div class="amount-row strong"><span>实付金额</span><span>¥{{ order.payAmount }}</span></div>

         <template v-if="order.remark">
            <div class="divider" />
            <div class="line">备注：{{ order.remark }}</div>
         </template>

         <div class="divider" />
         <div class="footer-tip">阿婆干饭社 感谢您的惠顾</div>
      </div>
   </div>
</template>

<script setup lang="ts" name="OrderPrint">
import { useRoute } from 'vue-router'
import { getOrder } from '@/api/merchant/order'
import { getShopInfo } from '@/api/merchant/shop'
import type { MerchantOrder } from '@/types/api/merchant/goods'

/** 订单明细快照（与后端 BizOrderItem 出参对应） */
interface OrderItem {
   goodsName?: string
   quantity?: number
   specFlavorJson?: string
}

const route = useRoute()
const orderId = Number(route.params.orderId)

const loading = ref<boolean>(true)
const error = ref<string>('')
const order = ref<MerchantOrder>()
const items = ref<OrderItem[]>([])
const shopName = ref<string>('阿婆干饭社')
const printTime = ref<string>('')
// 自动打印只触发一次，后续由商家手动点击重打
let autoPrinted = false

/** 状态字典（与订单列表页一致，退款状态优先展示） */
const statusMap: Record<number, string> = {
   0: '待支付',
   1: '待接单',
   2: '已接单制作中',
   3: '已出餐待取餐',
   4: '配送中',
   5: '已完成',
   6: '已取消'
}
const statusText = computed<string>(() => {
   if (!order.value) return '-'
   if (order.value.refundStatus === 1) return '退款审核中'
   if (order.value.refundStatus === 2) return '已退款'
   return statusMap[order.value.status!] || '未知'
})

const tailNo = computed<string>(() => (order.value?.orderNo || '').slice(-4))

/** 地址快照 JSON 解析（与订单列表页 addressBrief 一致） */
const addressText = computed<string>(() => {
   const raw = order.value?.addressSnapshot
   if (!raw) return '-'
   try {
      const a = JSON.parse(raw)
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join('') || '-'
   } catch {
      return '-'
   }
})

/** 规格口味快照 JSON 解析（与订单详情弹窗 specText 一致） */
function specText(it: OrderItem): string {
   if (!it.specFlavorJson) return ''
   try {
      const obj = JSON.parse(it.specFlavorJson)
      const parts: string[] = []
      if (obj.spec && obj.spec.name) parts.push(obj.spec.name)
      if (Array.isArray(obj.flavors)) {
         obj.flavors.forEach((f: any) => {
            if (f.values && f.values.length) parts.push(f.values.join('/'))
         })
      }
      return parts.join(' · ')
   } catch {
      return ''
   }
}

function doPrint() {
   window.print()
}

function closeWindow() {
   window.close()
}

onMounted(async () => {
   const d = new Date()
   const p = (n: number) => String(n).padStart(2, '0')
   printTime.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`

   if (!orderId) {
      error.value = '订单参数缺失，请从订单管理页重新打开'
      loading.value = false
      return
   }
   try {
      // 店铺信息仅用于小票抬头店名，加载失败不影响小票主体
      const [orderRes, shopRes] = await Promise.all([getOrder(orderId), getShopInfo().catch(() => null)])
      order.value = orderRes.data?.order
      items.value = orderRes.data?.items || []
      if (shopRes?.data?.shopName) shopName.value = shopRes.data.shopName
      if (!order.value) error.value = '订单不存在'
   } catch (e: any) {
      error.value = e?.msg || '加载失败，请确认已登录管理端后重试'
   }
   loading.value = false

   if (order.value && !autoPrinted) {
      autoPrinted = true
      nextTick(() => window.print())
   }
})
</script>

<style scoped>
.print-page {
   min-height: 100vh;
   background: #f0f0f0;
   padding: 16px 0 40px;
}

.toolbar {
   width: 340px;
   margin: 0 auto 12px;
   text-align: center;
}

.toolbar .tip {
   display: block;
   margin-top: 8px;
   font-size: 12px;
   color: #999;
}

.state {
   text-align: center;
   padding: 40px 0;
   color: #999;
}

.state.error {
   color: #f56c6c;
}

/* 热敏小票版式：等宽字体、窄幅、黑白 */
.receipt {
   width: 302px;
   margin: 0 auto;
   background: #fff;
   padding: 12px 14px;
   font-family: 'Courier New', Consolas, '宋体', monospace;
   color: #000;
   font-size: 13px;
   line-height: 1.5;
}

.shop-name {
   text-align: center;
   font-size: 18px;
   font-weight: bold;
   letter-spacing: 2px;
}

.sub-title {
   text-align: center;
   font-size: 15px;
   font-weight: bold;
   margin-top: 4px;
}

.divider {
   border-top: 1px dashed #000;
   margin: 8px 0;
}

.row {
   display: flex;
   justify-content: space-between;
   gap: 8px;
}

.order-no {
   word-break: break-all;
   font-size: 12px;
}

.tail {
   font-size: 16px;
}

.line {
   word-break: break-all;
}

.item {
   margin-bottom: 4px;
}

.item-name {
   font-weight: bold;
}

.item-spec {
   font-size: 12px;
   padding-left: 14px;
}

.amount-row {
   display: flex;
   justify-content: space-between;
}

.amount-row.strong {
   font-size: 16px;
   font-weight: bold;
}

.footer-tip {
   text-align: center;
   margin-top: 8px;
}

/* 打印：只输出小票，隐藏工具栏/状态文案 */
@media print {
   @page {
      size: 80mm auto;
      margin: 4mm;
   }

   body {
      background: #fff;
   }

   .no-print {
      display: none !important;
   }

   .print-page {
      background: #fff;
      padding: 0;
   }

   .receipt {
      width: 100%;
      margin: 0;
      padding: 0;
   }
}
</style>
