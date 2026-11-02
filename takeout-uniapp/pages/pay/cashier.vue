<template>
	<view class="page" v-if="order.id">
		<!-- 金额卡 -->
		<view class="amount-card ap-card">
			<text class="pay-label">应付金额</text>
			<view class="amount-row">
				<text class="symbol">¥</text>
				<text class="amount">{{ fix(order.payAmount) }}</text>
			</view>
			<view class="countdown" v-if="order.status === 0 && remainSeconds > 0">
				⏰ {{ formatRemain }} 后未支付将自动关闭
			</view>
			<view class="countdown closed" v-if="order.status === 6">
				订单已超时关闭，请返回重新下单
			</view>
		</view>

		<!-- 订单信息 -->
		<view class="info-card ap-card">
			<view class="info-row">
				<text class="label">订单号</text>
				<text class="value">{{ order.orderNo }}</text>
			</view>
			<view class="info-row">
				<text class="label">{{ order.deliveryType === 1 ? '堂食桌号' : '外卖' }}</text>
				<text class="value">{{ order.deliveryType === 1 ? (order.tableNo + ' 号桌') : '配送上门' }}</text>
			</view>
			<view class="info-row" v-if="order.deliveryType === 2 && addressText">
				<text class="label">收货地址</text>
				<text class="value addr">{{ addressText }}</text>
			</view>
			<view class="info-row">
				<text class="label">菜品</text>
				<text class="value">{{ itemSummary }}</text>
			</view>
			<view class="info-row" v-if="order.remark">
				<text class="label">备注</text>
				<text class="value">{{ order.remark }}</text>
			</view>
		</view>

		<!-- 支付方式（三期 T26：渠道与文案由 /api/pay/config 下发） -->
		<view class="pay-card ap-card">
			<view class="pay-row">
				<text class="pay-icon">💰</text>
				<view class="pay-info">
					<text class="pay-name">{{ payConfig.label }}</text>
					<text class="pay-tip">{{ payConfig.channel === 'wechat' ? '在微信收银台完成付款' : '模拟支付，仅用于演示，不产生真实扣款' }}</text>
				</view>
				<text class="pay-check">✔️</text>
			</view>
		</view>

		<!-- 确认支付 -->
		<view class="pay-btn-area">
			<view
				:class="['pay-btn ap-press', canPay ? '' : 'disabled']"
				@tap="confirmPay"
			>{{ payButtonText }}</view>
			<!-- 待支付时可取消订单（T6；后端条件更新幂等） -->
			<view class="cancel-btn ap-press" v-if="order.status === 0" @tap="cancelOrder">取消订单</view>
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
			paying: false,
			payConfig: { channel: 'mock', label: '微信支付（模拟）' }
		}
	},
	computed: {
		addressText() {
			try {
				if (!this.order.addressSnapshot) return ''
				const a = JSON.parse(this.order.addressSnapshot)
				return [a.province, a.city, a.district, a.detail].filter(Boolean).join('') + ' ' + (a.name || '')
			} catch (e) {
				return ''
			}
		},
		itemSummary() {
			const items = this.order.items || []
			return items.map(i => i.goodsName + 'x' + i.quantity).join('、')
		},
		formatRemain() {
			const s = this.remainSeconds
			const m = Math.floor(s / 60)
			return m + ' 分 ' + (s % 60) + ' 秒'
		},
		canPay() {
			return this.order.status === 0 && this.remainSeconds > 0 && !this.paying
		},
		payButtonText() {
			if (this.paying) return '支付中...'
			if (this.order.status === 1) return '订单已支付'
			if (this.order.status === 6) return '订单已关闭'
			return '确认支付 ¥' + this.fix(this.order.payAmount)
		}
	},
	onLoad(options) {
		this.orderId = options.id
	},
	onShow() {
		this.loadOrder()
		this.loadPayConfig()
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
		/** 加载订单（归属校验在后端） */
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
							this.loadOrder() // 到期刷新（订单可能已被关闭）
						}
					}, 1000)
				}
			} catch (e) {
				uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
				setTimeout(() => uni.navigateBack(), 1200)
			}
		},
		/** 加载支付渠道配置（三期 T26；失败保持默认模拟文案，不影响支付） */
		async loadPayConfig() {
			try {
				const body = await request({ url: '/api/pay/config' })
				if (body && body.channel) {
					this.payConfig = { channel: body.channel, label: body.label }
				}
			} catch (e) {
				// 配置读取失败按模拟渠道展示（文案不影响实际渠道，渠道以 /submit 返回为准）
			}
		},
		/** 确认支付（按渠道给提示文案） */
		confirmPay() {
			if (!this.canPay) {
				if (this.order.status === 0 && this.remainSeconds <= 0) {
					this.loadOrder()
				}
				return
			}
			uni.showModal({
				title: '是否确认支付？',
				content: this.payConfig.channel === 'wechat'
					? '将通过微信支付付款 ¥' + this.fix(this.order.payAmount)
					: '模拟支付，仅用于演示，不产生真实扣款',
				confirmText: '确认支付',
				success: (res) => {
					if (res.confirm) {
						this.doPay()
					}
				}
			})
		},
		/** 发起支付（三期 T26 统一走 /api/pay/submit：模拟渠道直接成功；微信渠道调起收银台后轮询结果） */
		async doPay() {
			this.paying = true
			try {
				const body = await request({
					url: '/api/pay/submit',
					method: 'POST',
					data: { orderId: this.order.id }
				})
				if (body && body.channel === 'wechat' && body.payParams) {
					// 微信渠道：调起收银台，成功后轮询订单状态（后端以回调/查单落账为准）再跳结果页
					await this.requestWxPayment(body.payParams)
					await this.pollPaidAndGo()
				} else {
					uni.redirectTo({ url: '/pages/pay/result?orderId=' + this.order.id })
				}
			} catch (e) {
				uni.showToast({ title: e.message || '支付失败', icon: 'none' })
				this.loadOrder()
			} finally {
				this.paying = false
			}
		},
		/** 调起微信收银台（payParams 为后端 SDK 签名的六元组；用户取消/失败抛错走统一提示） */
		requestWxPayment(payParams) {
			return new Promise((resolve, reject) => {
				uni.requestPayment({
					provider: 'wxpay',
					timeStamp: payParams.timeStamp,
					nonceStr: payParams.nonceStr,
					package: payParams.package,
					signType: payParams.signType,
					paySign: payParams.paySign,
					success: resolve,
					fail: (err) => reject(new Error((err && err.errMsg && err.errMsg.indexOf('cancel') >= 0) ? '支付已取消' : '支付未完成'))
				})
			})
		},
		/** 轮询订单状态（5 次 x 1s）等后端落账后跳结果页；超时也跳（结果页可看实时状态） */
		async pollPaidAndGo() {
			for (let i = 0; i < 5; i++) {
				try {
					const body = await request({ url: '/api/order/' + this.orderId })
					if (body.data && body.data.status === 1) {
						break
					}
				} catch (e) {
					// 轮询单次失败继续重试
				}
				await new Promise((resolve) => setTimeout(resolve, 1000))
			}
			uni.redirectTo({ url: '/pages/pay/result?orderId=' + this.order.id })
		},
		/** 取消订单（仅待支付；后端条件更新幂等） */
		cancelOrder() {
			uni.showModal({
				title: '取消订单',
				content: '确定取消该订单吗？取消后需重新下单',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/order/cancel/' + this.order.id, method: 'PUT' })
						uni.showToast({ title: '订单已取消', icon: 'none' })
						setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 800)
					} catch (e) {
						uni.showToast({ title: e.message || '取消失败', icon: 'none' })
					}
				}
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 20rpx;
	box-sizing: border-box;
}

