<template>
	<view class="page" v-if="order.id">
		<!-- 状态卡（退款状态优先展示） -->
		<view class="status-card">
			<text class="status-big">{{ statusText }}</text>
			<text class="status-sub">{{ statusSubText }}</text>
			<!-- 流转进度条（已取消不展示） -->
			<view class="steps" v-if="order.status !== 6 && order.refundStatus !== 1 && order.refundStatus !== 2">
				<view
					v-for="(s, i) in stepList"
					:key="i"
					:class="['step', stepIndex >= i ? 'done' : '']"
				>
					<view class="dot"></view>
					<text class="step-label">{{ s }}</text>
				</view>
			</view>
			<view class="countdown" v-if="order.status === 0 && remainSeconds > 0">
				⏰ {{ formatRemain }} 后未支付自动关闭
			</view>
		</view>

		<!-- 堂食桌号 / 外卖地址 -->
		<view class="info-card ap-card">
			<template v-if="order.deliveryType === 1">
				<view class="info-row">
					<text class="label">堂食桌号</text>
					<text class="value strong">{{ order.tableNo }} 号桌</text>
				</view>
				<view class="info-row" v-if="order.contactPhone">
					<text class="label">预留电话</text>
					<text class="value">{{ order.contactPhone }}</text>
				</view>
			</template>
			<template v-else>
				<view class="addr-row" v-if="addressInfo">
					<text class="addr-icon">📍</text>
					<view class="addr-body">
						<view class="addr-line1">
							<text class="addr-name">{{ addressInfo.name }}</text>
							<text class="addr-phone">{{ addressInfo.phone }}</text>
						</view>
						<text class="addr-detail">{{ addressFull }}</text>
					</view>
				</view>
			</template>
		</view>

		<!-- 菜品明细 -->
		<view class="items-card ap-card">
			<view v-for="(item, i) in order.items" :key="i" class="item-row">
				<image class="thumb" :src="item.image || '/static/images/placeholder.png'" mode="aspectFill" />
				<view class="item-info">
					<text class="item-name">{{ item.goodsName }}</text>
					<text class="item-spec" v-if="specText(item)">{{ specText(item) }}</text>
				</view>
				<view class="item-right">
					<text class="item-qty">x{{ item.quantity }}</text>
					<text class="item-subtotal">¥{{ fix(item.subtotal) }}</text>
					<!-- T20 评价提醒：已完成且未退款成功且未评价 → 明细级去评价 -->
					<text
						v-if="order.status === 5 && order.refundStatus !== 2 && !order.hasCommented && item.goodsId"
						class="comment-link ap-press"
						@tap="goComment(item)"
					>评价</text>
				</view>
			</view>
			<view class="sum-row">
				<text>菜品合计</text>
				<text>¥{{ fix(order.goodsAmount) }}</text>
			</view>
			<view class="sum-row" v-if="order.deliveryType === 2">
				<text>配送费</text>
				<text>¥{{ fix(order.deliveryFee) }}</text>
			</view>
			<view class="sum-row total">
				<text>实付</text>
				<text class="total-price">¥{{ fix(order.payAmount) }}</text>
			</view>
		</view>

		<!-- 退款进度（refund_status=1 审核中 / 有历史申请时展示；含商家电话便于沟通） -->
		<view class="refund-card ap-card" v-if="order.refundStatus === 1 || (refunds.length && order.refundStatus !== 2)">
			<view class="card-title-row">
				<text class="card-title">退款进度</text>
				<text class="shop-phone" @tap="callShop" v-if="shopPhone">☎️ 联系商家：{{ shopPhone }}</text>
			</view>
			<view v-for="(r, i) in refunds" :key="r.id" class="refund-item">
				<view class="refund-line1">
					<text class="refund-amount">¥{{ fix(r.refundAmount) }}</text>
					<text :class="['refund-tag', 'tag-' + r.auditStatus]">
						{{ r.auditStatus === 0 ? '审核中' : r.auditStatus === 1 ? '已退款' : (r.isAutoReject === 1 ? '超时自动驳回' : '已驳回') }}
					</text>
				</view>
				<text class="refund-reason">原因：{{ r.reason }}</text>
				<text class="refund-reject" v-if="r.rejectReason">商家回复：{{ r.rejectReason }}</text>
				<text class="refund-time">{{ r.createTime }}</text>
			</view>
		</view>

		<!-- 备注 -->
		<view class="remark-card ap-card" v-if="order.remark">
			<text class="label">订单备注</text>
			<text class="remark">{{ order.remark }}</text>
		</view>

		<!-- 底部操作区 -->
		<view class="action-bar">
			<template v-if="order.status === 0 && order.refundStatus === 0">
				<view class="op plain ap-press" @tap="cancelOrder">取消订单</view>
				<view class="op primary ap-press" @tap="goPay">去支付</view>
			</template>
			<!-- 配送中：退款按钮置灰 + 方案要求提示文案（T7） -->
			<view v-else-if="order.status === 4 && order.refundStatus === 0" class="op disabled full">
				配送中请联系商家协商
			</view>
			<!-- 主状态 1-3 且无进行中退款：可申请退款（整单，跳退款申请页） -->
			<view
				v-else-if="order.status >= 1 && order.status <= 3 && order.refundStatus === 0"
				class="op plain full ap-press"
				@tap="applyRefund"
			>申请退款（¥{{ fix(order.payAmount) }}）</view>
			<!-- 退款审核中 / 已退款：仅展示状态，无操作 -->
			<view v-else-if="order.refundStatus === 1" class="op disabled full">退款审核中，请耐心等待</view>
			<view v-else-if="order.refundStatus === 2" class="op disabled full">订单已退款</view>
		</view>
	</view>
