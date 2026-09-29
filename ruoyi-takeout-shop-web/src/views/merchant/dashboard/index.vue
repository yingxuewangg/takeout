<template>
   <div class="app-container takeout-page">
      <!-- 顶部筛选：时间范围 + 履约方式 -->
      <el-form :inline="true" class="takeout-search">
         <el-form-item label="时间范围">
            <el-radio-group v-model="query.range" @change="handleRangeChange">
               <el-radio-button value="today">今日</el-radio-button>
               <el-radio-button value="yesterday">昨日</el-radio-button>
               <el-radio-button value="last7">近7天</el-radio-button>
               <el-radio-button value="last30">近30天</el-radio-button>
               <el-radio-button value="month">本月</el-radio-button>
               <el-radio-button value="custom">自定义</el-radio-button>
            </el-radio-group>
         </el-form-item>
         <el-form-item label="自定义" v-if="query.range === 'custom'">
            <el-date-picker
               v-model="customRange"
               type="daterange"
               value-format="YYYY-MM-DD"
               range-separator="至"
               start-placeholder="开始日期"
               end-placeholder="结束日期"
               :clearable="false"
               @change="handleCustomChange"
            />
         </el-form-item>
         <el-form-item label="履约方式">
            <el-select v-model="query.deliveryType" placeholder="全部" clearable style="width: 130px" @change="loadAll">
               <el-option label="堂食" :value="1" />
               <el-option label="外卖" :value="2" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Refresh" @click="loadAll">刷新</el-button>
         </el-form-item>
      </el-form>

      <!-- 概览卡片 -->
      <el-row :gutter="16" v-loading="loading.overview">
         <el-col :xs="12" :sm="8" :md="6" v-for="card in cards" :key="card.label" style="margin-bottom: 16px">
            <el-card shadow="hover" class="stat-card">
               <div class="stat-label">{{ card.label }}</div>
               <div class="stat-value" :class="card.cls">
                  <span class="unit" v-if="card.money">¥</span>{{ card.value }}
               </div>
               <div class="stat-tip" v-if="card.tip">{{ card.tip }}</div>
            </el-card>
         </el-col>
      </el-row>

      <!-- 趋势图（T19：支持指标切换） -->
      <el-card shadow="never" style="margin-top: 8px">
         <template #header>
            <span>趋势</span>
            <el-radio-group v-model="trendMode" size="small" style="margin-left: 12px" @change="renderTrend">
               <el-radio-button value="main">营业额/订单量</el-radio-button>
               <el-radio-button value="price">客单价</el-radio-button>
               <el-radio-button value="refund">退款率</el-radio-button>
            </el-radio-group>
            <span class="header-tip">区间 ≤ 2 天按小时展示，否则按天</span>
         </template>
         <div v-loading="loading.trend">
            <div ref="trendRef" class="chart chart-trend"></div>
            <el-empty v-if="!loading.trend && !trendData.length" description="该区间暂无已完成订单" :image-size="80" />
         </div>
      </el-card>

      <!-- T19 高峰时段 + 出餐效率 -->
      <el-row :gutter="16" style="margin-top: 16px">
         <el-col :xs="24" :md="14">
            <el-card shadow="never">
               <template #header>
                  <span>高峰时段</span>
                  <span class="header-tip">跨天按小时累计有效订单，深色为峰值时段</span>
               </template>
               <div v-loading="loading.peak">
                  <div ref="peakRef" class="chart chart-peak"></div>
                  <el-empty v-if="!loading.peak && !peakHasData" description="该区间暂无已完成订单" :image-size="80" />
               </div>
            </el-card>
         </el-col>
         <el-col :xs="24" :md="10">
            <el-card shadow="never">
               <template #header>
                  <span>出餐效率</span>
                  <span class="header-tip">基于接单/出餐/取餐/送达时间（T18/T19 字段）</span>
               </template>
               <div v-loading="loading.efficiency">
                  <el-row :gutter="12">
                     <el-col :span="12" v-for="item in efficiencyItems" :key="item.label" style="margin-bottom: 12px">
                        <div class="eff-item">
                           <div class="eff-label">{{ item.label }}</div>
                           <div class="eff-value">{{ item.value }}</div>
                           <div class="eff-tip">样本 {{ item.samples }} 单</div>
                        </div>
                     </el-col>
                  </el-row>
                  <el-empty v-if="!loading.efficiency && !efficiencyHasData" description="暂无带环节时间的订单（新流转才会记录）" :image-size="60" />
               </div>
            </el-card>
         </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
         <!-- 菜品销量排行 -->
         <el-col :xs="24" :md="14">
            <el-card shadow="never">
               <template #header>
                  <span>菜品销量排行</span>
                  <span style="float: right">
                     <el-radio-group v-model="rank.sortBy" size="small" @change="loadRank">
                        <el-radio-button value="quantity">按数量</el-radio-button>
                        <el-radio-button value="amount">按金额</el-radio-button>
                     </el-radio-group>
                     <el-select v-model="rank.topN" size="small" style="width: 100px; margin-left: 8px" @change="loadRank">
                        <el-option :value="5" label="Top 5" />
                        <el-option :value="10" label="Top 10" />
                        <el-option :value="20" label="Top 20" />
                     </el-select>
                  </span>
               </template>
               <el-table v-loading="loading.rank" :data="rankData" class="takeout-table">
                  <el-table-column type="index" label="排名" width="70" align="center" />
                  <el-table-column label="菜品" prop="goodsName" min-width="160" :show-overflow-tooltip="true" />
                  <el-table-column label="销量" prop="quantity" align="right" width="90" />
                  <el-table-column label="销售额(元)" align="right" width="120">
                     <template #default="{ row }">{{ fix(row.amount) }}</template>
                  </el-table-column>
                  <el-table-column label="退款关联" align="right" width="90">
                     <template #default="{ row }">
                        <span :class="{ 'refund-hit': (row.refundCount || 0) > 0 }">{{ row.refundCount ?? 0 }}</span>
                     </template>
                  </el-table-column>
               </el-table>
               <el-empty v-if="!loading.rank && !rankData.length" description="该区间暂无销量数据" :image-size="80" />
            </el-card>
         </el-col>

         <!-- 分类占比 -->
         <el-col :xs="24" :md="10">
            <el-card shadow="never">
               <template #header>分类营业额占比</template>
               <div v-loading="loading.category">
                  <div ref="categoryRef" class="chart chart-pie"></div>
                  <el-empty v-if="!loading.category && !categoryData.length" description="该区间暂无分类数据" :image-size="80" />
               </div>
            </el-card>
         </el-col>
      </el-row>

      <div class="dashboard-note">
         统计口径：仅「已完成」订单计入营业额与订单量（按下单时间统计）；已退款金额从净营业额扣减，退款中单独展示；
         菜品排行与分类占比基于订单明细，已排除退款成功订单；退款率 = 退款成功订单数 ÷（有效订单数 + 退款成功订单数）；
         出餐效率只统计对应环节时间已记录的订单（T18 起流转才写入时间，历史订单不参与）。
      </div>
   </div>
