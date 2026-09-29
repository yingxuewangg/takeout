<template>
	<view class="page">
		<view v-for="f in favoriteList" :key="f.id" :class="['fav-card', 'ap-card', { invalid: !isAvailable(f) }]">
			<image class="thumb" :src="f.image || '/static/images/placeholder.png'" mode="aspectFill" />
			<view class="info" @tap="goDetail(f)">
				<view class="name-row">
					<text class="name">{{ f.goodsName }}</text>
					<text v-if="!f.onSale && f.goodsName !== '商品已失效'" class="status-tag">已下架</text>
					<text v-else-if="f.goodsName === '商品已失效'" class="status-tag">已失效</text>
					<text v-else-if="f.soldOut === '1'" class="status-tag soldout">已售罄</text>
				</view>
				<text class="desc">{{ f.description || '阿婆用心制作' }}</text>
				<view class="bottom">
					<text class="price">¥{{ fix(f.price) }}</text>
					<view class="actions">
						<text class="op-btn danger ap-press" @tap.stop="unfavorite(f)">取消收藏</text>
						<view v-if="isAvailable(f)" class="op-btn primary ap-press" @tap.stop="addToCart(f)">加入购物车</view>
					</view>
				</view>
			</view>
		</view>
		<view v-if="!favoriteList.length && !loading" class="ap-empty">
			<text class="em">💔</text>
			还没有收藏的菜品，去菜单页点❤收藏吧～
		</view>
		<view v-if="favoriteList.length && !finished" class="load-more" @tap="loadMore">
			{{ loading ? '加载中...' : '加载更多' }}
		</view>
	</view>
</template>

<script>
import { request, ensureLogin } from '@/utils/request'

export default {
	data() {
		return {
			favoriteList: [],
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
	methods: {
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		/** 可加购：在售且非菜品级售罄（失效商品 = 名称被置为"商品已失效"，onSale 为 null） */
		isAvailable(f) {
			return !!f.onSale && f.soldOut !== '1' && f.goodsName !== '商品已失效'
		},
		async reload() {
			this.pageNum = 1
			this.finished = false
			this.favoriteList = []
			await this.loadPage()
		},
		async loadPage() {
			if (this.loading || this.finished) return
			this.loading = true
			try {
				await ensureLogin()
				const body = await request({
					url: '/api/favorite/list?pageNum=' + this.pageNum + '&pageSize=' + this.pageSize
				})
				const rows = body.rows || []
				this.favoriteList = this.favoriteList.concat(rows)
				this.total = body.total || 0
				this.finished = this.favoriteList.length >= this.total || rows.length === 0
				this.pageNum += 1
			} catch (e) {
				uni.showToast({ title: e.message || '收藏加载失败', icon: 'none' })
			} finally {
				this.loading = false
			}
		},
		loadMore() {
			this.loadPage()
		},
		goDetail(f) {
			// 失效/下架商品仍可进详情页看历史信息（详情接口按商品存在性拦截）
			if (f.goodsName === '商品已失效') return
			uni.navigateTo({ url: '/pages/goods/detail?id=' + f.goodsId })
		},
		/** 取消收藏（物理删除），列表移除该行 */
		async unfavorite(f) {
			try {
				await request({ url: '/api/favorite/toggle/' + f.goodsId, method: 'POST' })
				this.favoriteList = this.favoriteList.filter(item => item.id !== f.id)
				this.total = Math.max(this.total - 1, 0)
				uni.showToast({ title: '已取消收藏', icon: 'none' })
			} catch (e) {
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
			}
		},
		/** 加入购物车（复用通用加购接口；售罄/库存由后端二次校验） */
		async addToCart(f) {
			try {
				await request({
					url: '/api/cart/add',
					method: 'POST',
					data: { goodsId: f.goodsId, quantity: 1 }
				})
				uni.showToast({ title: '已加入购物车', icon: 'none' })
			} catch (e) {
				uni.showToast({ title: e.message || '加购失败', icon: 'none' })
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background-color: #fff6ec;
	padding: 24rpx;
	box-sizing: border-box;
}

.fav-card {
	display: flex;
	padding: 20rpx;
	margin-bottom: 20rpx;

	&.invalid {
		opacity: 0.55;

		.thumb {
			filter: grayscale(0.8);
		}
	}

	.thumb {
		width: 160rpx;
		height: 160rpx;
		border-radius: 16rpx;
		flex-shrink: 0;
		background-color: #fff6ec;
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
				font-size: 30rpx;
				font-weight: 700;
				color: #4a3728;
				overflow: hidden;
				text-overflow: ellipsis;
				white-space: nowrap;
			}

			.status-tag {
				margin-left: 12rpx;
				padding: 2rpx 14rpx;
				font-size: 20rpx;
				color: #ffffff;
				background-color: #a89888;
				border-radius: 999rpx;
				flex-shrink: 0;

				&.soldout {
					background-color: #ffc53d;
					color: #4a3728;
				}
			}
		}

		.desc {
			margin-top: 8rpx;
			font-size: 24rpx;
			color: #a89888;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		.bottom {
			margin-top: auto;
			display: flex;
			align-items: center;
			justify-content: space-between;

			.price {
				font-size: 34rpx;
				font-weight: 700;
				color: #ff7043;
			}

			.actions {
				display: flex;
				align-items: center;
				gap: 16rpx;

				.op-btn {
					font-size: 24rpx;
					border-radius: 999rpx;
					padding: 8rpx 24rpx;

					&.danger {
						color: #f56c6c;
						border: 1rpx solid #f56c6c;
					}

					&.primary {
						color: #ffffff;
						background-color: #ff7043;
					}
				}
			}
		}
	}
}

.ap-empty {
	padding: 160rpx 0;
	text-align: center;
	font-size: 26rpx;
	color: #a89888;

	.em {
		display: block;
		font-size: 64rpx;
		margin-bottom: 16rpx;
	}
}

.load-more {
	text-align: center;
	padding: 20rpx;
	font-size: 24rpx;
	color: #a89888;
}
</style>
