<template>
	<view class="page">
		<!-- 状态 tab -->
		<scroll-view scroll-x class="tab-bar">
			<view
				v-for="t in tabs"
				:key="t.value"
				:class="['tab-item', currentStatus === t.value ? 'active' : '']"
				@tap="switchTab(t.value)"
			>{{ t.label }}</view>
		</scroll-view>

		<!-- 订单列表 -->
		<view class="order-list">
			<view v-for="o in orders" :key="o.id" class="order-card ap-card" @tap="goDetail(o)">
				<view class="head">
					<text class="order-no">{{ o.orderNo }}</text>
					<!-- 退款状态优先展示（方案 3.4/4.4） -->
					<text :class="['status-tag', statusClass(o)]">{{ statusText(o) }}</text>
				</view>
				<view class="goods-row">
					<image
						v-for="(g, gi) in o.items.slice(0, 3)"
						:key="gi"
						class="thumb"
						:src="g.image || '/static/images/placeholder.png'"
						mode="aspectFill"
					/>
					<text class="goods-count" v-if="o.goodsCount > 3">...</text>
					<text class="goods-total">共 {{ o.goodsCount }} 件</text>
				</view>
				<view class="foot">
					<text class="time">{{ o.createTime }}</text>
					<view class="price-row">
						<text class="symbol">¥</text>
						<text class="price">{{ fix(o.payAmount) }}</text>
					</view>
				</view>
				<!-- 操作按钮 -->
				<view class="ops" @tap.stop>
					<template v-if="o.status === 0 && o.refundStatus === 0">
						<text class="countdown" v-if="o.remainSeconds > 0">{{ formatRemain(o.remainSeconds) }} 后自动关闭</text>
						<view class="op-btn plain ap-press" @tap="cancelOrder(o)">取消订单</view>
						<view class="op-btn primary ap-press" @tap="goPay(o)">去支付</view>
					</template>
					<template v-else>
						<!-- T20 评价提醒：已完成且未退款成功且未评价 → 去评价（带入订单首个商品） -->
						<view
							v-if="o.status === 5 && o.refundStatus !== 2 && !o.hasCommented && o.items && o.items.length"
							class="op-btn primary ap-press"
							@tap="goComment(o)"
						>去评价</view>
						<!-- T13：再次购买（主状态 1-5 且未退款成功；后端同条件拦截） -->
						<view v-if="o.canRepurchase" class="op-btn plain ap-press" @tap="repurchase(o)">再次购买</view>
						<view
							v-if="o.status >= 1 && o.status <= 3 && o.refundStatus === 0"
							class="op-btn primary ap-press"
							@tap="applyRefund(o)"
						>申请退款</view>
						<!-- 配送中：退款按钮置灰形态 + 方案提示文案 -->
						<view v-else-if="o.status === 4 && o.refundStatus === 0" class="op-btn disabled">配送中请联系商家协商</view>
					</template>
				</view>
			</view>

			<view v-if="!orders.length && !loading" class="ap-empty">
				<text class="em">🧾</text>
				{{ currentStatus === null ? '还没有订单，去下一单吧～' : '该状态下暂无订单' }}
			</view>
			<view v-if="loading" class="loading-tip">加载中...</view>
			<view v-if="finished && orders.length" class="loading-tip">— 没有更多了 —</view>
		</view>
	</view>
</template>

<script>
import { request } from '@/utils/request'