</template>

<script setup lang="ts" name="Dashboard">
import * as echarts from 'echarts'
import { getOverview, getTrend, getGoodsRank, getCategoryStat, getPeakHours, getEfficiency } from '@/api/merchant/dashboard'
import type {
  DashboardQuery, DashboardOverview, DashboardTrendPoint, DashboardGoodsRank, DashboardCategory,
  DashboardPeakHour, DashboardEfficiency
} from '@/api/merchant/dashboard'

const { proxy } = getCurrentInstance()

/** 查询条件（默认近 7 天） */
const query = reactive<DashboardQuery>({ range: 'last7', deliveryType: undefined })
const customRange = ref<string[]>([])
const rank = reactive({ sortBy: 'quantity', topN: 10 })
/** T19 趋势指标切换：营业额/订单量、客单价、退款率 */
const trendMode = ref<'main' | 'price' | 'refund'>('main')

const loading = reactive({ overview: false, trend: false, rank: false, category: false, peak: false, efficiency: false })
const overview = ref<DashboardOverview | null>(null)
const trendData = ref<DashboardTrendPoint[]>([])
const rankData = ref<DashboardGoodsRank[]>([])
const categoryData = ref<DashboardCategory[]>([])
const peakData = ref<DashboardPeakHour[]>([])
const efficiencyData = ref<DashboardEfficiency | null>(null)

