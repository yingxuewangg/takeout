<template>
	<view class="page">
		<scroll-view scroll-y class="list">
			<view v-for="a in addressList" :key="a.id" class="addr-card">
				<view class="addr-main" @tap="chooseAndBack(a)">
					<view class="addr-line1">
						<text class="addr-name">{{ a.contactName }}</text>
						<text class="addr-phone">{{ a.contactPhone }}</text>
						<text v-if="a.isDefault === 1" class="default-tag">默认</text>
					</view>
					<text class="addr-detail">{{ addressFull(a) }}</text>
				</view>
				<view class="addr-ops">
					<text v-if="a.isDefault !== 1" class="op" @tap="setDefault(a)">设为默认</text>
					<text class="op" @tap="openForm(a)">编辑</text>
					<text class="op danger" @tap="removeAddress(a)">删除</text>
				</view>
			</view>
			<view v-if="!addressList.length" class="ap-empty">
				<text class="em">🏠</text>
				还没有收货地址，点下方按钮新增
			</view>
		</scroll-view>

		<view class="foot">
			<view class="add-btn ap-press" @tap="openForm()">＋ 新增地址</view>
		</view>

		<!-- 新增/编辑地址弹层 -->
		<wd-popup v-model="showForm" position="bottom" :safe-area-inset-bottom="true">
			<view class="address-form">
				<view class="form-head">
					<text class="form-title">{{ form.id ? '编辑地址' : '新增地址' }}</text>
					<text class="close" @tap="showForm = false">✕</text>
				</view>
				<view class="form-body">
					<view class="form-row">
						<text class="label">联系人</text>
						<input class="input" v-model="form.contactName" placeholder="收货人姓名" maxlength="32" />
					</view>
					<view class="form-row">
						<text class="label">手机号</text>
						<input class="input" v-model="form.contactPhone" placeholder="11位手机号" type="number" maxlength="11" />
					</view>
					<view class="form-row region-row">
						<text class="label">所在地区</text>
						<view class="region-inputs">
							<input class="input region" v-model="form.province" placeholder="省" />
							<input class="input region" v-model="form.city" placeholder="市" />
							<input class="input region" v-model="form.district" placeholder="区" />
						</view>
					</view>
					<view class="form-row">
						<text class="label">详细地址</text>
						<input class="input" v-model="form.detail" placeholder="街道、楼栋、门牌号" maxlength="255" />
					</view>
					<view class="form-row">
						<text class="label">设为默认</text>
						<switch :checked="form.isDefault === 1" color="#FF7043" @change="form.isDefault = $event.detail.value ? 1 : 0" />
					</view>
				</view>
				<view class="foot">
					<view class="add-btn ap-press" @tap="saveForm">保存</view>
				</view>
			</view>
		</wd-popup>
	</view>
</template>

<script>
import { request } from '@/utils/request'

export default {
	data() {
		return {
			addressList: [],
			showForm: false,
			form: {}
		}
	},
	onShow() {
		this.loadList()
	},
	methods: {
		async loadList() {
			try {
				const body = await request({ url: '/api/address/list' })
				this.addressList = body.data || []
			} catch (e) {
				uni.showToast({ title: e.message || '地址加载失败', icon: 'none' })
			}
		},
		addressFull(a) {
			return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
		},
		/** 从结算页「管理地址」进入时，点地址直接回选 */
		chooseAndBack(a) {
			const pages = getCurrentPages()
			if (pages.length > 1 && pages[pages.length - 2].route === 'pages/checkout/checkout') {
				const eventChannel = this.getOpenerEventChannel && this.getOpenerEventChannel()
				if (eventChannel && eventChannel.emit) {
					eventChannel.emit('pickAddress', a)
				}
				uni.navigateBack()
			}
		},
		openForm(address) {
			this.form = address
				? { ...address }
				: { contactName: '', contactPhone: '', province: '', city: '', district: '', detail: '', isDefault: 0 }
			this.showForm = true
		},
		async saveForm() {
			const f = this.form
			if (!f.contactName || !f.contactPhone || !f.detail) {
				uni.showToast({ title: '请填写联系人/手机号/详细地址', icon: 'none' })
				return
			}
			try {
				if (f.id) {
					await request({ url: '/api/address', method: 'PUT', data: f })
				} else {
					await request({ url: '/api/address', method: 'POST', data: f })
				}
				this.showForm = false
				await this.loadList()
			} catch (e) {
				uni.showToast({ title: e.message || '保存失败', icon: 'none' })
			}
		},
		async setDefault(a) {
			try {
				await request({ url: '/api/address/default/' + a.id, method: 'PUT' })
				await this.loadList()
			} catch (e) {
				uni.showToast({ title: e.message || '设置失败', icon: 'none' })
			}
		},
		removeAddress(a) {
			uni.showModal({
				title: '提示',
				content: '删除该地址？',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/address/' + a.id, method: 'DELETE' })
						await this.loadList()
					} catch (e) {
						uni.showToast({ title: e.message || '删除失败', icon: 'none' })
					}
				}
			})
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background-color: #fff6ec;
	display: flex;
	flex-direction: column;
	padding: 24rpx 24rpx 160rpx;
	box-sizing: border-box;
}

