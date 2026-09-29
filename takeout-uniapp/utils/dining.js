/**
 * 堂食/外卖履约方式存取（方案 3.4 履约方式）。
 * - 扫码进入 scene 带 tableNo 时自动切堂食并填桌号
 * - 同桌多次下单每单独立，不做并单
 * - T4 确认订单页读取本存储生成订单
 */

const DINING_KEY = 'takeout_dining'

/** @returns {{type:'dine'|'takeout', tableNo:string}} */
export function getDining() {
	const d = uni.getStorageSync(DINING_KEY)
	if (d && (d.type === 'dine' || d.type === 'takeout')) {
		return d
	}
	return { type: 'dine', tableNo: '' }
}

export function setDining(type) {
	const d = getDining()
	d.type = type === 'takeout' ? 'takeout' : 'dine'
	uni.setStorageSync(DINING_KEY, d)
	return d
}

export function setTableNo(tableNo) {
	const d = getDining()
	d.tableNo = String(tableNo || '').trim()
	d.type = 'dine'
	uni.setStorageSync(DINING_KEY, d)
	return d
}

/**
 * 解析扫码进入的 scene 参数（支持 tableNo=5 与直接 "5" 两种形式）
 * @returns 桌号字符串或空串
 */
export function parseSceneTableNo(scene) {
	if (!scene) return ''
	let raw = ''
	try {
		raw = decodeURIComponent(scene)
	} catch (e) {
		raw = scene
	}
	if (!raw) return ''
	if (raw.indexOf('=') > -1) {
		const params = {}
		raw.split('&').forEach(pair => {
			const [k, v] = pair.split('=')
			if (k) params[k] = v || ''
		})
		return (params.tableNo || '').trim()
	}
	return raw.trim()
}

export default { getDining, setDining, setTableNo, parseSceneTableNo }