const trendRef = ref<HTMLElement>()
const categoryRef = ref<HTMLElement>()
const peakRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let peakChart: echarts.ECharts | null = null

function fix(v?: number) {
   return Number(v || 0).toFixed(2)
}

/** 概览卡片数据（含退款相关均为独立展示，不做隐式扣减） */
const cards = computed(() => {
   const o = overview.value
   return [
      { label: '净营业额', value: fix(o?.netRevenue), money: true, cls: 'primary',
        tip: o ? `营业额 ¥${fix(o.revenue)} − 退款扣减 ¥${fix(o.refundedInRevenue)}` : '' },
      { label: '有效订单数', value: o?.orderCount ?? 0, money: false, cls: '',
        tip: o ? `已取消 ${o.cancelledCount} · 退款成功 ${o.refundedCount}` : '' },
      { label: '客单价', value: fix(o?.avgOrderAmount), money: true, cls: '' },
      { label: '退款率', value: `${Number(o?.refundRate || 0).toFixed(2)}%`, money: false, cls: 'danger',
        tip: o ? `退款成功 ${o.refundedCount} ÷（有效 ${o.orderCount} + 退款 ${o.refundedCount}）` : '' },
      { label: '退款金额', value: fix(o?.refundedAmount), money: true, cls: 'danger',
        tip: o ? `退款中 ¥${fix(o.refundingAmount)}（未扣减）` : '' }
   ]
})

/** T19 秒数格式化（null=无样本显示 —） */
function fmtDur(seconds?: number | null): string {
   if (seconds === null || seconds === undefined) return '—'
   const s = Math.round(Number(seconds))
   if (s < 60) return `${s} 秒`
   return `${Math.floor(s / 60)} 分 ${s % 60} 秒`
}

/** T19 出餐效率四段指标 */
const efficiencyItems = computed(() => {
   const e = efficiencyData.value
   return [
      { label: '平均接单耗时', value: fmtDur(e?.avgAcceptSeconds), samples: e?.acceptSamples ?? 0 },
      { label: '平均制作耗时', value: fmtDur(e?.avgMakeSeconds), samples: e?.makeSamples ?? 0 },
      { label: '平均取餐等待', value: fmtDur(e?.avgPickupWaitSeconds), samples: e?.pickupWaitSamples ?? 0 },
      { label: '平均配送耗时', value: fmtDur(e?.avgDeliverSeconds), samples: e?.deliverSamples ?? 0 }
   ]
})

const efficiencyHasData = computed(() => {
   const e = efficiencyData.value
   return !!e && ((e.acceptSamples ?? 0) + (e.makeSamples ?? 0) + (e.pickupWaitSamples ?? 0) + (e.deliverSamples ?? 0)) > 0
})

const peakHasData = computed(() => peakData.value.some(p => p.orderCount > 0))

/** 时间范围切换：自定义需选日期后再查询 */
function handleRangeChange() {
   if (query.range === 'custom') {
      if (customRange.value && customRange.value.length === 2) {
         loadAll()
      }
      return
   }
   loadAll()
}

function handleCustomChange() {
   if (customRange.value && customRange.value.length === 2) {
      loadAll()
   }
}

/** 组装请求参数（custom 时带上区间） */
function buildParams() {
   const params: DashboardQuery = { range: query.range, deliveryType: query.deliveryType }
   if (query.range === 'custom' && customRange.value && customRange.value.length === 2) {
      params.startDate = customRange.value[0]
      params.endDate = customRange.value[1]
   }
   return params
}

/** 加载全部区块（各区块独立容错：单个接口异常不影响其他区块展示） */
function loadAll() {
   loadOverview()
   loadTrend()
   loadRank()
   loadCategory()
   loadPeak()
   loadEfficiency()
}

function loadOverview() {
   loading.overview = true
   getOverview(buildParams())
      .then(res => { overview.value = res.data })
      .catch(() => { overview.value = null })
      .finally(() => { loading.overview = false })
}