.list {
	flex: 1;
}

.addr-card {
	background-color: #ffffff;
	border-radius: 24rpx;
	padding: 24rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 4rpx 16rpx rgba(255, 112, 67, 0.08);
}

.addr-line1 {
	display: flex;
	align-items: center;

	.addr-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #4a3728;
	}

	.addr-phone {
		margin-left: 16rpx;
		font-size: 26rpx;
		color: #a89888;
	}

	.default-tag {
		margin-left: 16rpx;
		font-size: 20rpx;
		color: #ff7043;
		border: 1rpx solid #ff7043;
		border-radius: 999rpx;
		padding: 2rpx 12rpx;
	}
}

.addr-detail {
	display: block;
	margin-top: 10rpx;
	font-size: 26rpx;
	color: #4a3728;
	line-height: 1.5;
}

.addr-ops {
	display: flex;
	justify-content: flex-end;
	margin-top: 16rpx;

	.op {
		margin-left: 32rpx;
		font-size: 26rpx;
		color: #a89888;
	}

	.op.danger {
		color: #f56c6c;
	}
}

.ap-empty {
	padding: 120rpx 0;
	text-align: center;
	font-size: 26rpx;
	color: #a89888;

	.em {
		display: block;
		font-size: 64rpx;
		margin-bottom: 16rpx;
	}
}

.foot {
	position: fixed;
	left: 24rpx;
	right: 24rpx;
	bottom: 32rpx;

	.add-btn {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		border-radius: 999rpx;
		color: #ffffff;
		font-size: 30rpx;
		font-weight: 600;
		background-color: #ff7043;
		box-shadow: 0 8rpx 20rpx rgba(255, 112, 67, 0.3);
	}
}

.address-form {
	background-color: #ffffff;
	border-radius: 32rpx 32rpx 0 0;
	padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));

	.form-head {
		display: flex;
		justify-content: space-between;
		align-items: center;

		.form-title {
			font-size: 32rpx;
			font-weight: 600;
			color: #4a3728;
		}

		.close {
			font-size: 32rpx;
			color: #a89888;
			padding: 8rpx;
		}
	}

	.form-body {
		margin-top: 24rpx;
	}

	.form-row {
		display: flex;
		align-items: center;
		margin-bottom: 24rpx;

		.label {
			width: 160rpx;
			font-size: 28rpx;
			color: #4a3728;
			flex-shrink: 0;
		}

		.input {
			flex: 1;
			height: 72rpx;
			padding: 0 20rpx;
			border-radius: 16rpx;
			background-color: #fff6ec;
			font-size: 28rpx;
			color: #4a3728;
		}

		.region-inputs {
			flex: 1;
			display: flex;
			gap: 12rpx;

			.region {
				flex: 1;
				height: 72rpx;
				padding: 0 12rpx;
				border-radius: 16rpx;
				background-color: #fff6ec;
				font-size: 28rpx;
				color: #4a3728;
				text-align: center;
			}
		}
	}
}
</style>
