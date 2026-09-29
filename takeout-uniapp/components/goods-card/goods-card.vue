<template>
	<!-- 注意：自定义事件名用 open 而非 tap——tap 与微信原生事件同名，新版基础库下原生冒泡事件会先于
	     triggerEvent 到达宿主，导致父级拿到原生事件对象、goods.id 丢失（2026-09-29 实证修复） -->
	<view class="goods-card ap-card" :class="{ soldout: goods.soldOut === '1' }" @tap="$emit('open', goods)">
		<view class="thumb-wrap">
			<image class="thumb" :src="goods.image || '/static/images/placeholder.png'" mode="aspectFill" />
			<!-- T20 收藏❤（favorited 由父级传入；点击冒泡阻止避免触发卡片跳详情） -->
			<text class="fav-btn ap-press" @tap.stop="$emit('fav', goods)">{{ favorited ? '❤️' : '🤍' }}</text>
		</view>
		<view class="info">
			<view class="name-row">
				<text class="name">{{ goods.name }}</text>
				<text v-if="goods.soldOut === '1'" class="hot-tag">已售罄</text>
			</view>
			<text class="desc">{{ goods.description || '阿婆用心制作' }}</text>
			<!-- T12：每日限量剩余库存（未启用限量或未取到时不展示） -->
			<text v-if="goods.dailyLimitEnabled && goods.remainQty !== null && goods.remainQty !== undefined" class="stock-text" :class="{ low: goods.remainQty <= 3 }">
				今日剩余 {{ goods.remainQty }} 份
			</text>
			<view class="bottom">
				<view class="price-row">
					<text class="symbol">¥</text>
					<text class="price">{{ goods.price }}</text>
				</view>
				<!-- 售罄：置灰不可加购（可查看详情）；正常：显示加购按钮位（T4 接入购物车） -->
				<view v-if="goods.soldOut === '1'" class="soldout-mark">售罄</view>
				<view v-else class="add-btn ap-press" @tap.stop="$emit('add', goods, $event)">＋</view>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	name: 'goods-card',
	emits: ['open', 'add', 'fav'],
	props: {
		// 菜单项数据：{id,name,image,price,description,soldOut}
		goods: {
			type: Object,
			default: () => ({})
		},
		// T20 是否已收藏（父级传入收藏状态，组件只展示与冒泡事件）
		favorited: {
			type: Boolean,
			default: false
		}
	}
}
</script>

<style lang="scss" scoped>
.goods-card {
	display: flex;
	padding: 20rpx;
	margin-bottom: 20rpx;

	.thumb-wrap {
		position: relative;
		flex-shrink: 0;

		.thumb {
			width: 160rpx;
			height: 160rpx;
			border-radius: 16rpx;
			background-color: $ap-bg;
		}

		/* T20 收藏❤按钮（图右上角悬浮） */
		.fav-btn {
			position: absolute;
			top: 6rpx;
			right: 6rpx;
			font-size: 30rpx;
			line-height: 1;
			padding: 4rpx;
			text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
		}
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
				color: $ap-text;
				overflow: hidden;
				text-overflow: ellipsis;
				white-space: nowrap;
			}

			.hot-tag {
				margin-left: 12rpx;
				padding: 2rpx 14rpx;
				font-size: 20rpx;
				color: #FFFFFF;
				background-color: $ap-text-light;
				border-radius: $ap-radius-pill;
				flex-shrink: 0;
			}
		}

		.desc {
			margin-top: 8rpx;
			font-size: 24rpx;
			color: $ap-text-light;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		/* T12 每日限量剩余库存 */
		.stock-text {
			margin-top: 6rpx;
			font-size: 22rpx;
			color: $ap-green;
			font-weight: 700;

			&.low {
				color: $ap-primary;
			}
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
					font-size: 34rpx;
					font-weight: 700;
					color: $ap-primary;
				}
			}

			/* 售罄标记：置灰、不可加购 */
			.soldout-mark {
				padding: 6rpx 24rpx;
				font-size: 24rpx;
				color: $ap-text-light;
				background-color: $ap-bg;
				border-radius: $ap-radius-pill;
			}

			/* 加购按钮（T4 起接入购物车逻辑；当前任务点击仅提示） */
			.add-btn {
				width: 56rpx;
				height: 56rpx;
				line-height: 52rpx;
				text-align: center;
				font-size: 40rpx;
				color: #FFFFFF;
				background-color: $ap-primary;
				border-radius: $ap-radius-pill;
				box-shadow: $ap-shadow-deep;
			}
		}
	}

	/* 售罄卡片整体置灰 */
	&.soldout {
		opacity: 0.55;

		.thumb {
			filter: grayscale(0.8);
		}
	}
}
</style>