function loadTrend() {
   loading.trend = true
   getTrend({ ...buildParams(), granularity: 'auto' })
      .then(res => {
         trendData.value = res.data || []
         renderTrend()
      })
      .catch(() => { trendData.value = []; renderTrend() })
      .finally(() => { loading.trend = false })
}

function loadRank() {
   loading.rank = true
   getGoodsRank({ ...buildParams(), sortBy: rank.sortBy, topN: rank.topN })
      .then(res => { rankData.value = res.data || [] })
      .catch(() => { rankData.value = [] })
      .finally(() => { loading.rank = false })
}

function loadCategory() {
   loading.category = true
   getCategoryStat(buildParams())
      .then(res => {
         categoryData.value = res.data || []
         renderCategory()
      })
      .catch(() => { categoryData.value = []; renderCategory() })
      .finally(() => { loading.category = false })
}

function loadPeak() {
   loading.peak = true
   getPeakHours(buildParams())
      .then(res => {
         peakData.value = res.data || []
         renderPeak()
      })
      .catch(() => { peakData.value = []; renderPeak() })
      .finally(() => { loading.peak = false })
}

function loadEfficiency() {
   loading.efficiency = true
   getEfficiency(buildParams())
      .then(res => { efficiencyData.value = res.data })
      .catch(() => { efficiencyData.value = null })
      .finally(() => { loading.efficiency = false })
}

/** 趋势折线图（T19：main=营业额/订单量双轴；price=客单价；refund=退款率） */
function renderTrend() {
   if (!trendRef.value) return
   if (!trendChart) {
      trendChart = echarts.init(trendRef.value)
   }
   const labels = trendData.value.map(p => p.timeLabel)
   let option: echarts.EChartsOption
   if (trendMode.value === 'price') {
      option = {
         tooltip: { trigger: 'axis', valueFormatter: (v: any) => `¥${Number(v).toFixed(2)}` },
         grid: { left: 60, right: 30, top: 40, bottom: 30 },
         xAxis: { type: 'category', data: labels, axisLabel: { hideOverlap: true } },
         yAxis: [{ type: 'value', name: '客单价(元)' }],
         series: [{
            name: '客单价', type: 'line', smooth: true,
            data: trendData.value.map(p => Number(p.avgOrderAmount || 0)),
            areaStyle: { opacity: 0.12 }, itemStyle: { color: '#67C23A' }
         }]
      } as echarts.EChartsOption
   } else if (trendMode.value === 'refund') {
      option = {
         tooltip: { trigger: 'axis', valueFormatter: (v: any) => `${Number(v).toFixed(2)}%` },
         grid: { left: 60, right: 30, top: 40, bottom: 30 },
         xAxis: { type: 'category', data: labels, axisLabel: { hideOverlap: true } },
         yAxis: [{ type: 'value', name: '退款率(%)', max: 100 }],
         series: [{
            name: '退款率', type: 'line', smooth: true,
            data: trendData.value.map(p => Number(p.refundRate || 0)),
            areaStyle: { opacity: 0.12 }, itemStyle: { color: '#F56C6C' }
         }]
      } as echarts.EChartsOption
   } else {
      option = {
         tooltip: { trigger: 'axis' },
         legend: { data: ['营业额', '订单量'] },
         grid: { left: 50, right: 50, top: 40, bottom: 30 },
         xAxis: { type: 'category', data: labels, axisLabel: { hideOverlap: true } },
         yAxis: [
            { type: 'value', name: '营业额(元)', axisLabel: { formatter: '{value}' } },
            { type: 'value', name: '订单量', minInterval: 1 }
         ],
         series: [
            {
               name: '营业额', type: 'line', smooth: true, yAxisIndex: 0,
               data: trendData.value.map(p => Number(p.revenue || 0)),
               areaStyle: { opacity: 0.12 }, itemStyle: { color: '#409EFF' }
            },
            {
               name: '订单量', type: 'line', smooth: true, yAxisIndex: 1,
               data: trendData.value.map(p => Number(p.orderCount || 0)),
               itemStyle: { color: '#E6A23C' }
            }
         ]
      } as echarts.EChartsOption
   }
   trendChart.setOption(option, true)
   trendChart.resize()
}

