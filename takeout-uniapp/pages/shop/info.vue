<template>
	<view class="page">
		<!-- 打烊提示条 -->
		<view class="closed-banner" v-if="shop.businessStatus === 0">
			pm 🌙 店铺已打烊，暂时无法接单
		</view>

		<!-- 店铺头卡 -->
		<view class="shop-card ap-card">
			<view class="name-row">
				<text class="shop-name">{{ shop.shopName || '阿婆干饭社' }}</text>
				<text :class="['biz-tag', shop.businessStatus === 1 ? 'open' : 'closed']">
					{{ shop.businessStatus === 1 ? '营业中' : '已打烊' }}
				</text>
			</view>
			<text class="notice" v-if="shop.notice">📣 {{ shop.notice }}</text>
		</view>

		<!-- 信息卡 -->
		<view class="info-card ap-card">
			<view class="info-row" @tap="callShop" v-if="shop.phone">
				<text class="icon">☎️</text>
				<view class="info-body">
					<text class="info-label">商家电话</text>
					<text class="info-value link">{{ shop.phone }}（点击拨打）</text>
				</view>
				<text class="arrow">›</text>
			</view>
			<view class="info-row" v-if="shop.businessHours">
				<text class="icon">🕐</text>
				<view class="info-body">
					<text class="info-label">营业时间</text>
					<text class="info-value">{{ shop.businessHours }}</text>
				</view>
			</view>
			<view class="info-row" v-if="shop.deliveryFee !== undefined">
				<text class="icon">🛵</text>
				<view class="info-body">
					<text class="info-label">配送说明</text>
					<text class="info-value">配送费 ¥{{ fix(shop.deliveryFee) }} · 起送价 ¥{{ fix(shop.minDeliveryAmount) }}</text>
				</view>
			</view>
			<view class="info-row" v-if="shop.address">
				<text class="icon">📍</text>
				<view class="info-body">
					<text class="info-label">店铺位置</text>
					<text class="info-value">{{ shop.address }}</text>
				</view>
			</view>
		</view>

		<!-- 地图（店铺位置） -->
		<view class="map-wrap ap-card" v-if="shop.latitude && shop.longitude">
			<map
				class="map"
				:latitude="Number(shop.latitude)"
				:longitude="Number(shop.longitude)"
				:markers="markers"
				:scale="16"
				show-location
			/>
		</view>

		<view class="foot-tip">— 阿婆干饭社，好好吃饭 —</view>
	</view>
</template>

<script>
import { request } from '@/utils/request'

export default {
	data() {
		return {
			shop: {},
			markers: []
		}
	},
	onShow() {
		this.loadShop()
	},
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		/** 店铺信息（游客可看；缓存一致性由后端保证） */
		async loadShop() {
			try {
				const body = await request({ url: '/api/shop/info', needAuth: false })
				this.shop = body.data || {}
				if (this.shop.latitude && this.shop.longitude) {
					this.markers = [{
						id: 1,
						latitude: Number(this.shop.latitude),
						longitude: Number(this.shop.longitude),
						title: this.shop.shopName || '阿婆干饭社',
						width: 32,
						height: 32
					}]
				}
			} catch (e) {
				uni.showToast({ title: '店铺信息加载失败', icon: 'none' })
			}
		},
		callShop() {
			if (this.shop.phone) {
				uni.makePhoneCall({ phoneNumber: this.shop.phone }).catch(() => {})
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 20rpx 20rpx 40rpx;
	box-sizing: border-box;
}

.closed-banner {
	margin-bottom: 20rpx;
	padding: 20rpx;
	text-align: center;
	font-size: 26rpx;
	font-weight: 700;
	color: $ap-text;
	background-color: $ap-secondary;
	border-radius: $ap-radius;
}

.shop-card {
	padding: 32rpx;

	.name-row {
		display: flex;
		align-items: center;

		.shop-name {
			font-size: 40rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.biz-tag {
			margin-left: auto;
			padding: 6rpx 24rpx;
			font-size: 24rpx;
			border-radius: $ap-radius-pill;

			&.open {
				color: #FFFFFF;
				background-color: $ap-green;
			}

			&.closed {
				color: $ap-text-light;
				background-color: $ap-bg;
			}
		}
	}

	.notice {
		display: block;
		margin-top: 16rpx;
		font-size: 26rpx;
		color: $ap-text-light;
	}
}

.info-card {
	margin-top: 20rpx;
	padding: 12rpx 24rpx;

	.info-row {
		display: flex;
		align-items: center;
		padding: 20rpx 0;

		&:not(:last-child) {
			border-bottom: 1rpx solid $ap-bg;
		}

		.icon {
			font-size: 32rpx;
			margin-right: 16rpx;
		}

		.info-body {
			flex: 1;
			display: flex;
			flex-direction: column;

			.info-label {
				font-size: 22rpx;
				color: $ap-text-light;
			}

			.info-value {
				margin-top: 4rpx;
				font-size: 26rpx;
				color: $ap-text;
				word-break: break-all;

				&.link {
					color: $ap-primary;
				}
			}
		}

		.arrow {
			font-size: 34rpx;
			color: $ap-text-light;
		}
	}
}

.map-wrap {
	margin-top: 20rpx;
	padding: 12rpx;

	.map {
		width: 100%;
		height: 360rpx;
		border-radius: 12rpx;
	}
}

.foot-tip {
	margin-top: 40rpx;
	text-align: center;
	font-size: 22rpx;
	color: $ap-text-light;
}
</style>
