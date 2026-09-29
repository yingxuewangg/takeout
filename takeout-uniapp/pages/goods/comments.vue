<template>
	<view class="page">
		<!-- 统计头 -->
		<view class="stats ap-card">
			<view class="score-block">
				<text class="avg">{{ stats.avgScore || 0 }}</text>
				<text class="avg-label">平均分</text>
			</view>
			<view class="stats-right">
				<text class="count">{{ stats.count || 0 }} 条留言</text>
				<text class="tip">仅购买过的用户可留言</text>
			</view>
			<view class="write-btn ap-press" @tap="openWrite">✍️ 写留言</view>
		</view>

		<!-- 留言列表 -->
		<view v-for="c in comments" :key="c.id" class="comment-card ap-card">
			<view class="head">
				<image class="avatar" :src="c.avatar || '/static/images/placeholder.png'" mode="aspectFill" />
				<view class="head-info">
					<text class="nickname">{{ c.nickname }}</text>
					<view class="sub-row">
						<text class="stars">{{ '★'.repeat(c.score) + '☆'.repeat(5 - c.score) }}</text>
						<text v-if="c.hasOrder" class="bought-tag">已购</text>
					</view>
				</view>
				<text class="time">{{ (c.createTime || '').slice(0, 10) }}</text>
			</view>
			<text class="content">{{ c.content }}</text>
			<view class="images" v-if="c.images && c.images.length">
				<image
					v-for="(img, i) in c.images"
					:key="i"
					class="img"
					:src="img"
					mode="aspectFill"
					@tap="preview(c.images, i)"
				/>
			</view>
			<view class="reply-box" v-if="c.reply">
				<text class="reply-label">商家回复</text>
				<text class="reply-text">{{ c.reply }}</text>
			</view>
		</view>

		<view v-if="!comments.length && !loading" class="ap-empty">
			<text class="em">💬</text>
			还没有留言，快来抢沙发～
		</view>
		<view v-if="loading" class="loading-tip">加载中...</view>
		<view v-if="finished && comments.length" class="loading-tip">— 没有更多了 —</view>

		<!-- 发表留言弹窗 -->
		<wd-popup v-model="writeOpen" position="bottom" :safe-area-inset-bottom="true">
			<view class="write-panel">
				<view class="panel-head">
					<text class="panel-title">写留言</text>
					<text class="close" @tap="writeOpen = false">✕</text>
				</view>
				<scroll-view scroll-y class="panel-body">
					<!-- 关联订单（已购资格：必选） -->
					<view class="panel-group">
						<text class="group-title">关联订单（仅购买过的用户可留言）</text>
						<view v-if="eligibleOrders.length" class="order-chips">
							<view
								v-for="o in eligibleOrders"
								:key="o.orderId"
								:class="['order-chip ap-press', selectedOrderId === o.orderId ? 'active' : '']"
								@tap="selectedOrderId = o.orderId"
							>
								{{ o.orderNo.slice(-8) }}
							</view>
						</view>
						<view v-else class="no-order">
							🛒 你还没有购买过该商品（无合格订单），购买后即可留言～
						</view>
					</view>
					<!-- 评分 -->
					<view class="panel-group">
						<text class="group-title">评分</text>
						<view class="star-row">
							<text
								v-for="s in 5"
								:key="s"
								:class="['star', s <= score ? 'on' : '']"
								@tap="score = s"
							>★</text>
						</view>
					</view>
					<!-- 内容 -->
					<view class="panel-group">
						<text class="group-title">留言内容</text>
						<textarea class="content-input" v-model="content" placeholder="口味、分量、包装…说说你的感受（必填）" maxlength="500" />
					</view>
					<!-- 图片 -->
					<view class="panel-group">
						<text class="group-title">图片（最多5张）</text>
						<view class="image-grid">
							<view v-for="(img, i) in images" :key="i" class="image-item">
								<image class="preview" :src="BASE_URL + img" mode="aspectFill" @tap="previewImg(i)" />
								<view class="remove" @tap="images.splice(i, 1)">✕</view>
							</view>
							<view v-if="images.length < 5" class="add-image ap-press" @tap="addImages">
								<text class="add-icon">📷</text>
							</view>
						</view>
					</view>
				</scroll-view>
				<view class="panel-foot">
					<view :class="['submit-btn ap-press', canSubmit ? '' : 'disabled']" @tap="submit">{{ submitting ? '发布中...' : '发 布' }}</view>
				</view>
			</view>
		</wd-popup>
	</view>