/** T19 高峰时段柱状图：峰值小时深色高亮 */
function renderPeak() {
   if (!peakRef.value) return
   if (!peakChart) {
      peakChart = echarts.init(peakRef.value)
   }
   peakChart.setOption({
      tooltip: {
         trigger: 'axis',
         formatter: (params: any) => {
            const p = Array.isArray(params) ? params[0] : params
            const row = peakData.value[p.dataIndex]
            return `${row?.hour ?? p.name} 点<br/>有效订单 ${row?.orderCount ?? 0} 单<br/>营业额 ¥${Number(row?.revenue || 0).toFixed(2)}${row?.peak ? '<br/>⭐ 峰值时段' : ''}`
         }
      },
      grid: { left: 50, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: peakData.value.map(p => `${p.hour}`), axisLabel: { formatter: '{value}时' } },
      yAxis: { type: 'value', name: '订单量', minInterval: 1 },
      series: [{
         name: '有效订单', type: 'bar', barMaxWidth: 28,
         data: peakData.value.map(p => ({
            value: p.orderCount,
            itemStyle: { color: p.peak ? '#F56C6C' : '#409EFF', opacity: p.orderCount > 0 ? 1 : 0.25 }
         }))
      }]
   }, true)
   peakChart.resize()
}

/** 分类占比饼图 */
function renderCategory() {
   if (!categoryRef.value) return
   if (!categoryChart) {
      categoryChart = echarts.init(categoryRef.value)
   }
   categoryChart.setOption({
      tooltip: {
         trigger: 'item',
         formatter: (p: any) => `${p.name}<br/>¥${Number(p.value).toFixed(2)}（${p.percent}%）`
      },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
         type: 'pie',
         radius: ['42%', '68%'],
         avoidLabelOverlap: true,
         label: { formatter: '{b}\n{d}%' },
         data: categoryData.value.map(c => ({ name: c.categoryName, value: Number(c.amount || 0) }))
      }]
   }, true)
   categoryChart.resize()
}

/** 窗口尺寸变化时重绘（图表自适应） */
function handleResize() {
   trendChart?.resize()
   categoryChart?.resize()
   peakChart?.resize()
}

onMounted(() => {
   loadAll()
   window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
   window.removeEventListener('resize', handleResize)
   trendChart?.dispose()
   categoryChart?.dispose()
   peakChart?.dispose()
   trendChart = null
   categoryChart = null
   peakChart = null
})
</script>

<style lang="scss" scoped>
.stat-card {
   .stat-label {
      font-size: 13px;
      color: #909399;
   }

   .stat-value {
      margin-top: 8px;
      font-size: 24px;
      font-weight: 700;
      color: #303133;

      .unit {
         font-size: 14px;
         margin-right: 2px;
      }

      &.primary { color: #409EFF; }
      &.danger { color: #F56C6C; }
   }

   .stat-tip {
      margin-top: 6px;
      font-size: 12px;
      color: #a8abb2;
      min-height: 18px;
   }
}

.header-tip {
   margin-left: 10px;
   font-size: 12px;
   color: #a8abb2;
}

.chart {
   width: 100%;
}

.chart-trend {
   height: 320px;
}

.chart-pie {
   height: 320px;
}

.chart-peak {
   height: 280px;
}

/* T19 出餐效率指标块 */
.eff-item {
   padding: 10px 12px;
   background-color: #f5f7fa;
   border-radius: 6px;

   .eff-label {
      font-size: 12px;
      color: #909399;
   }

   .eff-value {
      margin-top: 6px;
      font-size: 18px;
      font-weight: 700;
      color: #303133;
   }

   .eff-tip {
      margin-top: 4px;
      font-size: 12px;
      color: #a8abb2;
   }
}

.refund-hit {
   color: #f56c6c;
   font-weight: 600;
}

.dashboard-note {
   margin-top: 16px;
   padding: 10px 12px;
   font-size: 12px;
   line-height: 1.7;
   color: #909399;
   background-color: #f5f7fa;
   border-radius: 4px;
}
</style>