export default {
	data() {
		return {
			tabs: [
				{ label: '全部', value: null },
				{ label: '待支付', value: 0 },
				{ label: '进行中', value: -1 }, // 1/2/3/4 聚合，前端多状态查询由后端 status 参数逐值拼接？——见下方说明
				{ label: '已完成', value: 5 }
			],
			currentStatus: null,
			orders: [],
			pageNum: 1,
			pageSize: 10,
			total: 0,
			loading: false,
			finished: false
		}
	},
	onShow() {
		this.reload()
	},
	onReachBottom() {
		this.loadMore()
	},
	onPullDownRefresh() {
		this.reload(() => uni.stopPullDownRefresh())
	},
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		/** 状态文案：退款状态优先；主状态 3/4 按履约方式区分（T18：外卖区分骑手待取餐/骑手已取餐配送中） */
		statusText(o) {
			if (o.refundStatus === 1) return '退款审核中'
			if (o.refundStatus === 2) return '已退款'
			// 主状态 3：堂食是"待取餐"，外卖是"骑手待取餐"（商家出餐但骑手尚未取餐）
			if (o.status === 3) return o.deliveryType === 2 ? '骑手待取餐' : '待取餐'
			// 主状态 4（仅外卖）：骑手已取餐，配送中
			if (o.status === 4) return '骑手已取餐，配送中'
			const map = { 0: '待支付', 1: '待接单', 2: '制作中', 5: '已完成', 6: '已取消' }
			return map[o.status] || '未知'
		},
		statusClass(o) {
			if (o.refundStatus === 1) return 'refund'
			if (o.refundStatus === 2) return 'refunded'
			const map = { 0: 'unpaid', 1: 'doing', 2: 'doing', 3: 'doing', 4: 'doing', 5: 'done', 6: 'refunded' }
			return map[o.status] || 'refunded'
		},
		formatRemain(s) {
			const m = Math.floor(s / 60)
			return m + '分' + (s % 60) + '秒'
		},
		switchTab(value) {
			this.currentStatus = value
			this.reload()
		},
		/** 加载列表（"进行中" tab 聚合 1/2/3/4：取全部后前端过滤，待支付/已完成走后端筛选） */
		async reload(done) {
			this.pageNum = 1
			this.finished = false
			this.orders = []
			await this.loadPage()
			if (done) done()
		},
		async loadMore() {
			if (this.finished || this.loading) return
			this.pageNum++
			await this.loadPage()
		},
		async loadPage() {
			this.loading = true
			try {
				// "进行中" tab：后端按单状态筛选，这里取全部后本地过滤（数据量小，简单可靠）
				const url = '/api/order/list?pageNum=' + this.pageNum + '&pageSize=' + this.pageSize +
					(this.currentStatus !== null && this.currentStatus !== -1 ? '&status=' + this.currentStatus : '')
				const body = await request({ url, needAuth: true })
				let rows = body.rows || []
				if (this.currentStatus === -1) {
					rows = rows.filter(o => o.status >= 1 && o.status <= 4)
				}
				this.orders = this.pageNum === 1 ? rows : this.orders.concat(rows)
				this.total = body.total || 0
				if (this.currentStatus === -1) {
					this.finished = this.orders.length >= this.total || rows.length < this.pageSize
				} else {
					this.finished = this.orders.length >= this.total
				}
			} catch (e) {
				uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
			} finally {
				this.loading = false
			}
		},
		goDetail(o) {
			uni.navigateTo({ url: '/pages/order/detail?id=' + o.id })
		},
		goPay(o) {
			uni.navigateTo({ url: '/pages/pay/cashier?id=' + o.id })
		},
		/** 取消订单（仅待支付；后端条件更新幂等） */
		cancelOrder(o) {
			uni.showModal({
				title: '取消订单',
				content: '确定取消该订单吗？',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/order/cancel/' + o.id, method: 'PUT' })
						uni.showToast({ title: '订单已取消', icon: 'none' })
						this.reload()
					} catch (e) {
						uni.showToast({ title: e.message || '取消失败', icon: 'none' })
					}
				}
			})
		},
		/** 申请退款（主状态 1-3 可点；跳退款申请页） */
		applyRefund(o) {
			uni.navigateTo({ url: '/pages/order/refund?orderId=' + o.id })
		},
		/** T20 评价提醒：跳商品留言页并带入订单（后端按订单归属与资格校验） */
		goComment(o) {
			const first = o.items[0]
			if (!first || !first.goodsId) {
				uni.showToast({ title: '订单明细缺失，无法评价', icon: 'none' })
				return
			}
			uni.navigateTo({
				url: '/pages/goods/comments?goodsId=' + first.goodsId
					+ '&name=' + encodeURIComponent(first.goodsName || '')
					+ '&orderId=' + o.id
			})
		},
		/**
		 * 再次购买（T13）：调后端逐项加购，按返回结果分三态处理
		 *  - 全部成功：直接跳购物车
		 *  - 部分成功：先列出不可加购项及原因，用户确认后跳购物车
		 *  - 全部失败：提示"商品均已下架/售罄"，不跳转
		 */
		async repurchase(o) {
			uni.showLoading({ title: '正在加入购物车...' })
			let res
			try {
				res = await request({ url: '/api/order/' + o.id + '/repurchase', method: 'POST' })
			} catch (e) {
				uni.hideLoading()
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
				return
			}
			uni.hideLoading()
			const data = res.data || {}
			const added = data.added || []
			const skipped = data.skipped || []
			const adjustTips = added.filter(a => a.quantityReduced && a.adjustTip).map(a => a.adjustTip)

			// 全部失败
			if (data.allFailed || !added.length) {
				const lines = skipped.map(s => '· ' + this.itemLabel(s) + '：' + s.reason).join('\n')
				uni.showModal({
					title: '无法再次购买',
					content: lines || '商品均已下架/售罄',
					showCancel: false
				})
				return
			}
			// 全部成功（无跳过、无减量、无口味调整）
			if (!skipped.length && !adjustTips.length && !data.flavorAdjusted) {
				uni.showToast({ title: '已加入购物车', icon: 'none' })
				setTimeout(() => uni.navigateTo({ url: '/pages/cart/cart' }), 500)
				return
			}
			// 部分成功：列出跳过项与减量提示，确认后跳购物车
			const parts = []
			if (skipped.length) {
				parts.push('以下商品未能加入：\n' + skipped.map(s => '· ' + this.itemLabel(s) + '：' + s.reason).join('\n'))
			}
			if (adjustTips.length) {
				parts.push(adjustTips.map(t => '· ' + t).join('\n'))
			}
			if (data.flavorAdjusted) {
				parts.push('部分口味已调整，已按现有口味加入')
			}
			uni.showModal({
				title: '部分商品已加入购物车',
				content: parts.join('\n\n'),
				confirmText: '去看看',
				success: (r) => {
					if (r.confirm) uni.navigateTo({ url: '/pages/cart/cart' })
				}
			})
		},
		/** 跳过项展示名：菜品名（规格） */
		itemLabel(s) {
			return s.specName ? (s.goodsName + '（' + s.specName + '）') : s.goodsName
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding-bottom: 40rpx;
}

