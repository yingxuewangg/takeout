<template>
	<view class="page">
		<!-- 履约方式切换（与首页联动，存本地） -->
		<view class="dining-card ap-card">
			<view class="dining-switch">
				<view :class="['opt ap-press', deliveryType === 1 ? 'active' : '']" @tap="switchType(1)">🍜 堂食</view>
				<view :class="['opt ap-press', deliveryType === 2 ? 'active' : '']" @tap="switchType(2)">🛵 外卖配送</view>
			</view>
			<!-- 堂食：桌号 -->
			<view v-if="deliveryType === 1" class="table-row" @tap="editTableNo">
				<text class="label">桌号</text>
				<text :class="['value', tableNo ? '' : 'placeholder']">{{ tableNo ? tableNo + ' 号桌' : '点击选择' }} ✏️</text>
			</view>
			<!-- 外卖：收货地址（点击打开地址选择弹层） -->
			<view v-else class="addr-row">
				<view class="addr-main" @tap="showAddressPicker = true">
					<template v-if="selectedAddress">
						<view class="addr-info">
							<view class="addr-line1">
								<text class="addr-name">{{ selectedAddress.contactName }}</text>
								<text class="addr-phone">{{ selectedAddress.contactPhone }}</text>
								<text v-if="selectedAddress.isDefault === 1" class="default-tag">默认</text>
							</view>
							<text class="addr-detail">{{ addressFull(selectedAddress) }}</text>
						</view>
						<text class="arrow">›</text>
					</template>
					<template v-else>
						<text class="placeholder">请选择收货地址</text>
						<text class="arrow">›</text>
					</template>
				</view>
				<!-- T20 地址管理独立页入口 -->
				<text class="manage-link" @tap.stop="openAddressManage">管理</text>
			</view>
		</view>

		<!-- 菜品明细 -->
		<view class="items-card ap-card">
			<view class="card-title">菜品清单</view>
			<view v-for="item in checkout.items" :key="item.id" :class="['item-row', item.status !== 'normal' ? 'invalid' : '']">
				<text class="item-name">{{ item.goodsName }}</text>
				<text class="item-spec" v-if="item.specName || (item.flavors && item.flavors.length)">{{ specText(item) }}</text>
				<text v-if="item.status === 'soldout'" class="invalid-tag">已售罄</text>
				<text v-if="item.status === 'offshelf'" class="invalid-tag">已下架</text>
				<view class="item-right">
					<text class="item-qty">x{{ item.quantity }}</text>
					<text class="item-subtotal">¥{{ item.subtotal }}</text>
				</view>
			</view>
		</view>

		<!-- 备注 / 预留电话 -->
		<view class="extra-card ap-card">
			<view class="form-row">
				<text class="label">预留电话</text>
				<input class="input" v-model="contactPhone" placeholder="方便商家联系你" type="number" maxlength="11" />
			</view>
			<view class="form-row">
				<text class="label">订单备注</text>
				<input class="input" v-model="remark" placeholder="口味偏好、忌口等（选填）" maxlength="200" />
			</view>
			<!-- T20 备注快捷模板：点选追加，再点移除，可与自定义内容混排 -->
			<view class="remark-tags">
				<text
					v-for="t in remarkTemplates"
					:key="t"
					:class="['tag', 'ap-press', remark.includes(t) ? 'active' : '']"
					@tap="toggleRemarkTag(t)"
				>{{ t }}</text>
			</view>
		</view>

		<!-- 金额汇总 -->
		<view class="summary-card ap-card">
			<view class="sum-row">
				<text>菜品合计</text>
				<text>¥{{ fix(checkout.goodsAmount) }}</text>
			</view>
			<view class="sum-row" v-if="deliveryType === 2">
				<text>配送费</text>
				<text>¥{{ fix(checkout.deliveryFee) }}</text>
			</view>
			<view class="sum-row total">
				<text>应付</text>
				<text class="total-price">¥{{ fix(checkout.totalAmount) }}</text>
			</view>
		</view>

		<!-- 底部提交栏 -->
		<view class="submit-bar">
			<view class="submit-left">
				<text class="submit-amount">¥{{ fix(checkout.totalAmount) }}</text>
				<text class="submit-tip" v-if="tipText">{{ tipText }}</text>
			</view>
			<view :class="['submit-btn ap-press', canSubmit ? '' : 'disabled']" @tap="submitOrder">
				提交订单
			</view>
		</view>

		<!-- 地址选择弹层 -->
		<wd-popup v-model="showAddressPicker" position="bottom" :safe-area-inset-bottom="true">
			<view class="address-picker">
				<view class="picker-head">
					<text class="picker-title">选择收货地址</text>
					<text class="close" @tap="showAddressPicker = false">✕</text>
				</view>
				<scroll-view scroll-y class="picker-body">
					<view
						v-for="a in addressList"
						:key="a.id"
						:class="['addr-item', selectedAddress && selectedAddress.id === a.id ? 'active' : '']"
						@tap="chooseAddress(a)"
					>
						<view class="addr-line1">
							<text class="addr-name">{{ a.contactName }}</text>
							<text class="addr-phone">{{ a.contactPhone }}</text>
							<text v-if="a.isDefault === 1" class="default-tag">默认</text>
						</view>
						<text class="addr-detail">{{ addressFull(a) }}</text>
						<view class="addr-ops" @tap.stop>
							<text class="op" @tap="setDefault(a)">{{ a.isDefault === 1 ? '' : '设为默认' }}</text>
							<text class="op" @tap="editAddress(a)">编辑</text>
							<text class="op danger" @tap="deleteAddress(a)">删除</text>
						</view>
					</view>
					<view v-if="!addressList.length" class="ap-empty">
						<text class="em">🏠</text>
						还没有收货地址
					</view>
				</scroll-view>
				<view class="picker-foot">
					<view class="add-btn ap-press" @tap="openAddressForm()">＋ 新增地址</view>
				</view>
			</view>
		</wd-popup>

		<!-- 新增/编辑地址弹层 -->
		<wd-popup v-model="showAddressForm" position="bottom" :safe-area-inset-bottom="true">
			<view class="address-form">
				<view class="picker-head">
					<text class="picker-title">{{ form.id ? '编辑地址' : '新增地址' }}</text>
					<text class="close" @tap="showAddressForm = false">✕</text>
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
				<view class="picker-foot">
					<view class="add-btn ap-press" @tap="saveAddress">保存</view>
				</view>
			</view>
		</wd-popup>
	</view>
