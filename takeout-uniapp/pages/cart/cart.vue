<template>
	<view class="page">
		<view v-if="!items.length" class="ap-empty">
			<text class="em">🛒</text>
			购物车空空的，先去挑点好吃的吧～
			<view class="go-menu ap-press" @tap="goMenu">去点餐</view>
		</view>

		<template v-else>
			<view class="cart-list">
				<view
					v-for="item in items"
					:key="item.id"
					:class="['cart-item ap-card', item.status !== 'normal' ? 'invalid' : '']"
				>
					<image class="thumb" :src="item.image || '/static/images/placeholder.png'" mode="aspectFill" />
					<view class="info">
						<view class="name-row">
							<text class="name">{{ item.goodsName }}</text>
							<text v-if="item.status === 'soldout'" class="invalid-tag">已售罄</text>
							<text v-if="item.status === 'offshelf'" class="invalid-tag">已下架</text>
						</view>
						<text class="spec" v-if="item.specName || (item.flavors && item.flavors.length)">
							{{ specText(item) }}
						</text>
						<view class="bottom">
							<view class="price-row">
								<text class="symbol">¥</text>
								<text class="price">{{ item.price }}</text>
							</view>
							<!-- 失效项不可改数量，提示移除 -->
							<view v-if="item.status !== 'normal'" class="remove-btn ap-press" @tap="removeItem(item)">移除</view>
							<view v-else class="stepper">
								<view class="step-btn ap-press" @tap="changeQty(item, -1)">－</view>
								<text class="qty">{{ item.quantity }}</text>
								<view class="step-btn ap-press" @tap="changeQty(item, 1)">＋</view>
							</view>
						</view>
					</view>
				</view>
			</view>

			<!-- 清空 -->
			<view class="clear-row" @tap="clearCart">
				🗑️ 清空购物车
			</view>

			<!-- 底部结算栏 -->
			<cart-bar
				:count="validCount"
				:amount="validAmount"
				:disabled="hasInvalid"
				:tip="hasInvalid ? '含失效菜品，请先移除' : ''"
				button-text="去结算"
				@checkout="goCheckout"
			/>
		</template>
	</view>
</template>

<script>
import { request } from '@/utils/request'

export default {
	data() {
		return {
			items: [] // /api/cart/list 返回的明细（含有效性标记）
		}
	},
	computed: {
		/** 有效项数量 */
		validCount() {
			return this.items.filter(i => i.status === 'normal').reduce((sum, i) => sum + (i.quantity || 0), 0)
		},
		/** 有效项合计金额 */
		validAmount() {
			return this.items.filter(i => i.status === 'normal').reduce((sum, i) => sum + Number(i.subtotal || 0), 0)
		},
		hasInvalid() {
			return this.items.some(i => i.status !== 'normal')
		}
	},
	onShow() {
		this.loadCart()
	},
	methods: {
		/** 加载购物车 */
		async loadCart() {
			try {
				const body = await request({ url: '/api/cart/list' })
				this.items = body.data || []
			} catch (e) {
				uni.showToast({ title: e.message || '购物车加载失败', icon: 'none' })
			}
		},
		specText(item) {
			const parts = []
			if (item.specName) parts.push(item.specName)
			if (item.flavors && item.flavors.length) {
				item.flavors.forEach(f => {
					if (f.values && f.values.length) parts.push(f.values.join('/'))
				})
			}
			return parts.join(' · ')
		},
		/** 增减数量（减到 0 视为移除） */
		async changeQty(item, delta) {
			const next = item.quantity + delta
			try {
				if (next <= 0) {
					const res = await new Promise(resolve => {
						uni.showModal({
							title: '提示',
							content: `将移除"${item.goodsName}"，确定吗？`,
							success: r => resolve(r.confirm)
						})
					})
					if (!res) return
					await request({ url: '/api/cart/' + item.id, method: 'DELETE' })
				} else {
					await request({ url: '/api/cart/quantity', method: 'PUT', data: { id: item.id, quantity: next } })
				}
				this.loadCart()
			} catch (e) {
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
			}
		},
		async removeItem(item) {
			try {
				await request({ url: '/api/cart/' + item.id, method: 'DELETE' })
				this.loadCart()
			} catch (e) {
				uni.showToast({ title: e.message || '删除失败', icon: 'none' })
			}
		},
		async clearCart() {
			uni.showModal({
				title: '提示',
				content: '确定清空购物车吗？',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/cart/clear', method: 'DELETE' })
						this.loadCart()
					} catch (e) {
						uni.showToast({ title: e.message || '清空失败', icon: 'none' })
					}
				}
			})
		},
		goCheckout() {
			if (this.hasInvalid) return
			if (!this.validCount) {
				uni.showToast({ title: '先挑点好吃的吧～', icon: 'none' })
				return
			}
			uni.navigateTo({ url: '/pages/checkout/checkout' })
		},
		goMenu() {
			uni.navigateBack()
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 20rpx 20rpx 180rpx;
	box-sizing: border-box;
}

.cart-list {
	.cart-item {
		display: flex;
		padding: 20rpx;
		margin-bottom: 20rpx;

		.thumb {
			width: 140rpx;
			height: 140rpx;
			border-radius: 16rpx;
			flex-shrink: 0;
			background-color: $ap-bg;
		}

		.info {
			flex: 1;
			min-width: 0;
			margin-left: 20rpx;
			display: flex;
			flex-direction: column;

			.name-row {
				display: flex;
				align-items: center;

				.name {
					font-size: 28rpx;
					font-weight: 700;
					color: $ap-text;
				}

				.invalid-tag {
					margin-left: 12rpx;
					padding: 2rpx 14rpx;
					font-size: 20rpx;
					color: #FFFFFF;
					background-color: $ap-text-light;
					border-radius: $ap-radius-pill;
				}
			}

			.spec {
				margin-top: 6rpx;
				font-size: 22rpx;
				color: $ap-text-light;
				overflow: hidden;
				text-overflow: ellipsis;
				white-space: nowrap;
			}

			.bottom {
				margin-top: auto;
				display: flex;
				align-items: center;
				justify-content: space-between;

				.price-row {
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

				.remove-btn {
					padding: 8rpx 24rpx;
					font-size: 24rpx;
					color: $ap-text-light;
					background-color: $ap-bg;
					border-radius: $ap-radius-pill;
				}

				.stepper {
					display: flex;
					align-items: center;

					.step-btn {
						width: 52rpx;
						height: 52rpx;
						line-height: 48rpx;
						text-align: center;
						font-size: 32rpx;
						color: $ap-primary;
						background-color: $ap-bg;
						border-radius: 50%;
					}

					.qty {
						min-width: 64rpx;
						text-align: center;
						font-size: 28rpx;
						font-weight: 700;
						color: $ap-text;
					}
				}
			}
		}

		&.invalid {
			opacity: 0.55;

			.thumb {
				filter: grayscale(0.8);
			}
		}
	}
}

.clear-row {
	text-align: center;
	padding: 24rpx;
	font-size: 24rpx;
	color: $ap-text-light;
}
</style>
