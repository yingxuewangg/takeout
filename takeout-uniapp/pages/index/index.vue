<template>
	<view class="page">
		<!-- 店铺信息条（点击进商家信息页：位置/地图/电话） -->
		<view class="shop-bar ap-card ap-press" @tap="goShopInfo">
			<view class="shop-name-row">
				<text class="emoji">🍚</text>
				<text class="shop-name">{{ shop.shopName || '阿婆干饭社' }}</text>
				<text :class="['biz-tag', shop.businessStatus === 1 ? 'open' : 'closed']">
					{{ shop.businessStatus === 1 ? '营业中' : '已打烊' }}
				</text>
			</view>
			<text class="notice" v-if="shop.notice">📣 {{ shop.notice }}</text>
			<view class="meta" v-if="shop.businessHours">
				<text>🕐 {{ shop.businessHours }}</text>
				<text class="phone" @tap.stop="callShop" v-if="shop.phone">☎️ {{ shop.phone }}</text>
			</view>
		</view>

		<!-- 堂食 / 外卖切换（下单方式，T4 确认订单使用） -->
		<view class="dining-bar ap-card">
			<view class="dining-switch">
				<view :class="['opt ap-press', dining.type === 'dine' ? 'active' : '']" @tap="switchDining('dine')">
					🍜 堂食
				</view>
				<view :class="['opt ap-press', dining.type === 'takeout' ? 'active' : '']" @tap="switchDining('takeout')">
					🛵 外卖配送
				</view>
			</view>
			<view v-if="dining.type === 'dine'" class="dining-tip" @tap="editTableNo">
				桌号：{{ dining.tableNo ? dining.tableNo + ' 号' : '点击选择' }} ✏️
			</view>
			<view v-else class="dining-tip">
				起送 ¥{{ shop.minDeliveryAmount || 0 }} · 配送费 ¥{{ shop.deliveryFee || 0 }}
			</view>
		</view>

		<!-- 我的订单入口（T6 订单中心） -->
		<view class="order-entry ap-card ap-press" @tap="goOrders">
			<text class="entry-icon">🧾</text>
			<text class="entry-text">我的订单</text>
			<text class="entry-arrow">›</text>
		</view>

		<!-- 我的收藏入口（T20 子项④） -->
		<view class="order-entry ap-card ap-press" @tap="goFavorites">
			<text class="entry-icon">❤️</text>
			<text class="entry-text">我的收藏</text>
			<text class="entry-arrow">›</text>
		</view>

		<!-- AI 助手入口（二期 RAG） -->
		<view class="order-entry ap-card ap-press" @tap="goAiChat">
			<text class="entry-icon">🤖</text>
			<text class="entry-text">问问阿婆（AI 点餐助手）</text>
			<text class="entry-arrow">›</text>
		</view>

		<!-- 搜索 -->
		<view class="search-bar">
			<view class="search-box ap-card">
				<text class="search-icon">🔍</text>
				<input
					class="search-input"
					v-model="keyword"
					placeholder="搜索菜品，如：盖饭"
					placeholder-class="search-placeholder"
					confirm-type="search"
					@confirm="doSearch"
				/>
				<text v-if="searchMode" class="clear" @tap="clearSearch">✕</text>
			</view>
			<view class="search-btn ap-press" @tap="doSearch">搜索</view>
		</view>

		<!-- 主体：左分类 + 右商品 -->
		<view class="menu-wrap" v-if="!searchMode">
			<scroll-view class="cate-side" scroll-y>
				<view
					v-for="c in categories"
					:key="c.categoryId"
					:class="['cate-item', currentCateId === c.categoryId ? 'active' : '']"
					@tap="switchCate(c.categoryId)"
				>
					<text class="cate-name">{{ c.categoryName }}</text>
				</view>
			</scroll-view>

			<scroll-view
				class="goods-side"
				scroll-y
				:scroll-into-view="'cate-anchor-' + currentCateId"
				scroll-with-animation
				@scroll="onGoodsScroll"
			>
				<view v-for="c in categories" :key="'block-' + c.categoryId" :id="'cate-anchor-' + c.categoryId">
					<view class="cate-title">{{ c.categoryName }}</view>
					<goods-card
						v-for="g in c.goodsList"
						:key="g.id"
						:goods="g"
						:favorited="favoriteIds.includes(g.id)"
						@open="goDetail"
						@add="onAddTap"
						@fav="onFavToggle"
					/>
					<view v-if="!c.goodsList || !c.goodsList.length" class="empty-cate">
						该分类暂无在售菜品～
					</view>
				</view>
				<view v-if="!categories.length" class="ap-empty">
					<text class="em">🍚</text>
					菜单准备中，稍后再来看看～
				</view>
				<view class="bottom-placeholder">— 阿婆说：要好好吃饭 —</view>
			</scroll-view>
		</view>

		<!-- 搜索结果 -->
		<scroll-view class="search-result" scroll-y v-else>
			<goods-card
				v-for="g in searchResults"
				:key="'s-' + g.id"
				:goods="g"
				:favorited="favoriteIds.includes(g.id)"
				@open="goDetail"
				@add="onAddTap"
				@fav="onFavToggle"
			/>
			<view v-if="searched && !searchResults.length" class="ap-empty">
				<text class="em">🥺</text>
				没有找到相关菜品，换个关键词试试～
			</view>
		</scroll-view>

		<!-- 底部购物车栏（合计/角标弹跳/去结算） -->
		<cart-bar
			v-if="categories.length"
			:count="cartCount"
			:amount="cartAmount"
			button-text="去结算"
			@goCart="goCart"
			@checkout="goCart"
		/>

		<!-- 加购抛物线飞入动画（微交互：商品飞向购物车） -->
		<view
			v-for="dot in flyDots"
			:key="dot.id"
			class="fly-dot"
			:style="{ left: dot.x + 'px', top: dot.y + 'px', transform: dot.moved ? 'translate(' + dot.tx + 'px,' + dot.ty + 'px) scale(0.4)' : 'translate(0,0)', opacity: dot.moved ? 0.3 : 1 }"
		>🍚</view>
	</view>
