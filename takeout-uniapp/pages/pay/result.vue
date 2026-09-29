<template>
	<view class="page">
		<!-- 庆祝动效（纯 CSS：✅ 缩放弹入 + 彩点飘落） -->
		<view class="celebrate">
			<view class="check-circle">✅</view>
			<view v-for="(c, i) in confetti" :key="i" class="confetti" :style="c.style"></view>
		</view>
		<text class="title">支付成功！</text>
		<text class="sub">阿婆这就开始准备你的餐啦 🍚</text>

		<view class="result-card ap-card" v-if="order.id">
			<view class="row">
				<text class="label">支付金额</text>
				<text class="value price">¥{{ fix(order.payAmount) }}</text>
			</view>
			<view class="row">
				<text class="label">订单号</text>
				<text class="value">{{ order.orderNo }}</text>
			</view>
			<view class="row">
				<text class="label">订单状态</text>
				<text class="value">{{ order.deliveryType === 1 ? '待商家接单' : '待商家接单（将尽快配送）' }}</text>
			</view>
			<view class="row" v-if="order.deliveryType === 1">
				<text class="label">堂食桌号</text>
				<text class="value">{{ order.tableNo }} 号桌</text>
			</view>
		</view>

		<view class="btn-area">
			<view class="btn primary ap-press" @tap="goHome">返回首页</view>
			<view class="btn plain ap-press" @tap="goOrders">查看订单</view>
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
			confetti: []
		}
	},
	onLoad(options) {
		this.orderId = options.orderId
	},
	onShow() {
		this.loadOrder()
		this.makeConfetti()
	},
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		async loadOrder() {
			try {
				const body = await request({ url: '/api/order/' + this.orderId })
				this.order = body.data || {}
			} catch (e) {
				console.warn('订单信息加载失败:', e.message)
			}
		},
		/** 庆祝彩点（随机颜色/位置/延迟，纯 CSS 动画） */
		makeConfetti() {
			const colors = ['#FF7043', '#FFC53D', '#7FB77E', '#FFB6A3', '#FFE3C7']
			const list = []
			for (let i = 0; i < 14; i++) {
				list.push({
					style: `left:${Math.random() * 100}%;
					        background-color:${colors[i % colors.length]};
					        animation-delay:${(Math.random() * 0.8).toFixed(2)}s;
					        animation-duration:${(1.2 + Math.random()).toFixed(2)}s;`
				})
			}
			this.confetti = list
		},
		goHome() {
			uni.reLaunch({ url: '/pages/index/index' })
		},
		goOrders() {
			uni.redirectTo({ url: '/pages/order/detail?id=' + this.order.id })
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 80rpx 32rpx;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
	align-items: center;
	position: relative;
	overflow: hidden;
}

.celebrate {
	position: relative;
	width: 160rpx;
	height: 160rpx;

	.check-circle {
		width: 160rpx;
		height: 160rpx;
		line-height: 160rpx;
		text-align: center;
		font-size: 88rpx;
		background-color: $ap-card;
		border-radius: 50%;
		box-shadow: $ap-shadow-deep;
		animation: pop-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
	}
}

@keyframes pop-in {
	0% {
		transform: scale(0);
	}
	70% {
		transform: scale(1.15);
	}
	100% {
		transform: scale(1);
	}
}

.confetti {
	position: absolute;
	top: -40rpx;
	width: 14rpx;
	height: 22rpx;
	border-radius: 4rpx;
	opacity: 0;
	animation: confetti-fall linear forwards;
}

@keyframes confetti-fall {
	0% {
		opacity: 1;
		transform: translateY(0) rotate(0deg);
	}
	100% {
		opacity: 0;
		transform: translateY(500rpx) rotate(540deg);
	}
}

.title {
	margin-top: 32rpx;
	font-size: 40rpx;
	font-weight: 700;
	color: $ap-text;
}

.sub {
	margin-top: 12rpx;
	font-size: 26rpx;
	color: $ap-text-light;
}

.result-card {
	width: 100%;
	margin-top: 48rpx;
	padding: 28rpx;

	.row {
		display: flex;
		padding: 12rpx 0;

		.label {
			width: 160rpx;
			font-size: 24rpx;
			color: $ap-text-light;
			flex-shrink: 0;
		}

		.value {
			flex: 1;
			font-size: 26rpx;
			color: $ap-text;
			word-break: break-all;

			&.price {
				color: $ap-primary;
				font-size: 32rpx;
				font-weight: 700;
			}
		}
	}
}

.btn-area {
	width: 100%;
	margin-top: 56rpx;

	.btn {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 30rpx;
		font-weight: 700;
		border-radius: $ap-radius-pill;
		margin-bottom: 20rpx;

		&.primary {
			color: #FFFFFF;
			background-color: $ap-primary;
		}

		&.plain {
			color: $ap-primary;
			background-color: $ap-card;
			font-weight: 400;
		}
	}
}
</style>
