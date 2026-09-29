<template>
	<view class="page" v-if="goods.id">
		<!-- 商品大图 -->
		<image class="hero" :src="goods.image || '/static/images/placeholder.png'" mode="aspectFill" />

		<!-- 商品信息卡 -->
		<view class="info-card ap-card">
				<view class="name-row">
					<text class="name">{{ goods.name }}</text>
					<text v-if="isSoldOut" class="soldout-tag">已售罄</text>
				</view>
			<text class="desc">{{ goods.description || '阿婆用心制作' }}</text>
			<view class="price-row">
				<text class="symbol">¥</text>
				<text class="price">{{ displayPrice }}</text>
				<text class="sales" v-if="goods.sales !== null && goods.sales !== undefined">已售 {{ goods.sales }} 份</text>
			</view>
			<!-- T12：每日限量剩余库存（未启用限量时不展示） -->
			<view class="stock-row" v-if="goods.dailyLimitEnabled && goods.remainQty !== null && goods.remainQty !== undefined">
				<text class="stock-text" :class="{ low: goods.remainQty <= 3 }">今日剩余 {{ goods.remainQty }} 份</text>
			</view>
		</view>

		<!-- 留言区（仅购买过的用户可留言） -->
		<view class="comments-entry ap-card" @tap="goComments">
			<view class="comments-head">
				<text class="comments-title">留言 ({{ commentStats.count || 0 }})</text>
				<text class="comments-score" v-if="commentStats.count">均分 {{ commentStats.avgScore }}</text>
				<text class="comments-arrow">全部 ›</text>
			</view>
			<view v-for="(c, i) in commentPreview" :key="c.id" class="comment-line">
				<text class="comment-nick">{{ c.nickname }}</text>
				<text class="comment-star">{{ '★'.repeat(c.score) }}</text>
				<text class="comment-content">{{ c.content }}</text>
			</view>
			<text class="comments-tip" v-if="!commentPreview.length">暂无留言～购买过的用户可以发布留言</text>
		</view>

		<!-- 底部操作栏：收藏❤ + 售罄置灰/正常可打开规格口味弹窗 -->
		<view class="action-bar">
			<!-- T20 收藏切换（未登录点击时引导登录） -->
			<view class="fav-btn ap-press" @tap="toggleFav">{{ favorited ? '❤️' : '🤍' }}</view>
			<view
				v-if="isSoldOut"
				class="action-btn disabled"
			>已售罄 · 明日请早</view>
			<view v-else class="action-btn ap-press" @tap="openSelector">
				{{ hasOptions ? '选择规格口味' : '加入购物车' }}
			</view>
		</view>

		<!-- 规格/口味选择弹窗（可爱风大圆角；选择、计价与加入购物车） -->
		<wd-popup v-model="showSelector" position="bottom" :safe-area-inset-bottom="true">
			<view class="selector" v-if="goods.id">
				<view class="selector-head">
					<image class="selector-img" :src="goods.image || '/static/images/placeholder.png'" mode="aspectFill" />
					<view class="selector-head-info">
						<text class="selector-name">{{ goods.name }}</text>
						<view class="price-row">
							<text class="symbol">¥</text>
							<text class="price">{{ computedPrice }}</text>
						</view>
					</view>
					<text class="close" @tap="showSelector = false">✕</text>
				</view>

				<scroll-view scroll-y class="selector-body">
					<!-- 规格（单选；售罄规格置灰不可选，展示「售罄」标签） -->
					<view v-if="goods.specs && goods.specs.length" class="group">
						<text class="group-title">规格</text>
						<view class="chips">
							<view
								v-for="s in goods.specs"
								:key="'spec-' + s.id"
								:class="['chip', isSpecSoldOut(s) ? 'sold-out' : 'ap-press', selectedSpecId === s.id ? 'active' : '']"
								@tap="chooseSpec(s)"
							>
								{{ s.name }}
								<text v-if="s.priceDelta" class="delta">{{ s.priceDelta > 0 ? '+' + s.priceDelta : s.priceDelta }}</text>
								<!-- T12：规格级剩余库存 -->
								<text v-if="s.remainQty !== null && s.remainQty !== undefined" class="stock-mini">剩{{ s.remainQty }}</text>
								<text v-if="isSpecSoldOut(s)" class="sold-tag">售罄</text>
							</view>
						</view>
					</view>

					<!-- 口味组（单选/多选） -->
					<view v-for="f in goods.flavors" :key="'flavor-' + f.id" class="group">
						<text class="group-title">{{ f.name }}<text class="group-sub">{{ f.selectType === '1' ? '（可多选）' : '（单选）' }}</text></text>
						<view class="chips">
							<view
								v-for="opt in f.options"
								:key="f.id + '-' + opt"
								:class="['chip ap-press', isFlavorPicked(f, opt) ? 'active' : '']"
								@tap="toggleFlavor(f, opt)"
							>{{ opt }}</view>
						</view>
					</view>
				</scroll-view>

					<view class="selector-foot">
						<view class="qty-stepper">
							<view class="step-btn ap-press" @tap="changeQty(-1)">－</view>
							<text class="qty">{{ quantity }}</text>
							<view class="step-btn ap-press" @tap="changeQty(1)">＋</view>
						</view>
						<view :class="['confirm-btn', allSpecsSoldOut ? 'disabled' : 'ap-press']" @tap="addToCart">
							{{ allSpecsSoldOut ? '已售罄 · 明日请早' : ('加入购物车 · ¥' + computedTotal) }}
						</view>
					</view>
			</view>
		</wd-popup>
	</view>
