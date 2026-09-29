<script>
	import { ensureLogin } from './utils/request'

	export default {
		onLaunch: function() {
			console.log('App Launch')
			// 启动即静默微信登录（游客仍可浏览菜单，登录态供下单等操作使用）
			ensureLogin().catch(err => {
				console.warn('静默登录失败（游客模式浏览）:', err)
			})
		},
		onShow: function() {
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style lang="scss">
	/* 全站公共样式：可爱风主题（方案附录 C） */
	page {
		/* wot-design-uni 主题变量覆盖（不逐个组件覆写样式） */
		--wot-color-theme: #FF7043;
		--wot-color-success: #7FB77E;
		--wot-color-warning: #FFC53D;
		--wot-button-border-radius: 999rpx;

		background-color: $ap-bg;
		color: $ap-text;
		font-size: 28rpx;
	}

	/* 白卡片（浮在奶油底色上） */
	.ap-card {
		background-color: $ap-card;
		border-radius: $ap-radius;
		box-shadow: $ap-shadow;
	}

	/* 价格文字 */
	.ap-price {
		color: $ap-primary;
		font-weight: 700;
	}

	/* 空状态 */
	.ap-empty {
		padding: 120rpx 0;
		text-align: center;
		color: $ap-text-light;
		font-size: 26rpx;
		.em {
			font-size: 72rpx;
			display: block;
			margin-bottom: 16rpx;
		}
	}

	/* 通用按压反馈（微交互：点击缩放回弹） */
	.ap-press {
		@include ap-press;
	}
</style>