.amount-card {
	padding: 40rpx;
	text-align: center;

	.pay-label {
		font-size: 24rpx;
		color: $ap-text-light;
	}

	.amount-row {
		margin-top: 12rpx;

		.symbol {
			font-size: 34rpx;
			color: $ap-primary;
			font-weight: 700;
		}

		.amount {
			font-size: 72rpx;
			color: $ap-primary;
			font-weight: 700;
		}
	}

	.countdown {
		margin-top: 12rpx;
		font-size: 22rpx;
		color: $ap-primary;

		&.closed {
			color: $ap-text-light;
		}
	}
}

.info-card,
.pay-card {
	margin-top: 20rpx;
	padding: 24rpx;

	.info-row,
	.pay-row {
		display: flex;
		padding: 12rpx 0;
		align-items: flex-start;

		.label {
			width: 150rpx;
			font-size: 24rpx;
			color: $ap-text-light;
			flex-shrink: 0;
		}

		.value {
			flex: 1;
			font-size: 26rpx;
			color: $ap-text;
			word-break: break-all;
		}
	}
}

.pay-card {
	.pay-row {
		.pay-icon {
			font-size: 40rpx;
			margin-right: 16rpx;
		}

		.pay-info {
			flex: 1;

			.pay-name {
				display: block;
				font-size: 28rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.pay-tip {
				font-size: 20rpx;
				color: $ap-text-light;
			}
		}

		.pay-check {
			font-size: 32rpx;
			color: $ap-primary;
		}
	}
}

.pay-btn-area {
	margin-top: 40rpx;

	.pay-btn {
		height: 92rpx;
		line-height: 92rpx;
		text-align: center;
		font-size: 32rpx;
		font-weight: 700;
		color: #FFFFFF;
		background-color: $ap-primary;
		border-radius: $ap-radius-pill;

		&.disabled {
			background-color: $ap-text-light;
			font-weight: 400;
		}
	}

	.cancel-btn {
		margin-top: 20rpx;
		height: 80rpx;
		line-height: 80rpx;
		text-align: center;
		font-size: 26rpx;
		color: $ap-text-light;
		background-color: $ap-card;
		border-radius: $ap-radius-pill;
	}
}
</style>
