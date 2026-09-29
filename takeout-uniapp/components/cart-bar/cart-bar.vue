<template>
	<view class="cart-bar">
		<view class="cart-entry ap-press" @tap="$emit('goCart')">
			<view class="cart-icon">🛒</view>
			<!-- 角标：数量变化时弹跳（微交互） -->
			<view v-if="count > 0" :class="['badge', bounce ? 'bounce' : '']">{{ count > 99 ? '99+' : count }}</view>
		</view>
		<view class="amount-area">
			<view class="amount-row">
				<text class="symbol">¥</text>
				<text class="amount">{{ amountText }}</text>
			</view>
			<text class="amount-tip" v-if="tip">{{ tip }}</text>
		</view>
		<view :class="['checkout-btn ap-press', disabled ? 'disabled' : '']" @tap="onCheckout">
			{{ buttonText }}
		</view>
	</view>
</template>

<script>
export default {
	name: 'cart-bar',
	props: {
		// 购物车商品总数量
		count: {
			type: Number,
			default: 0
		},
		// 有效商品合计金额
		amount: {
			type: [Number, String],
			default: 0
		},
		// 按钮文案
		buttonText: {
			type: String,
			default: '去结算'
		},
		// 是否禁用结算
		disabled: {
			type: Boolean,
			default: false
		},
		// 金额旁提示（如"未起送"）
		tip: {
			type: String,
			default: ''
		}
	},
	data() {
		return {
			bounce: false
		}
	},
	computed: {
		amountText() {
			return Number(this.amount || 0).toFixed(2)
		}
	},
	watch: {
		// 数量变化触发角标弹跳动画
		count(newVal, oldVal) {
			if (newVal !== oldVal && newVal > 0) {
				this.bounce = false
				this.$nextTick(() => {
					this.bounce = true
					setTimeout(() => {
						this.bounce = false
					}, 500)
				})
			}
		}
	},
	methods: {
		onCheckout() {
			if (this.disabled) return
			this.$emit('checkout')
		}
	}
}
</script>

<style lang="scss" scoped>
.cart-bar {
	position: fixed;
	left: 20rpx;
	right: 20rpx;
	bottom: calc(20rpx + env(safe-area-inset-bottom));
	height: 100rpx;
	background-color: $ap-text;
	border-radius: $ap-radius-pill;
	display: flex;
	align-items: center;
	box-shadow: $ap-shadow-deep;
	z-index: 90;

	.cart-entry {
		position: relative;
		width: 96rpx;
		height: 96rpx;
		margin: -30rpx 0 0 16rpx;
		background-color: $ap-text;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;

		.cart-icon {
			font-size: 44rpx;
		}

		.badge {
			position: absolute;
			top: -6rpx;
			right: -6rpx;
			min-width: 36rpx;
			height: 36rpx;
			line-height: 36rpx;
			padding: 0 8rpx;
			text-align: center;
			font-size: 20rpx;
			color: $ap-text;
			font-weight: 700;
			background-color: $ap-secondary;
			border-radius: $ap-radius-pill;
		}

		.bounce {
			animation: badge-bounce 0.5s ease;
		}
	}

	.amount-area {
		margin-left: 20rpx;

		.amount-row {
			.symbol {
				font-size: 24rpx;
				color: #FFFFFF;
				font-weight: 700;
			}

			.amount {
				font-size: 36rpx;
				color: #FFFFFF;
				font-weight: 700;
			}
		}

		.amount-tip {
			font-size: 20rpx;
			color: $ap-secondary;
		}
	}

	.checkout-btn {
		margin-left: auto;
		margin-right: 10rpx;
		height: 80rpx;
		line-height: 80rpx;
		padding: 0 44rpx;
		font-size: 28rpx;
		font-weight: 700;
		color: #FFFFFF;
		background-color: $ap-primary;
		border-radius: $ap-radius-pill;

		&.disabled {
			background-color: $ap-text-light;
			font-weight: 400;
		}
	}
}

@keyframes badge-bounce {
	0% {
		transform: scale(1);
	}
	40% {
		transform: scale(1.5);
	}
	70% {
		transform: scale(0.9);
	}
	100% {
		transform: scale(1);
	}
}
</style>