</template>

<script>
import { request } from '@/utils/request'

export default {
	data() {
		return {
			orderId: null,
			order: {},
			remainSeconds: 0,
			timer: null,
			refunds: [],     // 退款记录（倒序，供退款进度展示）
			shopPhone: ''    // 商家电话（退款场景展示，方案 3.3）
		}
	},
	computed: {
		/** 退款状态优先展示（方案 3.4）；主状态 3/4 按履约方式区分（T18：外卖区分骑手待取餐/骑手已取餐配送中） */
		statusText() {
			const o = this.order
			if (o.refundStatus === 1) return '退款审核中'
			if (o.refundStatus === 2) return '已退款'
			// 主状态 3：堂食是"待取餐"，外卖是"骑手待取餐"（商家出餐但骑手尚未取餐）
			if (o.status === 3) return o.deliveryType === 2 ? '骑手待取餐' : '待取餐'
			// 主状态 4（仅外卖）：骑手已取餐，配送中
			if (o.status === 4) return '骑手已取餐，配送中'
			const map = { 0: '待支付', 1: '待接单', 2: '制作中', 5: '已完成', 6: '已取消' }
			return map[o.status] || ''
		},
		statusSubText() {
			const o = this.order
			if (o.refundStatus === 1) return '商家审核中，主状态已冻结'
			if (o.refundStatus === 2) return '退款已完成（模拟）'
			if (o.status === 0) return '请尽快完成支付'
			if (o.status === 1) return '等待商家接单'
			if (o.status === 2) return '商家正在制作'
			if (o.status === 3) return o.deliveryType === 1 ? '已出餐，请取餐' : '商家已出餐，骑手待取餐'
			if (o.status === 4) return '骑手已取餐，正在配送'
			if (o.status === 5) return '感谢惠顾，欢迎再来～'
			if (o.status === 6) return '订单已取消'
			return ''
		},
		/** 进度步骤（堂食无配送环节） */
		stepList() {
			return this.order.deliveryType === 1
				? ['下单', '支付', '接单', '出餐', '完成']
				: ['下单', '支付', '接单', '出餐', '配送', '完成']
		},
		/** 当前所处步骤索引（用于进度条高亮） */
		stepIndex() {
			const map = { 0: 0, 1: 2, 2: 3, 3: 4, 4: 4, 5: 99, 6: 99 }
			let idx = map[this.order.status]
			if (this.order.status === 1 && this.order.payStatus === 1) idx = 2
			if (this.order.deliveryType === 1) {
				const dineMap = { 0: 0, 1: 2, 2: 3, 3: 4, 5: 99, 6: 99 }
				idx = dineMap[this.order.status]
			}
			if (this.order.status === 4) idx = 4
			return idx === undefined ? -1 : idx
		},
		addressInfo() {
			try {
				return this.order.addressSnapshot ? JSON.parse(this.order.addressSnapshot) : null
			} catch (e) {
				return null
			}
		},
		addressFull() {
			const a = this.addressInfo
			return a ? [a.province, a.city, a.district, a.detail].filter(Boolean).join('') : ''
		},
		formatRemain() {
			const s = this.remainSeconds
			return Math.floor(s / 60) + ' 分 ' + (s % 60) + ' 秒'
		}
	},
	onLoad(options) {
		this.orderId = options.id
	},
	onShow() {
		this.loadOrder()
		this.loadShopPhone()
	},
	onUnload() {
		this.clearTimer()
	},
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		clearTimer() {
			if (this.timer) {
				clearInterval(this.timer)
				this.timer = null
			}
		},
		async loadOrder() {
			try {
				const body = await request({ url: '/api/order/' + this.orderId })
				this.order = body.data || {}
				this.remainSeconds = this.order.remainSeconds || 0
				this.clearTimer()
				if (this.order.status === 0 && this.remainSeconds > 0) {
					this.timer = setInterval(() => {
						this.remainSeconds--
						if (this.remainSeconds <= 0) {
							this.clearTimer()
							this.loadOrder()
						}
					}, 1000)
				}
				// 退款记录（退款中/有历史时展示进度；驳回后可再次申请）
				if (this.order.refundStatus === 1 || (this.order.status >= 1 && this.order.status <= 3)) {
					this.loadRefunds()
				}
			} catch (e) {
				uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
				setTimeout(() => uni.navigateBack(), 1200)
			}
		},
		/** 退款记录（商家电话也一并展示在退款卡） */
		async loadRefunds() {
			try {
				const body = await request({ url: '/api/refund/order/' + this.orderId, needAuth: true })
				this.refunds = body.data || []
			} catch (e) {
				console.warn('退款记录加载失败:', e.message)
			}
		},
		/** 商家电话（方案 3.3：退款相关页面展示商家电话） */
		async loadShopPhone() {
			try {
				const body = await request({ url: '/api/shop/info', needAuth: false })
				this.shopPhone = (body.data && body.data.phone) || ''
			} catch (e) {
				console.warn('店铺信息加载失败:', e.message)
			}
		},
		callShop() {
			if (this.shopPhone) {
				uni.makePhoneCall({ phoneNumber: this.shopPhone }).catch(() => {})
			}
		},
		/** T20 评价提醒：明细级去评价，跳商品留言页并带入本订单 */
		goComment(item) {
			uni.navigateTo({
				url: '/pages/goods/comments?goodsId=' + item.goodsId
					+ '&name=' + encodeURIComponent(item.goodsName || '')
					+ '&orderId=' + this.order.id
			})
		},
		specText(item) {
			if (!item.specFlavorJson) return ''
			try {
				const obj = JSON.parse(item.specFlavorJson)
				const parts = []
				if (obj.spec && obj.spec.name) parts.push(obj.spec.name)
				if (Array.isArray(obj.flavors)) {
					obj.flavors.forEach(f => {
						if (f.values && f.values.length) parts.push(f.values.join('/'))
					})
				}
				return parts.join(' · ')
			} catch (e) {
				return ''
			}
		},
		goPay() {
			uni.navigateTo({ url: '/pages/pay/cashier?id=' + this.order.id })
		},
		/** 取消订单（仅待支付；后端条件更新幂等） */
		cancelOrder() {
			uni.showModal({
				title: '取消订单',
				content: '确定取消该订单吗？',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/order/cancel/' + this.order.id, method: 'PUT' })
						uni.showToast({ title: '订单已取消', icon: 'none' })
						this.loadOrder()
					} catch (e) {
						uni.showToast({ title: e.message || '取消失败', icon: 'none' })
					}
				}
			})
		},
		/** 申请退款（仅整单；主状态 1-3 且无进行中退款，跳退款申请页） */
		applyRefund() {
			uni.navigateTo({ url: '/pages/order/refund?orderId=' + this.order.id })
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 20rpx 20rpx 60rpx;
	box-sizing: border-box;
}

