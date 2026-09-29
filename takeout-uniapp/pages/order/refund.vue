<template>
	<view class="page" v-if="order.id">
		<!-- 退款金额卡（仅整单退款：金额=订单实付，含配送费快照） -->
		<view class="amount-card ap-card">
			<view class="amount-left">
				<text class="amount-label">退款金额（整单）</text>
				<view class="amount-row">
					<text class="symbol">¥</text>
					<text class="amount">{{ fix(order.payAmount) }}</text>
				</view>
				<text class="amount-sub">原路退回（模拟）· 含配送费 ¥{{ fix(order.deliveryFee) }}</text>
			</view>
			<view class="contact" @tap="callShop" v-if="shopPhone">
				<text class="contact-icon">☎️</text>
				<text class="contact-text">联系商家</text>
			</view>
		</view>
		<view class="contact-tip" v-if="shopPhone">有疑问可先联系商家协商：{{ shopPhone }}</view>

		<!-- 退款原因 -->
		<view class="reason-card ap-card">
			<view class="card-title-row">
				<text class="card-title">退款原因</text>
				<text class="count">{{ reason.length }}/200</text>
			</view>
			<textarea
				class="reason-input"
				v-model="reason"
				placeholder="请填写退款原因（必填）"
				maxlength="200"
			/>
		</view>

		<!-- 图片凭证（复用统一上传模块，最多5张） -->
		<view class="images-card ap-card">
			<view class="card-title-row">
				<text class="card-title">图片凭证</text>
				<text class="count">{{ images.length }}/5</text>
			</view>
			<view class="image-grid">
				<view v-for="(img, i) in images" :key="i" class="image-item">
					<image class="preview" :src="BASE_URL + img" mode="aspectFill" @tap="previewImage(i)" />
					<view class="remove" @tap="images.splice(i, 1)">✕</view>
				</view>
				<view v-if="images.length < 5" class="add-image ap-press" @tap="addImages">
					<text class="add-icon">📷</text>
					<text class="add-text">上传</text>
				</view>
			</view>
			<text class="image-tip">支持 jpg/jpeg/png/webp，单张不超过 5MB</text>
		</view>

		<!-- 提交 -->
		<view class="submit-area">
			<view :class="['submit-btn ap-press', canSubmit ? '' : 'disabled']" @tap="submit">
				{{ submitting ? '提交中...' : '提交申请' }}
			</view>
			<text class="submit-tip">提交后商家将尽快审核（24 小时未审核将自动驳回）</text>
		</view>
	</view>
</template>

<script>
import { request, BASE_URL } from '@/utils/request'
import { chooseAndUploadImages } from '@/utils/upload'