.tab-bar {
	white-space: nowrap;
	background-color: $ap-card;
	padding: 0 12rpx;
	border-radius: $ap-radius;
	margin: 20rpx;
	box-shadow: $ap-shadow;

	.tab-item {
		display: inline-block;
		padding: 20rpx 36rpx;
		font-size: 28rpx;
		color: $ap-text-light;

		&.active {
			color: $ap-primary;
			font-weight: 700;
			border-bottom: 4rpx solid $ap-primary;
		}
	}
}

.order-list {
	padding: 0 20rpx;
}

.order-card {
	padding: 24rpx;
	margin-bottom: 20rpx;

	.head {
		display: flex;
		align-items: center;

		.order-no {
			font-size: 24rpx;
			color: $ap-text-light;
			flex: 1;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		.status-tag {
			margin-left: 12rpx;
			font-size: 26rpx;
			font-weight: 700;

			&.unpaid { color: $ap-secondary; }
			&.doing { color: $ap-primary; }
			&.done { color: $ap-green; }
			&.refund { color: #E57373; }
			&.refunded { color: $ap-text-light; }
		}
	}

	.goods-row {
		display: flex;
		align-items: center;
		margin-top: 20rpx;

		.thumb {
			width: 96rpx;
			height: 96rpx;
			border-radius: 12rpx;
			margin-right: 12rpx;
			background-color: $ap-bg;
		}

		.goods-count {
			font-size: 24rpx;
			color: $ap-text-light;
		}

		.goods-total {
			margin-left: auto;
			font-size: 24rpx;
			color: $ap-text-light;
		}
	}

	.foot {
		margin-top: 16rpx;
		display: flex;
		align-items: center;

		.time {
			font-size: 22rpx;
			color: $ap-text-light;
		}

		.price-row {
			margin-left: auto;

			.symbol {
				font-size: 22rpx;
				color: $ap-primary;
			}

			.price {
				font-size: 32rpx;
				font-weight: 700;
				color: $ap-primary;
			}
		}
	}

	.ops {
		margin-top: 16rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid $ap-bg;
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 16rpx;

		.countdown {
			margin-right: auto;
			font-size: 22rpx;
			color: $ap-primary;
		}

		.op-btn {
			padding: 10rpx 28rpx;
			font-size: 24rpx;
			border-radius: $ap-radius-pill;

			&.primary {
				color: #FFFFFF;
				background-color: $ap-primary;
			}

			&.plain {
				color: $ap-text;
				background-color: $ap-bg;
			}

			&.disabled {
				color: $ap-text-light;
				background-color: $ap-bg;
				opacity: 0.7;
			}
		}
	}
}

.loading-tip {
	text-align: center;
	padding: 24rpx;
	font-size: 22rpx;
	color: $ap-text-light;
}
</style>
