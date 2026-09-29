/**
 * AI 流式问答客户端（二期 RAG）。
 *
 * 微信小程序端 SSE 接收说明：
 *   uni.request 开启 enableChunked 后，服务端返回的分块数据通过 onChunkReceived 回调，
 *   每次拿到的是 ArrayBuffer（可能包含多个 SSE 事件，也可能只包含半个事件），
 *   因此必须用缓冲区累积后按 "\n\n" 切分完整事件，再解析每行的 "data:{...}"。
 *
 * 服务端事件（data 为 JSON）：
 *   {"type":"start","sessionNo":"..."} / {"type":"delta","content":"..."} /
 *   {"type":"done","costMs":123} / {"type":"error","message":"..."}
 *
 * 注：知识来源不下发前端（仅服务端落库留痕），故本客户端只处理回答正文。
 */
import { BASE_URL, getToken } from './request'

/**
 * 发起流式提问
 * @param {Object} opts
 * @param {string} opts.question  问题（≤500 字）
 * @param {string} opts.sessionNo 会话标识（可空，为空则新建）
 * @param {(text:string)=>void} opts.onDelta    收到增量文本时回调（用于逐字渲染）
 * @param {(sessionNo:string)=>void} opts.onStart 收到会话号时回调
 * @param {(costMs:number)=>void} opts.onDone   正常结束回调
 * @returns {{abort: Function, promise: Promise<void>}} 可用于中断请求
 */
export function streamChat({ question, sessionNo, onDelta, onStart, onDone }) {
	let task = null
	let finished = false
	const promise = new Promise((resolve, reject) => {
		// 累积缓冲区：chunk 可能截断在事件中间，必须跨回调累积
		let buffer = ''
		let answerText = ''

		task = uni.request({
			url: BASE_URL + '/api/ai/chat/stream',
			method: 'GET',
			timeout: 180000,
			// 关键：开启分块接收（微信小程序基础库 2.20.2+ / 各端支持情况见 uni 文档）
			enableChunked: true,
			header: {
				Authorization: 'Bearer ' + getToken(),
				Accept: 'text/event-stream'
			},
			data: { question: question, sessionNo: sessionNo || '' },
			success: () => {
				// 部分端在请求结束时才触发 success；流式内容已在 onChunkReceived 处理
				if (!finished) {
					finished = true
					resolve()
				}
			},
			fail: (err) => {
				if (!finished) {
					finished = true
					reject(new Error('连接失败：' + ((err && err.errMsg) || '请检查网络')))
				}
			}
		})

		// 分块回调：累积 → 按空行切分完整事件 → 解析 data 行
		if (task && typeof task.onChunkReceived === 'function') {
			task.onChunkReceived((res) => {
				try {
					buffer += decodeChunk(res.data)
					let idx
					// SSE 事件以空行分隔
					while ((idx = buffer.indexOf('\n\n')) >= 0) {
						const rawEvent = buffer.slice(0, idx)
						buffer = buffer.slice(idx + 2)
						handleEvent(rawEvent)
					}
				} catch (e) {
					console.warn('[AI流式] 分块解析异常:', e)
				}
			})
		} else {
			// 兜底：个别端不支持 enableChunked，则整体响应会在 success 里返回
			console.warn('[AI流式] 当前环境不支持分块接收，请升级基础库或改用非流式接口')
		}

		function handleEvent(rawEvent) {
			// 一个事件可能有多行（data:/event:/id:），这里只取 data:
			const lines = rawEvent.split('\n')
			for (const line of lines) {
				const t = line.trim()
				if (!t.startsWith('data:')) continue
				const payload = t.slice(5).trim()
				if (!payload) continue
				let msg
				try {
					msg = JSON.parse(payload)
				} catch (e) {
					continue
				}
				if (msg.type === 'start') {
					if (onStart && msg.sessionNo) onStart(msg.sessionNo)
				} else if (msg.type === 'delta') {
					answerText += msg.content || ''
					if (onDelta) onDelta(msg.content || '')
				} else if (msg.type === 'done') {
					if (!finished) {
						finished = true
						if (onDone) onDone(msg.costMs || 0)
						resolve()
					}
				} else if (msg.type === 'error') {
					if (!finished) {
						finished = true
						reject(new Error(msg.message || 'AI 回答失败'))
					}
				}
			}
		}
	})

	return {
		promise,
		abort() {
			if (task && task.abort) task.abort()
		}
	}
}

/** ArrayBuffer / 字符串 → 文本（SSE 为 UTF-8，需正确处理中文） */
function decodeChunk(data) {
	if (typeof data === 'string') return data
	if (data instanceof ArrayBuffer) {
		// 小程序环境可能没有 TextDecoder，用 uni 提供的解码或手动解码
		if (typeof TextDecoder !== 'undefined') {
			return new TextDecoder('utf-8').decode(new Uint8Array(data))
		}
		return decodeUtf8(new Uint8Array(data))
	}
	if (data && data.byteLength !== undefined) {
		return decodeUtf8(new Uint8Array(data))
	}
	return String(data || '')
}

/** 手写 UTF-8 解码（兼容无 TextDecoder 的小程序运行时，正确处理多字节中文） */
function decodeUtf8(bytes) {
	let out = ''
	let i = 0
	while (i < bytes.length) {
		const b = bytes[i]
		if (b < 0x80) {
			out += String.fromCharCode(b)
			i += 1
		} else if (b >= 0xc0 && b < 0xe0) {
			out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f))
			i += 2
		} else if (b >= 0xe0 && b < 0xf0) {
			out += String.fromCharCode(
				((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f)
			)
			i += 3
		} else {
			// 4 字节（emoji 等）：转换为代理对
			const cp =
				((b & 0x07) << 18) |
				((bytes[i + 1] & 0x3f) << 12) |
				((bytes[i + 2] & 0x3f) << 6) |
				(bytes[i + 3] & 0x3f)
			const hi = 0xd800 + ((cp - 0x10000) >> 10)
			const lo = 0xdc00 + ((cp - 0x10000) & 0x3ff)
			out += String.fromCharCode(hi, lo)
			i += 4
		}
	}
	return out
}

export default { streamChat }
