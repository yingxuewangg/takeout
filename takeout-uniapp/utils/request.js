/**
 * 小程序端统一请求封装。
 * - BASE_URL 指向后端服务；真机调试请改为局域网 IP，并在微信开发者工具勾选"不校验合法域名"
 * - 自动携带 Authorization: Bearer {token}
 * - 响应 code=200 时返回整个 body；code=401 时自动静默重登一次并重试原请求；其他非 200 抛错
 */

// 后端地址（本地开发；真机调试请改为局域网 IP）
export const BASE_URL = 'http://localhost:4121'

// 登录接口与白名单（游客可浏览，不强制 token）
const LOGIN_PATH = '/api/login'

const TOKEN_KEY = 'takeout_token'
const MEMBER_KEY = 'takeout_member'

export function getToken() {
	return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getMember() {
	return uni.getStorageSync(MEMBER_KEY) || null
}

function saveLogin(data) {
	uni.setStorageSync(TOKEN_KEY, data.token)
	uni.setStorageSync(MEMBER_KEY, {
		memberId: data.memberId,
		nickname: data.nickname,
		avatar: data.avatar,
		phone: data.phone
	})
}

function clearLogin() {
	uni.removeStorageSync(TOKEN_KEY)
	uni.removeStorageSync(MEMBER_KEY)
}

/** 调后端登录接口：code -> POST /api/login 换 token 并保存 */
function requestLoginByCode(code, resolve, reject) {
	uni.request({
		url: BASE_URL + LOGIN_PATH,
		method: 'POST',
		data: { code: code },
		success: (r) => {
			const body = r.data
			if (body && body.code === 200 && body.data && body.data.token) {
				saveLogin(body.data)
				resolve(body.data)
			} else {
				reject(new Error((body && body.msg) || '登录失败'))
			}
		},
		fail: (err) => reject(new Error('网络异常，登录请求失败'))
	})
}

/**
 * 静默登录（分平台）：
 * - 微信小程序：wx.login 取 code -> 后端 code2session
 * - H5 浏览器调试：无微信环境，用本地持久化的调试标识作为 code，
 *   需后端开启开发辅助开关 takeout.wx.mock-enabled（code 即 openid）
 */
export function login() {
	return new Promise((resolve, reject) => {
		// #ifdef H5
		const DEBUG_OPENID_KEY = 'takeout_debug_openid'
		let debugOpenid = uni.getStorageSync(DEBUG_OPENID_KEY)
		if (!debugOpenid) {
			debugOpenid = 'h5-debug-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
			uni.setStorageSync(DEBUG_OPENID_KEY, debugOpenid)
		}
		requestLoginByCode(debugOpenid, resolve, reject)
		// #endif
		// #ifndef H5
		uni.login({
			provider: 'weixin',
			success: (res) => {
				if (!res.code) {
					reject(new Error('wx.login 未返回 code'))
					return
				}
				requestLoginByCode(res.code, resolve, reject)
			},
			fail: (err) => reject(new Error('微信登录调用失败：' + (err && err.errMsg)))
		})
		// #endif
	})
}

/**
 * 确保存在登录态（已有 token 则直接通过；否则静默登录）
 * @param force true 时强制重新登录（如 401 后）
 */
export function ensureLogin(force = false) {
	if (!force && getToken()) {
		return Promise.resolve(getMember())
	}
	clearLogin()
	return login()
}

/**
 * 统一请求
 * @param options { url, method, data, needAuth(默认 true：401 时自动重登重试) }
 * @return Promise<body>，body 为后端 AjaxResult（code=200）
 */
export function request(options) {
	const { url, method = 'GET', data = {}, needAuth = true } = options
	return new Promise((resolve, reject) => {
		const header = { 'Content-Type': 'application/json' }
		const token = getToken()
		if (token) {
			header['Authorization'] = 'Bearer ' + token
		}
		uni.request({
			url: BASE_URL + url,
			method,
			data,
			header,
			success: async (res) => {
				const body = res.data
				if (body && body.code === 200) {
					resolve(body)
					return
				}
				if (body && body.code === 401 && needAuth) {
					// 登录过期：静默重登一次并重试
					try {
						await ensureLogin(true)
						const retryBody = await request({ ...options, needAuth: false })
						resolve(retryBody)
					} catch (e) {
						reject(new Error('请先登录'))
					}
					return
				}
				reject(new Error((body && body.msg) || ('请求失败(' + res.statusCode + ')')))
			},
			fail: (err) => {
				reject(new Error('网络异常，请稍后重试'))
			}
		})
	})
}

export default { request, login, ensureLogin, getToken, getMember, clearLogin, BASE_URL }