</template>

<script>
import { request, ensureLogin, getMember } from '@/utils/request'
import { getDining, setDining, setTableNo, parseSceneTableNo } from '@/utils/dining'

export default {
	data() {
		return {
			shop: {},                    // 店铺信息（含营业状态/配送费/起送价）
			categories: [],              // 分类 + 在售菜品
			currentCateId: null,         // 当前选中分类
			cateOffsets: [],             // 右侧各分类区块 offsetTop（滚动联动）
			dining: { type: 'dine', tableNo: '' }, // 履约方式
			keyword: '',
			searchMode: false,
			searchResults: [],
			searched: false,
			searching: false,            // 搜索请求进行中（防连点重复请求）
			cartCount: 0,                // 购物车商品总数（底部栏角标）
			cartAmount: 0,               // 购物车有效合计金额
			flyDots: [],                 // 加购飞入动画点
			flySeq: 0,
			favoriteIds: []              // T20 收藏的菜品ID集合（❤状态展示）
		}
	},
	onLoad(options) {
		// 扫码进入：scene 可携带桌号（堂食自动带入）
		const tableNo = parseSceneTableNo(options && options.scene)
		if (tableNo) {
			setTableNo(tableNo)
			uni.showToast({ title: '已带入桌号 ' + tableNo, icon: 'none' })
		}
		this.dining = getDining()
	},
	onShow() {
		this.dining = getDining()
		this.loadShop()
		this.loadMenu()
		this.loadCartSummary()
		this.loadFavorites()
	},
	onPullDownRefresh() {
		Promise.all([this.loadShop(), this.loadMenu()]).finally(() => uni.stopPullDownRefresh())
	},
	methods: {
		/** 店铺信息（走后端缓存） */
		async loadShop() {
			try {
				const body = await request({ url: '/api/shop/info', needAuth: false })
				this.shop = body.data || {}
			} catch (e) {
				console.warn('店铺信息加载失败:', e.message)
			}
		},
		/** 菜单列表（走后端缓存，售罄标记随列表下发） */
		async loadMenu() {
			try {
				const body = await request({ url: '/api/menu/list', needAuth: false })
				this.categories = body.data || []
				if (this.categories.length && !this.currentCateId) {
					this.currentCateId = this.categories[0].categoryId
				}
				this.$nextTick(() => this.calcCateOffsets())
				// T12：菜单缓存不含实时库存，另取库存并合并到列表项（不缓存、直接查库）
				this.loadStock()
			} catch (e) {
				uni.showToast({ title: e.message || '菜单加载失败', icon: 'none' })
			}
		},
		/** T12：批量拉取启用每日限量的菜品库存，合并 remainQty 到列表项 */
		async loadStock() {
			const limited = []
			this.categories.forEach(c => (c.goodsList || []).forEach(g => {
				if (g.dailyLimitEnabled) limited.push(g.id)
			}))
			if (!limited.length) return
			try {
				const body = await request({ url: '/api/goods/stock/batch?goodsIds=' + limited.join(','), needAuth: false })
				const stockMap = {}
				;(body.data || []).forEach(s => { stockMap[s.goodsId] = s })
				this.categories = this.categories.map(c => ({
					...c,
					goodsList: (c.goodsList || []).map(g => {
						const s = stockMap[g.id]
						if (!s) return g
						// 菜品级剩余：无规格用菜品级库存；有规格取各规格剩余之和
						const specRemain = (s.specs || []).reduce((sum, x) => sum + (x.remainQty || 0), 0)
						const remainQty = (s.specs && s.specs.length) ? specRemain : (s.remainQty === null ? undefined : s.remainQty)
						return { ...g, remainQty }
					})
				}))
			} catch (e) {
				console.warn('库存加载失败:', e.message)
			}
		},
		/** 计算各分类区块在滚动区内的 offsetTop（左侧高亮联动用） */
		calcCateOffsets() {
			const query = uni.createSelectorQuery().in(this)
			this.categories.forEach(c => {
				query.select('#cate-anchor-' + c.categoryId).boundingClientRect()
			})
			query.exec(rects => {
				if (!rects || !rects.length || rects[0] === null) return
				const sideTop = rects[0].top
				this.cateOffsets = rects.map(r => (r ? r.top - sideTop : 0))
			})
		},
		/** 右侧滚动时左侧分类高亮联动 */
		onGoodsScroll(e) {
			const scrollTop = e.detail.scrollTop
			let target = this.categories[0] && this.categories[0].categoryId
			for (let i = 0; i < this.cateOffsets.length; i++) {
				if (scrollTop + 20 >= this.cateOffsets[i]) {
					target = this.categories[i] && this.categories[i].categoryId
				}
			}
			if (target && target !== this.currentCateId) {
				this.currentCateId = target
			}
		},
		/** 左侧切分类 */
		switchCate(categoryId) {
			this.currentCateId = categoryId
		},
		/** 切换堂食/外卖 */
		switchDining(type) {
			this.dining = setDining(type)
			if (type === 'dine' && !this.dining.tableNo) {
				this.editTableNo()
			}
		},
		/** 手动输入桌号（堂食必绑桌号） */
		editTableNo() {
			uni.showModal({
				title: '输入桌号',
				editable: true,
				placeholderText: '如：5',
				success: (res) => {
					if (res.confirm) {
						const no = (res.content || '').trim()
						if (!no) {
							uni.showToast({ title: '桌号不能为空', icon: 'none' })
							return
						}
						this.dining = setTableNo(no)
						uni.showToast({ title: no + ' 号桌', icon: 'none' })
					}
				}
			})
		},
		/** 搜索（后端缓存内过滤） */
		async doSearch() {
			const kw = (this.keyword || '').trim()
			if (!kw) {
				// 空关键词：已在搜索结果态则退回菜单，否则提示用户先输入
				if (this.searchMode) {
					this.clearSearch()
				} else {
					uni.showToast({ title: '请输入菜品名称', icon: 'none' })
				}
				return
			}
			if (this.searching) return
			this.searching = true
			try {
				const body = await request({ url: '/api/goods/search?keyword=' + encodeURIComponent(kw), needAuth: false })
				this.searchResults = body.data || []
				this.searched = true
				this.searchMode = true
			} catch (e) {
				uni.showToast({ title: e.message || '搜索失败', icon: 'none' })
			} finally {
				this.searching = false
			}
		},
		clearSearch() {
			this.keyword = ''
			this.searchMode = false
			this.searchResults = []
			this.searched = false
		},
		/** 查看详情（售罄菜品也可查看；有规格/口味的菜品点"+"也进详情选择） */
		goDetail(goods) {
			uni.navigateTo({ url: '/pages/goods/detail?id=' + goods.id })
		},
		/**
		 * 加购按钮分流（T4）：
		 * 有规格或口味 -> 进详情弹窗选择；无 -> 直接加购（抛物线飞入动画）
		 */
		onAddTap(goods, e) {
			if (goods.hasSpec || goods.hasFlavor) {
				uni.navigateTo({ url: '/pages/goods/detail?id=' + goods.id + '&autoOpen=1' })
				return
			}
			this.addToCart(goods, e)
		},
		/** 直接加购（先确保登录态，再调后端；售罄/下架由后端二次校验拦截） */
		async addToCart(goods, e) {
			try {
				await ensureLogin()
				await request({
					url: '/api/cart/add',
					method: 'POST',
					data: { goodsId: goods.id, quantity: 1 }
				})
				uni.showToast({ title: '已加入购物车', icon: 'none' })
				this.flyToCart(e)
				this.loadCartSummary()
			} catch (err) {
				uni.showToast({ title: err.message || '加购失败', icon: 'none' })
			}
		},
		/** 抛物线飞入动画：从点击处飞向左下角购物车（纯 CSS transform 过渡） */
		flyToCart(e) {
			const sys = uni.getSystemInfoSync()
			const startX = (e && e.detail && e.detail.x) || sys.windowWidth / 2
			const startY = (e && e.detail && e.detail.y) || sys.windowHeight / 2
			const dot = {
				id: ++this.flySeq,
				x: startX - 12,
				y: startY - 12,
				tx: 24 - startX,
				ty: sys.windowHeight - 110 - startY,
				moved: false
			}
			this.flyDots.push(dot)
			setTimeout(() => {
				dot.moved = true
			}, 20)
			setTimeout(() => {
				this.flyDots = this.flyDots.filter(d => d.id !== dot.id)
			}, 700)
		},
		/** 购物车汇总（底部栏数量与合计） */
		async loadCartSummary() {
			try {
				const body = await request({ url: '/api/cart/list', needAuth: false })
				const items = body.data || []
				this.cartCount = items.filter(i => i.status === 'normal').reduce((s, i) => s + (i.quantity || 0), 0)
				this.cartAmount = items.filter(i => i.status === 'normal').reduce((s, i) => s + Number(i.subtotal || 0), 0)
			} catch (e) {
				// 未登录（游客浏览）：保持 0，加购时会先触发登录
				this.cartCount = 0
				this.cartAmount = 0
			}
		},
		/** 去购物车 */
		goCart() {
			uni.navigateTo({ url: '/pages/cart/cart' })
		},
		/** 我的订单（T6 订单中心） */
		goOrders() {
			uni.navigateTo({ url: '/pages/order/list' })
		},
		/** 我的收藏（T20 子项④） */
		goFavorites() {
			uni.navigateTo({ url: '/pages/favorite/list' })
		},
		/** T20 加载收藏 ID 集合（未登录静默跳过，❤ 均为空心） */
		async loadFavorites() {
			if (!getMember()) {
				this.favoriteIds = []
				return
			}
			try {
				const body = await request({ url: '/api/favorite/ids' })
				this.favoriteIds = body.data || []
			} catch (e) {
				this.favoriteIds = []
			}
		},
		/** T20 收藏/取消收藏（菜单卡片❤） */
		async onFavToggle(goods) {
			try {
				await ensureLogin()
				const body = await request({ url: '/api/favorite/toggle/' + goods.id, method: 'POST' })
				const favorited = !!(body.data && body.data.favorited)
				if (favorited) {
					if (!this.favoriteIds.includes(goods.id)) this.favoriteIds.push(goods.id)
					uni.showToast({ title: '已收藏', icon: 'none' })
				} else {
					this.favoriteIds = this.favoriteIds.filter(id => id !== goods.id)
					uni.showToast({ title: '已取消收藏', icon: 'none' })
				}
			} catch (e) {
				uni.showToast({ title: e.message || '操作失败', icon: 'none' })
			}
		},
		/** 商家信息页（T9：位置/地图/电话） */
		goShopInfo() {
			uni.navigateTo({ url: '/pages/shop/info' })
		},
		/** AI 点餐助手（二期 RAG） */
		goAiChat() {
			uni.navigateTo({ url: '/pages/ai/chat' })
		},
		/** 加购按钮（购物车在 T4 上线，本任务仅提示） */
		onAddTapFallback(goods) {
			uni.showToast({ title: '购物车功能即将上线～', icon: 'none' })
		},
		/** 拨打商家电话 */
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
	padding: 20rpx;
	padding-bottom: 160rpx;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
}

/* 加购飞入动画点（fixed 定位，transform 过渡到购物车位置） */
.fly-dot {
	position: fixed;
	z-index: 999;
	width: 44rpx;
	height: 44rpx;
	line-height: 44rpx;
	text-align: center;
	font-size: 34rpx;
	pointer-events: none;
	transition: transform 0.6s cubic-bezier(0.35, -0.4, 0.7, 1), opacity 0.6s ease-in;
}

/* 店铺信息条 */
.shop-bar {
	padding: 24rpx;

	.shop-name-row {
		display: flex;
		align-items: center;

		.emoji { font-size: 40rpx; margin-right: 12rpx; }

		.shop-name {
			font-size: 36rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.biz-tag {
			margin-left: auto;
			padding: 4rpx 20rpx;
			font-size: 22rpx;
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
		margin-top: 12rpx;
		font-size: 24rpx;
		color: $ap-text-light;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta {
		margin-top: 12rpx;
		display: flex;
		justify-content: space-between;
		font-size: 22rpx;
		color: $ap-text-light;
	}
}

/* 堂食/外卖切换 */
.dining-bar {
	margin-top: 20rpx;
	padding: 20rpx 24rpx;
	display: flex;
	align-items: center;

	.dining-switch {
		display: flex;
		background-color: $ap-bg;
		border-radius: $ap-radius-pill;
		padding: 6rpx;

		.opt {
			padding: 10rpx 28rpx;
			font-size: 26rpx;
			color: $ap-text-light;
			border-radius: $ap-radius-pill;

			&.active {
				color: #FFFFFF;
				background-color: $ap-primary;
				font-weight: 700;
			}
		}
	}

	.dining-tip {
		margin-left: auto;
		font-size: 24rpx;
		color: $ap-text-light;
	}
}

/* 我的订单入口 */
.order-entry {
	margin-top: 20rpx;
	padding: 22rpx 28rpx;
	display: flex;
	align-items: center;

	.entry-icon {
		font-size: 32rpx;
		margin-right: 14rpx;
	}

	.entry-text {
		font-size: 28rpx;
		font-weight: 700;
		color: $ap-text;
	}

	.entry-arrow {
		margin-left: auto;
		font-size: 36rpx;
		color: $ap-text-light;
	}
}

/* 搜索 */
.search-bar {
	margin-top: 20rpx;
	display: flex;
	align-items: center;

	.search-box {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		padding: 16rpx 24rpx;

		.search-icon { font-size: 28rpx; }

		.search-input {
			flex: 1;
			min-width: 0;
			margin-left: 12rpx;
			font-size: 26rpx;
			color: $ap-text;
		}

		.clear {
			padding: 0 8rpx;
			color: $ap-text-light;
			font-size: 28rpx;
		}
	}

	/* 搜索按钮（与主色一致，胶囊形，按压缩放） */
	.search-btn {
		flex-shrink: 0;
		margin-left: 16rpx;
		padding: 0 32rpx;
		height: 68rpx;
		line-height: 68rpx;
		text-align: center;
		font-size: 26rpx;
		font-weight: 700;
		color: #FFFFFF;
		background-color: $ap-primary;
		border-radius: $ap-radius-pill;
		box-shadow: $ap-shadow;
	}
}

/* 主体布局 */
.menu-wrap {
	flex: 1;
	display: flex;
	margin-top: 20rpx;
	height: 70vh;

	.cate-side {
		width: 176rpx;
		flex-shrink: 0;
		background-color: $ap-card;
		border-radius: $ap-radius;
		box-shadow: $ap-shadow;
		height: 100%;

		.cate-item {
			padding: 28rpx 16rpx;
			text-align: center;
			font-size: 26rpx;
			color: $ap-text-light;
			position: relative;

			&.active {
				color: $ap-primary;
				font-weight: 700;
				background-color: $ap-bg;
				border-radius: $ap-radius;

				&::before {
					content: '';
					position: absolute;
					left: 0;
					top: 50%;
					transform: translateY(-50%);
					width: 8rpx;
					height: 40rpx;
					border-radius: 4rpx;
					background-color: $ap-primary;
				}
			}
		}
	}

	.goods-side {
		flex: 1;
		height: 100%;
		padding: 0 0 0 20rpx;
		box-sizing: border-box;

		.cate-title {
			padding: 16rpx 8rpx;
			font-size: 28rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.empty-cate {
			padding: 32rpx;
			font-size: 24rpx;
			color: $ap-text-light;
			text-align: center;
		}
	}
}

/* 搜索结果 */
.search-result {
	flex: 1;
	margin-top: 20rpx;
	height: 70vh;
}

.bottom-placeholder {
	padding: 32rpx 0 60rpx;
	text-align: center;
	font-size: 22rpx;
	color: $ap-text-light;
}
</style>