</template>

<script>
import { request, getMember } from '@/utils/request'
import { getDining, setDining, setTableNo } from '@/utils/dining'

export default {
	data() {
		return {
			deliveryType: 1, // 1堂食 2外卖（与首页选择联动）
			tableNo: '',
			checkout: {}, // /api/cart/checkout 结算预览
			contactPhone: '',
			remark: '',
			// T20 备注快捷模板
			remarkTemplates: ['少辣', '不要香菜', '不要洋葱', '多加餐具', '打包'],
			addressList: [],
			selectedAddress: null,
			showAddressPicker: false,
			showAddressForm: false,
			form: {}, // 地址表单
			submitting: false // 防连点
		}
	},
	computed: {
		canSubmit() {
			const c = this.checkout
			if (!c || !c.items || !c.items.length) return false
			return c.canSubmit === true && (this.deliveryType === 2 ? !!this.selectedAddress : true)
		},
		tipText() {
			const c = this.checkout
			if (!c || !c.items) return ''
			if (c.businessClosed) return c.closedTip || '店铺已打烊'
			if (c.hasInvalid) return '购物车含失效菜品，请先处理'
			if (this.deliveryType === 1 && !this.tableNo) return '请选择桌号'
			if (this.deliveryType === 2 && !this.selectedAddress) return '请选择收货地址'
			if (Number(c.amountShort) > 0) return `还差 ¥${c.amountShort} 起送`
			return ''
		}
	},
	onLoad() {
		const dining = getDining()
		this.deliveryType = dining.type === 'takeout' ? 2 : 1
		this.tableNo = dining.tableNo || ''
		const member = getMember()
		if (member && member.phone) {
			this.contactPhone = member.phone
		}
	},
	onShow() {
		this.refresh()
	},
	methods: {
		/** 刷新结算预览与地址 */
		async refresh() {
			try {
				const params = 'deliveryType=' + this.deliveryType + (this.tableNo ? '&tableNo=' + encodeURIComponent(this.tableNo) : '')
				const body = await request({ url: '/api/cart/checkout?' + params })
				this.checkout = body.data || {}
				if (this.deliveryType === 2) {
					this.selectedAddress = this.checkout.defaultAddress || null
					await this.loadAddressList()
				}
			} catch (e) {
				uni.showToast({ title: e.message || '结算信息加载失败', icon: 'none' })
				setTimeout(() => uni.navigateBack(), 1200)
			}
		},
		async loadAddressList() {
			try {
				const body = await request({ url: '/api/address/list' })
				this.addressList = body.data || []
			} catch (e) {
				console.warn('地址加载失败:', e.message)
			}
		},
		fix(v) {
			return Number(v || 0).toFixed(2)
		},
		addressFull(a) {
			return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
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
		/** 切换履约方式（与首页存储联动） */
		switchType(type) {
			if (type === this.deliveryType) return
			if (type === 1) {
				const dining = getDining()
				this.tableNo = dining.tableNo || ''
				this.selectedAddress = null
			} else {
				this.tableNo = ''
			}
			this.deliveryType = type
			this.refresh()
		},
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
						this.tableNo = no
						setTableNo(no)
						this.refresh()
					}
				}
			})
		},
		chooseAddress(a) {
			this.selectedAddress = a
			this.showAddressPicker = false
		},
		/** T20 备注快捷模板：未选则追加到备注尾部，已选则从备注中移除 */
		toggleRemarkTag(t) {
			if (this.remark.includes(t)) {
				this.remark = this.remark
					.replace(t, '')
					.replace('，，', '，')
					.replace(/^[，、\s]+|[，、\s]+$/g, '')
			} else {
				this.remark = this.remark ? this.remark.replace(/[，、\s]*$/, '，') + t : t
				if (this.remark.length > 200) {
					this.remark = this.remark.slice(0, 200)
					uni.showToast({ title: '备注最多 200 字', icon: 'none' })
				}
			}
		},
		/** T20 跳转地址管理页；返回时通过事件通道接收回选地址 */
		openAddressManage() {
			uni.navigateTo({
				url: '/pages/address/list',
				events: {
					pickAddress: (a) => {
						this.selectedAddress = a
					}
				}
			})
		},
		openAddressForm(address) {
			this.form = address
				? { ...address }
				: { contactName: '', contactPhone: '', province: '', city: '', district: '', detail: '', isDefault: 0 }
			this.showAddressForm = true
		},
		editAddress(a) {
			this.openAddressForm(a)
		},
		async saveAddress() {
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
				this.showAddressForm = false
				await this.loadAddressList()
				// 新增/编辑默认地址后刷新预览里的默认地址
				this.refresh()
			} catch (e) {
				uni.showToast({ title: e.message || '保存失败', icon: 'none' })
			}
		},
		async setDefault(a) {
			try {
				await request({ url: '/api/address/default/' + a.id, method: 'PUT' })
				await this.loadAddressList()
				if (this.selectedAddress && this.selectedAddress.id === a.id) {
					this.selectedAddress.isDefault = 1
				}
			} catch (e) {
				uni.showToast({ title: e.message || '设置失败', icon: 'none' })
			}
		},
		deleteAddress(a) {
			uni.showModal({
				title: '提示',
				content: '删除该地址？',
				success: async (res) => {
					if (!res.confirm) return
					try {
						await request({ url: '/api/address/' + a.id, method: 'DELETE' })
						if (this.selectedAddress && this.selectedAddress.id === a.id) {
							this.selectedAddress = null
						}
						await this.loadAddressList()
						this.refresh()
					} catch (e) {
						uni.showToast({ title: e.message || '删除失败', icon: 'none' })
					}
				}
			})
		},
		/** 提交订单：创建订单（防重锁/打烊/失效项/起送价等由后端二次校验）→ 跳收银台 */
		async submitOrder() {
			if (!this.canSubmit) {
				uni.showToast({ title: this.tipText || '暂时不能提交', icon: 'none' })
				return
			}
			// 防止连点重复提交（后端另有 takeout:order:lock 防重锁兜底）
			if (this.submitting) return
			this.submitting = true
			try {
				const body = await request({
					url: '/api/order/create',
					method: 'POST',
					data: {
						deliveryType: this.deliveryType,
						tableNo: this.deliveryType === 1 ? this.tableNo : null,
						addressId: this.deliveryType === 2 && this.selectedAddress ? this.selectedAddress.id : null,
						contactPhone: this.contactPhone || null,
						remark: this.remark || null
					}
				})
				// 成功后购物车已被后端清空；redirectTo 收银台（防返回键重复下单）
				uni.redirectTo({ url: '/pages/pay/cashier?id=' + body.data.orderId })
			} catch (e) {
				uni.showToast({ title: e.message || '下单失败', icon: 'none' })
				// 店铺状态/起送价可能变化，刷新预览
				this.refresh()
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
	padding: 20rpx 20rpx 170rpx;
	box-sizing: border-box;
}

.dining-card {
	padding: 24rpx;

	.dining-switch {
		display: flex;
		background-color: $ap-bg;
		border-radius: $ap-radius-pill;
		padding: 6rpx;

		.opt {
			flex: 1;
			text-align: center;
			padding: 12rpx 0;
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

	.table-row,
	.addr-row {
		margin-top: 20rpx;
		display: flex;
		align-items: center;

		.label {
			font-size: 26rpx;
			color: $ap-text-light;
			margin-right: 20rpx;
		}

		.value {
			font-size: 28rpx;
			color: $ap-text;
			font-weight: 700;
		}

		.placeholder {
			color: $ap-text-light;
			font-weight: 400;
		}

		.arrow {
			margin-left: auto;
			font-size: 36rpx;
			color: $ap-text-light;
		}

		/* T20 管理地址入口 */
		.addr-main {
			flex: 1;
			min-width: 0;
			display: flex;
			align-items: center;

			.addr-info {
				flex: 1;
				min-width: 0;
			}
		}

		.manage-link {
			margin-left: 16rpx;
			flex-shrink: 0;
			font-size: 24rpx;
			color: $ap-primary;
			border: 1rpx solid $ap-primary;
			border-radius: $ap-radius-pill;
			padding: 4rpx 20rpx;
		}

		.addr-info {
			flex: 1;
			min-width: 0;

			.addr-line1 {
				display: flex;
				align-items: center;

				.addr-name {
					font-size: 28rpx;
					font-weight: 700;
					color: $ap-text;
				}

				.addr-phone {
					margin-left: 16rpx;
					font-size: 24rpx;
					color: $ap-text-light;
				}

				.default-tag {
					margin-left: 12rpx;
					padding: 2rpx 12rpx;
					font-size: 18rpx;
					color: $ap-primary;
					background-color: $ap-bg;
					border-radius: $ap-radius-pill;
				}
			}

			.addr-detail {
				display: block;
				margin-top: 6rpx;
				font-size: 24rpx;
				color: $ap-text-light;
			}
		}
	}
}

.items-card,
.extra-card,
.summary-card {
	margin-top: 20rpx;
	padding: 24rpx;

	.card-title {
		font-size: 28rpx;
		font-weight: 700;
		color: $ap-text;
		margin-bottom: 12rpx;
	}

	.item-row {
		display: flex;
		align-items: center;
		padding: 14rpx 0;
		flex-wrap: wrap;

		.item-name {
			font-size: 26rpx;
			color: $ap-text;
		}

		.item-spec {
			margin-left: 12rpx;
			font-size: 22rpx;
			color: $ap-text-light;
			max-width: 260rpx;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		.invalid-tag {
			margin-left: 8rpx;
			padding: 2rpx 10rpx;
			font-size: 18rpx;
			color: #FFFFFF;
			background-color: $ap-text-light;
			border-radius: $ap-radius-pill;
		}

		.item-right {
			margin-left: auto;

			.item-qty {
				font-size: 24rpx;
				color: $ap-text-light;
				margin-right: 16rpx;
			}

			.item-subtotal {
				font-size: 26rpx;
				font-weight: 700;
				color: $ap-primary;
			}
		}

		&.invalid {
			opacity: 0.5;
		}
	}
}

/* T20 备注快捷标签 */
.remark-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 4rpx;
	padding: 0 0 8rpx 150rpx;

	.tag {
		font-size: 22rpx;
		color: $ap-text-light;
		background-color: $ap-bg;
		border-radius: $ap-radius-pill;
		padding: 6rpx 20rpx;

		&.active {
			color: #ffffff;
			background-color: $ap-primary;
		}
	}
}

.form-row {
	display: flex;
	align-items: center;
	padding: 14rpx 0;

	.label {
		width: 150rpx;
		font-size: 26rpx;
		color: $ap-text-light;
		flex-shrink: 0;	}

	.input {
		flex: 1;
		font-size: 26rpx;
		color: $ap-text;
	}

	.region-inputs {
		flex: 1;
		display: flex;
		gap: 12rpx;

		.region {
			flex: 1;
			min-width: 0;
		}
	}
}

.summary-card {
	.sum-row {
		display: flex;
		justify-content: space-between;
		padding: 10rpx 0;
		font-size: 26rpx;
		color: $ap-text;

		&.total {
			padding-top: 20rpx;
			border-top: 1rpx solid $ap-bg;
			font-weight: 700;

			.total-price {
				color: $ap-primary;
				font-size: 34rpx;
			}
		}
	}
}

/* 底部提交栏 */
.submit-bar {
	position: fixed;
	left: 20rpx;
	right: 20rpx;
	bottom: calc(20rpx + env(safe-area-inset-bottom));
	background-color: $ap-card;
	border-radius: $ap-radius-pill;
	box-shadow: $ap-shadow-deep;
	display: flex;
	align-items: center;
	padding: 16rpx 16rpx 16rpx 32rpx;

	.submit-left {
		display: flex;
		flex-direction: column;

		.submit-amount {
			font-size: 34rpx;
			font-weight: 700;
			color: $ap-primary;
		}

		.submit-tip {
			font-size: 20rpx;
			color: $ap-text-light;
		}
	}

	.submit-btn {
		margin-left: auto;
		height: 84rpx;
		line-height: 84rpx;
		padding: 0 56rpx;
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

/* 地址选择弹层 */
.address-picker,
.address-form {
	padding: 32rpx;

	.picker-head {
		display: flex;
		align-items: center;

		.picker-title {
			font-size: 30rpx;
			font-weight: 700;
			color: $ap-text;
		}

		.close {
			margin-left: auto;
			font-size: 32rpx;
			color: $ap-text-light;
		}
	}

	.picker-body {
		max-height: 50vh;
		margin-top: 20rpx;

		.addr-item {
			padding: 20rpx;
			border-radius: $ap-radius;
			background-color: $ap-bg;
			margin-bottom: 16rpx;

			&.active {
				box-shadow: 0 0 0 2rpx $ap-primary inset;
			}

			.addr-line1 {
				display: flex;
				align-items: center;

				.addr-name {
					font-size: 28rpx;
					font-weight: 700;
					color: $ap-text;
				}

				.addr-phone {
					margin-left: 16rpx;
					font-size: 24rpx;
					color: $ap-text-light;
				}

				.default-tag {
					margin-left: 12rpx;
					padding: 2rpx 12rpx;
					font-size: 18rpx;
					color: $ap-primary;
					background-color: $ap-card;
					border-radius: $ap-radius-pill;
				}
			}

			.addr-detail {
				display: block;
				margin-top: 6rpx;
				font-size: 24rpx;
				color: $ap-text-light;
			}

			.addr-ops {
				margin-top: 12rpx;
				display: flex;
				justify-content: flex-end;
				gap: 28rpx;

				.op {
					font-size: 24rpx;
					color: $ap-text-light;

					&.danger {
						color: #E57373;
					}
				}
			}
		}
	}

	.picker-foot {
		padding-top: 20rpx;

		.add-btn {
			height: 84rpx;
			line-height: 84rpx;
			text-align: center;
			font-size: 28rpx;
			font-weight: 700;
			color: #FFFFFF;
			background-color: $ap-primary;
			border-radius: $ap-radius-pill;
		}
	}

	.form-body {
		margin-top: 12rpx;
	}
}
</style>
