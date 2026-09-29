/**
 * 新订单实时提醒（T15）：WebSocket 连接管理 + 通知弹窗 + 语音播报。
 *
 * - 连接地址取 VITE_APP_WS_URL（如 ws://localhost:4121/websocket/order），握手带 ?token=<若依登录token>；
 * - 断线自动重连（指数退避 1s/2s/4s...上限 30s），重连成功派发 window 事件 `order-push-reconnect`
 *   （订单管理页监听后刷新列表，一期不做消息补发）；
 * - 语音播报用 Web Speech API（浏览器原生，零费用）；Chrome 等禁止无用户交互的自动播放，
 *   必须由商家先点击「语音提醒」开关激活（activateVoice 内播一段空文本），未激活/不支持时仅弹窗不报错；
 * - 开关状态存 localStorage（takeout.voice.enabled），页面刷新后保留。
 */
import { ElNotification } from 'element-plus'
import { getToken } from '@/utils/auth'

const VOICE_KEY = 'takeout.voice.enabled'
const RECONNECT_EVENT = 'order-push-reconnect'

/** 语音播报开关（未点击开启前为 false，弹窗正常、语音静默跳过） */
let voiceEnabled = localStorage.getItem(VOICE_KEY) === '1'
/** 当前连接与重连状态 */
let ws: WebSocket | null = null
let retryCount = 0
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let manuallyClosed = false
let connectionGeneration = 0

/** 是否支持 Web Speech API（不支持时仅弹窗） */
function speechSupported(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window
}

/** 语音播报（zh-CN；浏览器不支持时静默跳过） */
function speak(text: string) {
  if (!voiceEnabled || !speechSupported()) return
  try {
    // 某些 Chromium 版本在页面重新激活后会保留 paused 队列，先恢复再播报
    if (window.speechSynthesis.paused) window.speechSynthesis.resume()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1
    utterance.volume = 1
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
  } catch (e) {
    // 播报失败不影响通知弹窗
    console.warn('语音播报失败:', e)
  }
}

/** 新订单消息处理：弹通知 + 语音播报 */
function handleNewOrder(data: Record<string, any>) {
  const delivery = Number(data.deliveryType) === 2 ? '外卖' : '堂食'
  const amount = Number(data.amount || 0).toFixed(2)
  const no = String(data.orderNo || '').slice(-8)
  ElNotification({
    title: '🔔 新订单提醒',
    message: `尾号 ${no} 的${delivery}订单，金额 ¥${amount}，请及时处理`,
    type: 'success',
    duration: 0 // 不自动关闭，需商家手动处理
  })
  speak(String(data.content || '您有新的订单，请及时处理'))
}

function handleMessage(event: MessageEvent) {
  try {
    const data = JSON.parse(event.data)
    if (data.type === 'NEW_ORDER') handleNewOrder(data)
  } catch (e) {
    console.warn('新订单消息解析失败:', e)
  }
}

function scheduleReconnect() {
  if (manuallyClosed || reconnectTimer) return
  const delay = Math.min(30000, 1000 * Math.pow(2, retryCount))
  retryCount++
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

/** 建立 WebSocket 连接（登录后由 Navbar 调用；重复调用会先关旧连接） */
function connect() {
  if (typeof WebSocket === 'undefined') return
  const token = getToken()
  if (!token) return // 未登录不连
  const generation = ++connectionGeneration
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    ws.close(1000, 'reconnect')
  }
  const configuredUrl = import.meta.env.VITE_APP_WS_URL
  if (!configuredUrl) {
    console.error('[新订单提醒] 未配置 VITE_APP_WS_URL')
    return
  }
  const separator = configuredUrl.includes('?') ? '&' : '?'
  const url = `${configuredUrl}${separator}token=${encodeURIComponent(token)}`
  manuallyClosed = false
  ws = new WebSocket(url)
  ws.onopen = () => {
    if (generation !== connectionGeneration) return
    retryCount = 0
    console.log('[新订单提醒] 已连接')
    // 重连成功后通知订单列表页刷新（一期不做消息补发）
    window.dispatchEvent(new CustomEvent(RECONNECT_EVENT))
  }
  ws.onmessage = handleMessage
  ws.onclose = (event) => {
    if (generation !== connectionGeneration) return
    // 1006 = 异常断开（后端重启/网络中断）；鉴权失败时浏览器也可能只报告 1006
    console.warn('[新订单提醒] 连接关闭 code=' + event.code)
    scheduleReconnect()
  }
  ws.onerror = () => {
    if (generation === connectionGeneration) ws?.close()
  }
}

/** 主动断开（退出登录时调用，避免无效重连） */
function close() {
  manuallyClosed = true
  connectionGeneration++
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  ws?.close(1000, 'manual close')
  ws = null
}

/** 开启语音提醒：需在用户点击事件内调用（激活浏览器音频权限），成功后记住开关 */
function enableVoice(): boolean {
  voiceEnabled = true
  localStorage.setItem(VOICE_KEY, '1')
  // 浏览器自动播放限制：必须在用户手势内触发一次 speak 完成激活（空格不影响体验）
  if (speechSupported()) {
    try {
      const warm = new SpeechSynthesisUtterance(' ')
      warm.volume = 0
      window.speechSynthesis.speak(warm)
    } catch (ignore) {
      // 激活失败不报错，下次新订单时自动重试
    }
  }
  return speechSupported()
}

/** 关闭语音提醒 */
function disableVoice() {
  voiceEnabled = false
  localStorage.setItem(VOICE_KEY, '0')
  if (speechSupported()) window.speechSynthesis.cancel()
}

function isVoiceEnabled(): boolean {
  return voiceEnabled
}

export const orderPush = {
  connect,
  close,
  enableVoice,
  disableVoice,
  isVoiceEnabled,
  RECONNECT_EVENT
}