</template>

<script>
import { request, ensureLogin, BASE_URL } from '@/utils/request'
import { chooseAndUploadImages } from '@/utils/upload'

export default {
	data() {
		return {
			goodsId: null,
			goodsName: '',
			stats: {},
			comments: [],
			pageNum: 1,
			pageSize: 10,
			total: 0,
			loading: false,
			finished: false,
			writeOpen: false,
			eligibleOrders: [],
			selectedOrderId: null,
			// T20 评价提醒：从订单页带入的待评价订单（自动打开留言框并预选）
			pendingOrderId: null,
			score: 5,
			content: '',
			images: [],
			submitting: false
		}
	},
	computed: {
		canSubmit() {
			return this.selectedOrderId && this.content.trim() && !this.submitting
		}
	},
	onLoad(options) {
		// 防御：goodsId 必须是正整数，否则 /api/comment/stats/{goodsId} 会拼出 "undefined" 报参数类型错误
		if (!/^\d+$/.test(options.goodsId || '')) {
			uni.showToast({ title: '商品参数有误，请返回重试', icon: 'none' })
			setTimeout(() => uni.navigateBack(), 1200)
			return
		}
		this.goodsId = options.goodsId
		// 跳转时 name 经 encodeURIComponent 编码，需解码后使用（否则导航栏标题显示为 %E9%98%BF… 乱码）
		let name = options.name || ''
		try {
			name = decodeURIComponent(name)
		} catch (e) {
			// 非法编码串按原样处理，避免整页报错
		}
		this.goodsName = name
		uni.setNavigationBarTitle({ title: name ? name + ' · 留言' : '商品留言' })
		// T20 评价提醒：从订单页「去评价」进入时带入待评价订单（自动打开留言框并预选）
		if (options.orderId && /^\d+$/.test(options.orderId)) {
			this.pendingOrderId = Number(options.orderId)
		}
	},
	onShow() {
		this.reload()
	},
	onReachBottom() {
		this.loadMore()
	},
	methods: {
		/** 刷新列表与统计（goodsId 无效时不发请求） */
		async reload(done) {
			if (!this.goodsId) return
			this.pageNum = 1
			this.finished = false
			this.comments = []
			try {
				const stats = await request({ url: '/api/comment/stats/' + this.goodsId, needAuth: false })
				this.stats = stats.data || {}
			} catch (e) { /* 统计失败不阻塞列表 */ }
			await this.loadPage()
			this.applyPendingOrder()
			if (done) done()
		},
		/** T20 评价提醒：带入了待评价订单时，自动打开留言框并预选该订单 */
		async applyPendingOrder() {
			if (!this.pendingOrderId) return
			const target = this.pendingOrderId
			this.pendingOrderId = null
			try {
				await ensureLogin()
				const body = await request({ url: '/api/comment/eligible-orders/' + this.goodsId })
				this.eligibleOrders = body.data || []
				if (this.eligibleOrders.some(o => o.orderId === target)) {
					this.selectedOrderId = target
					this.score = 5
					this.content = ''
					this.images = []
					this.writeOpen = true
				} else {
					uni.showToast({ title: '该订单暂不可评价（需已完成且未退款）', icon: 'none' })
				}
			} catch (e) {
				// 未登录等场景静默，用户可手动写留言
			}
		},
		async loadMore() {
			if (this.finished || this.loading) return
			this.pageNum++
			await this.loadPage()
		},
		async loadPage() {
			this.loading = true
			try {
				const body = await request({
					url: '/api/comment/list/' + this.goodsId + '?pageNum=' + this.pageNum + '&pageSize=' + this.pageSize,
					needAuth: false
				})
				const rows = body.rows || []
				this.comments = this.pageNum === 1 ? rows : this.comments.concat(rows)
				this.total = body.total || 0
				this.finished = this.comments.length >= this.total
			} catch (e) {
				uni.showToast({ title: e.message || '留言加载失败', icon: 'none' })
			} finally {
				this.loading = false
			}
		},
		preview(urls, i) {
			uni.previewImage({ urls, current: urls[i] })
		},
		previewImg(i) {
			this.preview(this.images.map(p => BASE_URL + p), i)
		},
		/** 打开发表弹窗：登录 + 拉取可关联订单（未购买则提示） */
		async openWrite() {
			try {
				await ensureLogin()
				const body = await request({ url: '/api/comment/eligible-orders/' + this.goodsId })
				this.eligibleOrders = body.data || []
				if (!this.eligibleOrders.length) {
					uni.showToast({ title: '购买该商品后才能留言哦～', icon: 'none' })
					return
				}
				this.selectedOrderId = this.eligibleOrders[0].orderId
				this.score = 5
				this.content = ''
				this.images = []
				this.writeOpen = true
			} catch (e) {
				uni.showToast({ title: e.message || '请先登录', icon: 'none' })
			}
		},
		async addImages() {
			try {
				await chooseAndUploadImages(5 - this.images.length, (paths) => {
					this.images = paths
				})
			} catch (e) {
				if (e && e.message && e.message !== '取消选择') {
					uni.showToast({ title: e.message, icon: 'none' })
				}
			}
		},
		/** 提交留言（敏感词/资格由后端兜底校验） */
		async submit() {
			if (!this.canSubmit) {
				uni.showToast({ title: !this.selectedOrderId ? '请选择关联订单' : '请填写留言内容', icon: 'none' })
				return
			}
			this.submitting = true
			try {
				await request({
					url: '/api/comment',
					method: 'POST',
					data: {
						goodsId: Number(this.goodsId),
						orderId: this.selectedOrderId,
						content: this.content.trim(),
						images: this.images,
						score: this.score
					}
				})
				uni.showToast({ title: '留言发布成功', icon: 'success' })
				this.writeOpen = false
				this.reload()
			} catch (e) {
				uni.showToast({ title: e.message || '发布失败', icon: 'none' })
			} finally {
				this.submitting = false
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

.stats {
	padding: 28rpx;
	display: flex;
	align-items: center;

	.score-block {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-right: 32rpx;

		.avg {
			font-size: 60rpx;
			font-weight: 700;
			color: $ap-primary;
		}

		.avg-label {
			font-size: 20rpx;
			color: $ap-text-light;
		}
	}

	.stats-right {
		display: flex;
		flex-direction: column;

		.count {
			font-size: 28rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.tip {
			margin-top: 6rpx;
			font-size: 22rpx;
			color: $ap-text-light;
		}
	}

	.write-btn {
		margin-left: auto;
		padding: 14rpx 30rpx;
		font-size: 26rpx;
		font-weight: 700;
		color: #FFFFFF;
		background-color: $ap-primary;
		border-radius: $ap-radius-pill;
	}
}

.comment-card {
	padding: 24rpx;
	margin-bottom: 20rpx;

	.head {
		display: flex;
		align-items: center;

		.avatar {
			width: 72rpx;
			height: 72rpx;
			border-radius: 50%;
			background-color: $ap-bg;
			flex-shrink: 0;
		}

		.head-info {
			flex: 1;
			min-width: 0;
			margin-left: 16rpx;

			.nickname {
				font-size: 26rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.sub-row {
				display: flex;
				align-items: center;

				.stars {
					font-size: 22rpx;
					color: $ap-secondary;
				}

				.bought-tag {
					margin-left: 10rpx;
					padding: 0 12rpx;
					font-size: 18rpx;
					color: $ap-green;
					background-color: rgba(127, 183, 126, 0.12);
					border-radius: $ap-radius-pill;
				}
			}
		}

		.time {
			font-size: 20rpx;
			color: $ap-text-light;
		}
	}

	.content {
		display: block;
		margin-top: 16rpx;
		font-size: 26rpx;
		color: $ap-text;
		word-break: break-all;
	}

	.images {
		margin-top: 14rpx;
		display: flex;
		flex-wrap: wrap;

		.img {
			width: 150rpx;
			height: 150rpx;
			border-radius: 12rpx;
			margin: 0 12rpx 12rpx 0;
			background-color: $ap-bg;
		}
	}

	.reply-box {
		margin-top: 16rpx;
		padding: 16rpx;
		background-color: $ap-bg;
		border-radius: 12rpx;

		.reply-label {
			font-size: 22rpx;
			font-weight: 700;
			color: $ap-primary;
			margin-right: 12rpx;
		}

		.reply-text {
			font-size: 24rpx;
			color: $ap-text;
		}
	}
}

.loading-tip {
	text-align: center;
	padding: 24rpx;
	font-size: 22rpx;
	color: $ap-text-light;
}

/* 发表弹窗 */
.write-panel {
	padding: 32rpx;

	.panel-head {
		display: flex;
		align-items: center;

		.panel-title {
			font-size: 32rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.close {
			margin-left: auto;
			font-size: 32rpx;
			color: $ap-text-light;
		}
	}

	.panel-body {
		max-height: 60vh;
		margin-top: 20rpx;

		.panel-group {
			margin-bottom: 28rpx;

			.group-title {
				font-size: 26rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.order-chips {
				margin-top: 14rpx;
				display: flex;
				flex-wrap: wrap;

				.order-chip {
					margin: 0 14rpx 14rpx 0;
					padding: 12rpx 26rpx;
					font-size: 24rpx;
					color: $ap-text;
					background-color: $ap-bg;
					border-radius: $ap-radius-pill;

					&.active {
						color: #FFFFFF;
						background-color: $ap-primary;
						font-weight: 700;
					}
				}
			}

			.no-order {
				margin-top: 14rpx;
				padding: 20rpx;
				font-size: 24rpx;
				color: $ap-text-light;
				background-color: $ap-bg;
				border-radius: 12rpx;
			}

			.star-row {
				margin-top: 10rpx;

				.star {
					font-size: 52rpx;
					color: $ap-bg;
					margin-right: 14rpx;

					&.on {
						color: $ap-secondary;
					}
				}
			}

			.content-input {
				width: 100%;
				height: 150rpx;
				margin-top: 14rpx;
				padding: 16rpx;
				box-sizing: border-box;
				font-size: 26rpx;
				color: $ap-text;
				background-color: $ap-bg;
				border-radius: 12rpx;
			}

			.image-grid {
				margin-top: 14rpx;
				display: flex;
				flex-wrap: wrap;

				.image-item {
					position: relative;
					margin: 0 14rpx 14rpx 0;

					.preview {
						width: 130rpx;
						height: 130rpx;
						border-radius: 12rpx;
						background-color: $ap-bg;
					}

					.remove {
						position: absolute;
						top: -10rpx;
						right: -10rpx;
						width: 34rpx;
						height: 34rpx;
						line-height: 30rpx;
						text-align: center;
						font-size: 20rpx;
						color: #FFFFFF;
						background-color: $ap-text-light;
						border-radius: 50%;
					}
				}

				.add-image {
					width: 130rpx;
					height: 130rpx;
					background-color: $ap-bg;
					border-radius: 12rpx;
					display: flex;
					align-items: center;
					justify-content: center;

					.add-icon {
						font-size: 44rpx;
					}
				}
			}
		}
	}

	.panel-foot {
		padding-top: 16rpx;

		.submit-btn {
			height: 88rpx;
			line-height: 88rpx;
			text-align: center;
			font-size: 30rpx;
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
}
</style>