.status-card {
	background: linear-gradient(135deg, $ap-primary, #ff8f65);
	border-radius: $ap-radius;
	padding: 40rpx 32rpx;
	color: #FFFFFF;
	box-shadow: $ap-shadow-deep;

	.status-big {
		font-size: 44rpx;
		font-weight: 700;
	}

	.status-sub {
		display: block;
		margin-top: 10rpx;
		font-size: 24rpx;
		opacity: 0.9;
	}

	.steps {
		margin-top: 32rpx;
		display: flex;
		justify-content: space-between;

		.step {
			display: flex;
			flex-direction: column;
			align-items: center;
			flex: 1;
			position: relative;

			.dot {
				width: 18rpx;
				height: 18rpx;
				border-radius: 50%;
				background-color: rgba(255, 255, 255, 0.4);
			}

			.step-label {
				margin-top: 10rpx;
				font-size: 20rpx;
				opacity: 0.7;
			}

			&:not(:first-child)::before {
				content: '';
				position: absolute;
				top: 8rpx;
				right: 50%;
				width: 100%;
				height: 3rpx;
				background-color: rgba(255, 255, 255, 0.4);
			}

			&.done .dot {
				background-color: #FFFFFF;
			}

			&.done .step-label {
				opacity: 1;
				font-weight: 700;
			}

			&.done:not(:first-child)::before {
				background-color: #FFFFFF;
			}
		}
	}

	.countdown {
		margin-top: 20rpx;
		font-size: 24rpx;
		font-weight: 700;
	}
}

.info-card,
.items-card,
.remark-card {
	margin-top: 20rpx;
	padding: 24rpx;
}

.info-row {
	display: flex;
	padding: 8rpx 0;

	.label {
		width: 160rpx;
		font-size: 24rpx;
		color: $ap-text-light;
	}

	.value {
		flex: 1;
		font-size: 26rpx;
		color: $ap-text;

		&.strong {
			font-weight: 700;
		}
	}
}

.addr-row {
	display: flex;
	align-items: flex-start;

	.addr-icon {
		font-size: 36rpx;
		margin-right: 16rpx;
	}

	.addr-body {
		flex: 1;

		.addr-line1 {
			.addr-name {
				font-size: 28rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.addr-phone {
				margin-left: 16rpx;
				font-size: 24rpx;
				color: $ap-text-light;
			}
		}

		.addr-detail {
			display: block;
			margin-top: 6rpx;
			font-size: 24rpx;
			color: $ap-text-light;
		}
	}
}

.items-card {
	.item-row {
		display: flex;
		align-items: center;
		padding: 14rpx 0;

		.thumb {
			width: 100rpx;
			height: 100rpx;
			border-radius: 12rpx;
			background-color: $ap-bg;
			flex-shrink: 0;
		}

		.item-info {
			flex: 1;
			min-width: 0;
			margin-left: 16rpx;

			.item-name {
				font-size: 26rpx;
				color: $ap-text;
			}

			.item-spec {
				display: block;
				margin-top: 6rpx;
				font-size: 22rpx;
				color: $ap-text-light;
			}
		}

		.item-right {
			text-align: right;

			.item-qty {
				display: block;
				font-size: 22rpx;
				color: $ap-text-light;
			}

			/* T20 明细级去评价入口 */
			.comment-link {
				display: inline-block;
				margin-top: 6rpx;
				font-size: 22rpx;
				color: #ffffff;
				background-color: $ap-primary;
				border-radius: $ap-radius-pill;
				padding: 4rpx 20rpx;
			}

			.item-subtotal {
				font-size: 26rpx;
				font-weight: 700;
				color: $ap-primary;
			}
		}
	}

	.sum-row {
		display: flex;
		justify-content: space-between;
		padding: 12rpx 0 0;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: $ap-text-light;

		&.total {
			border-top: 1rpx solid $ap-bg;
			margin-top: 16rpx;
			padding-top: 20rpx;
			color: $ap-text;
			font-size: 28rpx;

			.total-price {
				color: $ap-primary;
				font-size: 34rpx;
				font-weight: 700;
			}
		}
	}
}

.remark-card {
	.label {
		font-size: 24rpx;
		color: $ap-text-light;
	}

	.remark {
		display: block;
		margin-top: 8rpx;
		font-size: 26rpx;
		color: $ap-text;
	}
}

/* 退款进度卡 */
.refund-card {
	margin-top: 20rpx;
	padding: 24rpx;

	.card-title-row {
		display: flex;
		align-items: center;

		.card-title {
			font-size: 28rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.shop-phone {
			margin-left: auto;
			font-size: 22rpx;
			color: $ap-primary;
		}
	}

	.refund-item {
		margin-top: 20rpx;
		padding: 20rpx;
		background-color: $ap-bg;
		border-radius: 12rpx;

		.refund-line1 {
			display: flex;
			align-items: center;

			.refund-amount {
				font-size: 30rpx;
				font-weight: 700;
				color: $ap-primary;
			}

			.refund-tag {
				margin-left: auto;
				font-size: 24rpx;
				font-weight: 700;

				&.tag-0 { color: $ap-secondary; }
				&.tag-1 { color: $ap-green; }
				&.tag-2 { color: $ap-text-light; }
			}
		}

		.refund-reason,
		.refund-reject,
		.refund-time {
			display: block;
			margin-top: 8rpx;
			font-size: 22rpx;
			color: $ap-text-light;
		}

		.refund-reject {
			color: $ap-text;
		}
	}
}

.action-bar {
	position: fixed;
	left: 20rpx;
	right: 20rpx;
	bottom: calc(20rpx + env(safe-area-inset-bottom));
	display: flex;
	gap: 20rpx;

	.op {
		flex: 1;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 28rpx;
		font-weight: 700;
		border-radius: $ap-radius-pill;

		&.primary {
			color: #FFFFFF;
			background-color: $ap-primary;
		}

		&.plain {
			color: $ap-text;
			background-color: $ap-card;
			box-shadow: $ap-shadow;
		}

		&.disabled {
			color: $ap-text-light;
			background-color: $ap-bg;
			font-weight: 400;
		}

		&.full {
			flex: 1;
		}
	}
}
</style>
