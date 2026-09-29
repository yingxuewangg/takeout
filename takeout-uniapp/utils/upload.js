/**
 * 图片上传（复用后端统一上传模块 /api/file/upload，T7 退款凭证/T8 留言图片共用）。
 * 返回相对路径（/profile/upload/...），由调用方随业务入库；完整 URL 展示时后端/前端拼接。
 */
import { BASE_URL, getToken } from './request'

/** 上传单张图片，返回相对路径 */
export function uploadImage(filePath) {
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: BASE_URL + '/api/file/upload',
			filePath,
			name: 'file',
			header: { Authorization: 'Bearer ' + getToken() },
			success: (res) => {
				try {
					const body = JSON.parse(res.data)
					if (body.code === 200 && body.fileName) {
						resolve(body.fileName)
					} else {
						reject(new Error((body && body.msg) || '上传失败'))
					}
				} catch (e) {
					reject(new Error('上传响应解析失败'))
				}
			},
			fail: () => reject(new Error('上传失败，请检查网络'))
		})
	})
}

/**
 * 选择并上传多张图片（追加模式）
 * @param max 剩余可传张数
 * @param onOne 每张完成回调（已完成的路径数组），便于进度展示
 * @returns 上传成功的相对路径数组（失败抛错，已成功的不回滚由调用方决定）
 */
export function chooseAndUploadImages(max, onOne) {
	return new Promise((resolve, reject) => {
		if (max <= 0) {
			reject(new Error('最多上传 ' + (max + 5) + ' 张'))
			return
		}
		uni.chooseImage({
			count: max,
			success: async (res) => {
				const paths = []
				try {
					for (const p of res.tempFilePaths) {
						const fileName = await uploadImage(p)
						paths.push(fileName)
						if (onOne) onOne(paths.slice())
					}
					resolve(paths)
				} catch (e) {
					reject(e)
				}
			},
			fail: () => reject(new Error('取消选择'))
		})
	})
}

export default { uploadImage, chooseAndUploadImages }