export default {
	data() {
		return {
			orderId: null,
			order: {},
			shopPhone: '',
			reason: '',
			images: [], // 相对路径数组（入库值）
			submitting: false
		}
	},
	computed: {
		canSubmit() {
			return this.reason.trim().length > 0 && !this.submitting
		}
	},
	onLoad(options) {
		this.orderId = options.orderId
	},
	onShow() {
		this.loadOrder()
		this.loadShopPhone()
	},
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		/** 订单信息（金额展示；归属校验在后端） */
		async loadOrder() {
			try {
				const body = await request({ url: '/api/order/' + this.orderId })
				this.order = body.data || {}
			} catch (e) {
				uni.showToast({ title: e.message || '订单加载失败', icon: 'none' })
				setTimeout(() => uni.navigateBack(), 1200)
			}
		},
		/** 商家电话（退款页面展示，方案 3.3 双方电话可沟通） */
		async loadShopPhone() {
			try {
				const body = await request({ url: '/api/shop/info', needAuth: false })
				this.shopPhone = (body.data && body.data.phone) || ''
			} catch (e) {
				console.warn('店铺信息加载失败:', e.message)
			}
		},
		/** 选择并上传图片（追加） */
		async addImages() {
			try {
				await this.doChoose()
			} catch (e) {
				if (e && e.message && e.message !== '取消选择') {
					uni.showToast({ title: e.message, icon: 'none' })
				}
			}
		},
		doChoose() {
			return chooseAndUploadImages(5 - this.images.length, (paths) => {
				this.images = paths
			})
		},
		previewImage(index) {
			const urls = this.images.map(p => BASE_URL + p)
			uni.previewImage({ urls, current: urls[index] })
		},
		/** 提交申请（防重锁/状态链校验由后端兜底） */
		async submit() {
			if (!this.canSubmit) {
				uni.showToast({ title: '请填写退款原因', icon: 'none' })
				return
			}
			this.submitting = true
			try {
				await request({
					url: '/api/refund/apply',
					method: 'POST',
					data: {
						orderId: Number(this.orderId),
						reason: this.reason.trim(),
						evidenceImages: this.images
					}
				})
				uni.showToast({ title: '退款申请已提交', icon: 'success' })
				setTimeout(() => {
					const pages = getCurrentPages()
					// 返回订单详情页（其 onShow 会刷新退款状态）
					uni.navigateBack()
				}, 600)
			} catch (e) {
				uni.showToast({ title: e.message || '提交失败', icon: 'none' })
			} finally {
				this.submitting = false
			}
		},
		callShop() {
			if (this.shopPhone) {
				uni.makePhoneCall({ phoneNumber: this.shopPhone }).catch(() => {})
			}
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

.amount-card {
	padding: 32rpx;
	display: flex;
	align-items: center;

	.amount-left {
		flex: 1;

		.amount-label {
			font-size: 24rpx;
			color: $ap-text-light;
		}

		.amount-row {
			margin-top: 8rpx;

			.symbol {
				font-size: 28rpx;
				color: $ap-primary;
				font-weight: 700;
			}

			.amount {
				font-size: 56rpx;
				color: $ap-primary;
				font-weight: 700;
			}
		}

		.amount-sub {
			display: block;
			margin-top: 8rpx;
			font-size: 22rpx;
			color: $ap-text-light;
		}
	}

	.contact {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 12rpx 20rpx;
		background-color: $ap-bg;
		border-radius: $ap-radius;

		.contact-icon {
			font-size: 36rpx;
		}

		.contact-text {
			margin-top: 4rpx;
			font-size: 20rpx;
			color: $ap-text;
		}
	}
}

.contact-tip {
	margin-top: 14rpx;
	padding: 0 8rpx;
	font-size: 22rpx;
	color: $ap-text-light;
}

.reason-card,
.images-card {
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

		.count {
			margin-left: auto;
			font-size: 22rpx;
			color: $ap-text-light;
		}
	}
}

.reason-input {
	width: 100%;
	height: 160rpx;
	margin-top: 16rpx;
	padding: 16rpx;
	box-sizing: border-box;
	font-size: 26rpx;
	color: $ap-text;
	background-color: $ap-bg;
	border-radius: 12rpx;
}

.image-grid {
	margin-top: 16rpx;
	display: flex;
	flex-wrap: wrap;

	.image-item {
		position: relative;
		margin: 0 16rpx 16rpx 0;

		.preview {
			width: 140rpx;
			height: 140rpx;
			border-radius: 12rpx;
			background-color: $ap-bg;
		}

		.remove {
			position: absolute;
			top: -12rpx;
			right: -12rpx;
			width: 36rpx;
			height: 36rpx;
			line-height: 32rpx;
			text-align: center;
			font-size: 20rpx;
			color: #FFFFFF;
			background-color: $ap-text-light;
			border-radius: 50%;
		}
	}

	.add-image {
		width: 140rpx;
		height: 140rpx;
		background-color: $ap-bg;
		border-radius: 12rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;

		.add-icon {
			font-size: 44rpx;
		}

		.add-text {
			font-size: 20rpx;
			color: $ap-text-light;
		}
	}
}

.image-tip {
	display: block;
	margin-top: 8rpx;
	font-size: 20rpx;
	color: $ap-text-light;
}

.submit-area {
	margin-top: 40rpx;

	.submit-btn {
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

	.submit-tip {
		display: block;
		margin-top: 16rpx;
		text-align: center;
		font-size: 22rpx;
		color: $ap-text-light;
	}
}
</style>