</template>

<script>
import { request, ensureLogin, getMember } from '@/utils/request'

export default {
	data() {
		return {
			goodsId: null,
			goods: {},               // 详情（含 specs/flavors，图片 URL 后端已拼好）
			showSelector: false,
			selectedSpecId: null,    // 已选规格（单选，可空）
			flavorPicks: {},         // 已选口味：{ flavorId: [选项,...] }（单选组最多一个）
			quantity: 1,             // 加购数量
			commentStats: {},        // 留言统计（条数/均分）
			commentPreview: [],      // 留言预览（前2条）
			favorited: false         // T20 是否已收藏
		}
	},
	computed: {
		/** 是否有规格或口味可选 */
		hasOptions() {
			return (this.goods.specs && this.goods.specs.length > 0) ||
			       (this.goods.flavors && this.goods.flavors.length > 0)
		},
		/** 有效售罄：菜品级售罄 或 全部规格售罄（与列表展示口径一致） */
		isSoldOut() {
			return this.goods.soldOut === '1' || this.allSpecsSoldOut
		},
		/** 该菜品有规格且全部规格售罄 */
		allSpecsSoldOut() {
			const specs = this.goods.specs || []
			return specs.length > 0 && specs.every(s => this.isSpecSoldOut(s))
		},
		/** 基础价 + 规格差价（单价） */
		computedPrice() {
			const base = Number(this.goods.price || 0)
			let delta = 0
			if (this.selectedSpecId && this.goods.specs) {
				const spec = this.goods.specs.find(s => s.id === this.selectedSpecId)
				if (spec && spec.priceDelta) delta = Number(spec.priceDelta)
			}
			return (base + delta).toFixed(2)
		},
		/** 单价 x 数量 */
		computedTotal() {
			return (Number(this.computedPrice) * this.quantity).toFixed(2)
		},
		/** 展示价（未打开弹窗时展示基础价） */
		displayPrice() {
			return this.showSelector ? this.computedPrice : Number(this.goods.price || 0).toFixed(2)
		}
	},
	onLoad(options) {
		// 防御：id 必须是正整数，否则后续 /api/goods/detail/{id} 会拼出 "undefined" 报参数类型错误
		if (!/^\d+$/.test(options.id || '')) {
			uni.showToast({ title: '商品参数有误，请返回首页重试', icon: 'none' })
			// 首页不是 tabBar 页（本项目未配置 tabBar），须用 reLaunch 而非 switchTab
			setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 1200)
			return
		}
		this.goodsId = options.id
		this.autoOpen = options.autoOpen === '1'
	},
	onShow() {
		if (!this.goodsId) return
		this.loadDetail()
		this.loadComments()
		this.loadFavStatus()
	},
	methods: {
		/** T20 收藏状态（未登录静默跳过，显示空心❤） */
		async loadFavStatus() {
			if (!getMember()) {
				this.favorited = false
				return
			}
			try {
				const body = await request({ url: '/api/favorite/' + this.goodsId + '/status' })
				this.favorited = !!(body.data && body.data.favorited)
			} catch (e) {
				this.favorited = false
			}
		},
		/** T20 收藏/取消收藏（未登录先引导登录） */
		async toggleFav() {
			try {
				await ensureLogin()
				const body = await request({ url: '/api/favorite/toggle/' + this.goodsId, method: 'POST' })
				this.favorited = !!(body.data && body.data.favorited)
				uni.showToast({ title: this.favorited ? '已收藏' : '已取消收藏', icon: 'none' })
			} catch (e) {
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
			}
		},
		/** 加载详情（售罄菜品可查看详情） */
			async loadDetail() {
				try {
					const body = await request({ url: '/api/goods/detail/' + this.goodsId, needAuth: false })
					this.goods = body.data || {}
					// 默认选中第一个未售罄的规格（全部售罄则不选中）
					if (this.goods.specs && this.goods.specs.length) {
						const firstOk = this.goods.specs.find(s => !this.isSpecSoldOut(s))
						this.selectedSpecId = firstOk ? firstOk.id : null
					}
					// 首页带规格/口味菜品的"+"跳转进来时自动打开选择弹窗
					if (this.autoOpen && !this.isSoldOut) {
						this.autoOpen = false
						this.showSelector = true
					}
				} catch (e) {
					uni.showToast({ title: e.message || '菜品加载失败', icon: 'none' })
					setTimeout(() => uni.navigateBack(), 1200)
				}
			},
		/** 留言统计与预览（游客可看） */
		async loadComments() {
			try {
				const stats = await request({ url: '/api/comment/stats/' + this.goodsId, needAuth: false })
				this.commentStats = stats.data || {}
				const list = await request({
					url: '/api/comment/list/' + this.goodsId + '?pageNum=1&pageSize=2',
					needAuth: false
				})
				this.commentPreview = list.rows || []
			} catch (e) {
				console.warn('留言加载失败:', e.message)
			}
		},
		/** 查看全部留言 */
		goComments() {
			uni.navigateTo({
				url: '/pages/goods/comments?goodsId=' + this.goodsId + '&name=' + encodeURIComponent(this.goods.name || '')
			})
		},
		openSelector() {
			this.showSelector = true
		},
		/** 加购数量步进（1-99） */
		changeQty(delta) {
			const next = this.quantity + delta
			if (next < 1 || next > 99) return
			this.quantity = next
		},
		/** 规格是否售罄（规格级售罄标记，菜品级售罄由上层判断） */
		isSpecSoldOut(spec) {
			return spec && spec.soldOut === '1'
		},
		/** 选规格（单选；售罄规格不可选中） */
		chooseSpec(spec) {
			if (this.isSpecSoldOut(spec)) {
				uni.showToast({ title: '该规格已售罄，请选择其他规格', icon: 'none' })
				return
			}
			this.selectedSpecId = spec.id
		},
		/** 口味选择：单选组覆盖，多选组切换 */
		toggleFlavor(flavor, option) {
			const picks = { ...this.flavorPicks }
			const current = picks[flavor.id] || []
			if (flavor.selectType === '1') {
				// 多选
				const idx = current.indexOf(option)
				if (idx > -1) {
					current.splice(idx, 1)
				} else {
					current.push(option)
				}
				picks[flavor.id] = current
			} else {
				// 单选（再点一次可取消）
				picks[flavor.id] = current[0] === option ? [] : [option]
			}
			this.flavorPicks = picks
		},
		isFlavorPicked(flavor, option) {
			const current = this.flavorPicks[flavor.id] || []
			return current.indexOf(option) > -1
		},
		/**
		 * 加入购物车：组装规格/口味快照（口味 JSON 由后端规范化合并），
		 * 售罄/下架由后端二次校验拦截
		 */
		async addToCart() {
			// 全部规格售罄时按钮已置灰；此处兜底拦截（后端仍有二次校验）
			if (this.allSpecsSoldOut || this.goods.soldOut === '1') {
				uni.showToast({ title: '已售罄 · 明日请早', icon: 'none' })
				return
			}
			if (this.goods.specs && this.goods.specs.length && !this.selectedSpecId) {
				uni.showToast({ title: '该商品规格已售罄，请选择其他规格', icon: 'none' })
				return
			}
			const flavorJson = this.buildFlavorJson()
			try {
				await request({
					url: '/api/cart/add',
					method: 'POST',
					data: {
						goodsId: this.goods.id,
						specId: this.selectedSpecId || null,
						flavorJson: flavorJson || null,
						quantity: this.quantity
					}
				})
				this.showSelector = false
				this.quantity = 1
				uni.showToast({ title: '已加入购物车', icon: 'none' })
			} catch (e) {
				uni.showToast({ title: e.message || '加购失败', icon: 'none' })
			}
		},
		/** 已选口味 -> 快照 JSON（[{name, values[]}]，与购物车存储结构一致） */
		buildFlavorJson() {
			const picks = []
			this.goods.flavors && this.goods.flavors.forEach(f => {
				const values = this.flavorPicks[f.id] || []
				if (values.length) {
					picks.push({ name: f.name, values: values })
				}
			})
			return picks.length ? JSON.stringify(picks) : ''
		},
		/** 汇总当前选择（T4 加购后提示不再使用，保留供展示） */
		buildSelectionSummary() {
			const parts = []
			if (this.selectedSpecId && this.goods.specs) {
				const spec = this.goods.specs.find(s => s.id === this.selectedSpecId)
				if (spec) parts.push(spec.name)
			}
			this.goods.flavors && this.goods.flavors.forEach(f => {
				const picked = this.flavorPicks[f.id] || []
				if (picked.length) parts.push(picked.join('/'))
			})
			return parts.join(' · ')
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding-bottom: 140rpx;
}

.hero {
	width: 100%;
	height: 560rpx;
	display: block;
	background-color: $ap-bg;
}

.info-card {
	margin: -60rpx 20rpx 0;
	padding: 28rpx;
	position: relative;

	.name-row {
		display: flex;
		align-items: center;

		.name {
			font-size: 38rpx;
			font-weight: 700;
			color: $ap-text;
			flex: 1;
		}

		.soldout-tag {
			padding: 4rpx 20rpx;
			font-size: 22rpx;
			color: #FFFFFF;
			background-color: $ap-text-light;
			border-radius: $ap-radius-pill;
		}
	}

	.desc {
		display: block;
		margin-top: 12rpx;
		font-size: 26rpx;
		color: $ap-text-light;
	}

		.price-row {
			margin-top: 20rpx;
			display: flex;
			align-items: baseline;

			.symbol {
				font-size: 26rpx;
				color: $ap-primary;
				font-weight: 700;
			}

			.price {
				font-size: 48rpx;
				color: $ap-primary;
				font-weight: 700;
			}

			.sales {
				margin-left: auto;
				font-size: 22rpx;
				color: $ap-text-light;
			}
		}

		/* T12 每日限量剩余库存 */
		.stock-row {
			margin-top: 12rpx;

			.stock-text {
				font-size: 24rpx;
				color: $ap-green;
				font-weight: 700;

				&.low {
					color: $ap-primary;
				}
			}
		}
	}

/* 留言区入口 */
.comments-entry {
	margin-top: 20rpx;
	padding: 24rpx;

	.comments-head {
		display: flex;
		align-items: center;

		.comments-title {
			font-size: 28rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.comments-score {
			margin-left: 14rpx;
			font-size: 24rpx;
			color: $ap-secondary;
			font-weight: 700;
		}

		.comments-arrow {
			margin-left: auto;
			font-size: 24rpx;
			color: $ap-text-light;
		}
	}

	.comment-line {
		margin-top: 16rpx;
		display: flex;
		align-items: center;
		font-size: 24rpx;

		.comment-nick {
			color: $ap-text-light;
			flex-shrink: 0;
		}

		.comment-star {
			color: $ap-secondary;
			margin: 0 12rpx;
			flex-shrink: 0;
		}

		.comment-content {
			color: $ap-text;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}
	}

	.comments-tip {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: $ap-text-light;
	}
}

/* 底部操作栏 */
.action-bar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	display: flex;
	align-items: center;
	padding: 20rpx 32rpx;
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
	background-color: $ap-card;
	box-shadow: 0 -4rpx 16rpx rgba(255, 112, 67, 0.08);

	/* T20 收藏按钮（动作栏左侧） */
	.fav-btn {
		width: 88rpx;
		height: 88rpx;
		line-height: 84rpx;
		text-align: center;
		font-size: 44rpx;
		background-color: $ap-bg;
		border-radius: $ap-radius-pill;
		flex-shrink: 0;
		margin-right: 16rpx;
	}

	.action-btn {
		flex: 1;
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

/* 规格/口味弹窗 */
.selector {
	padding: 32rpx;

	.selector-head {
		display: flex;
		align-items: center;

		.selector-img {
			width: 120rpx;
			height: 120rpx;
			border-radius: 16rpx;
			background-color: $ap-bg;
		}

		.selector-head-info {
			flex: 1;
			margin-left: 20rpx;

			.selector-name {
				font-size: 30rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.price-row {
				margin-top: 8rpx;

				.symbol { font-size: 22rpx; color: $ap-primary; font-weight: 700; }
				.price { font-size: 40rpx; color: $ap-primary; font-weight: 700; }
			}
		}

		.close {
			padding: 8rpx;
			font-size: 32rpx;
			color: $ap-text-light;
		}
	}

	.selector-body {
		max-height: 52vh;
		margin-top: 24rpx;

		.group {
			margin-bottom: 28rpx;

			.group-title {
				font-size: 26rpx;
				font-weight: 700;
				color: $ap-text;

				.group-sub {
					margin-left: 8rpx;
					font-size: 22rpx;
					font-weight: 400;
					color: $ap-text-light;
				}
			}

			.chips {
				margin-top: 16rpx;
				display: flex;
				flex-wrap: wrap;

					.chip {
						margin: 0 16rpx 16rpx 0;
						padding: 12rpx 32rpx;
						font-size: 26rpx;
						color: $ap-text;
						background-color: $ap-bg;
						border-radius: $ap-radius-pill;

						.delta {
							margin-left: 6rpx;
							font-size: 22rpx;
							color: $ap-text-light;
						}

						/* T12 规格剩余库存小标 */
						.stock-mini {
							margin-left: 6rpx;
							font-size: 20rpx;
							color: $ap-green;
						}

						/* 规格级售罄：置灰、不可选中 */
						&.sold-out {
							color: $ap-text-light;
							background-color: $ap-bg;
							opacity: 0.55;

							.delta {
								color: $ap-text-light;
							}

							.sold-tag {
								margin-left: 8rpx;
								padding: 2rpx 12rpx;
								font-size: 20rpx;
								color: #FFFFFF;
								background-color: $ap-text-light;
								border-radius: $ap-radius-pill;
							}
						}

						&.active {
							color: #FFFFFF;
							background-color: $ap-primary;
							font-weight: 700;

							.delta {
								color: rgba(255, 255, 255, 0.85);
							}
						}
					}
			}
		}
	}

	.selector-foot {
		padding-top: 16rpx;
		display: flex;
		align-items: center;

		.qty-stepper {
			display: flex;
			align-items: center;

			.step-btn {
				width: 56rpx;
				height: 56rpx;
				line-height: 50rpx;
				text-align: center;
				font-size: 32rpx;
				color: $ap-primary;
				background-color: $ap-bg;
				border-radius: 50%;
			}

			.qty {
				min-width: 70rpx;
				text-align: center;
				font-size: 30rpx;
				font-weight: 700;
				color: $ap-text;
			}
		}

		.confirm-btn {
			margin-left: auto;
			flex: 1;
			max-width: 60%;
			height: 88rpx;
			line-height: 88rpx;
			text-align: center;
			font-size: 28rpx;
			font-weight: 700;
			color: #FFFFFF;
			background-color: $ap-primary;
			border-radius: $ap-radius-pill;

			/* 全部规格售罄：置灰禁用 */
			&.disabled {
				background-color: $ap-text-light;
				font-weight: 400;
			}
		}
	}
}
</style>
