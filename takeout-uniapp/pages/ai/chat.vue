<template>
	<view class="page">
		<!-- 顶部：会话切换 -->
		<view class="topbar">
			<view class="topbar-left" @tap="openSessions">
				<text class="session-title">{{ sessionTitle }}</text>
				<text class="session-arrow">▾</text>
			</view>
			<view class="new-btn ap-press" @tap="startNewSession">＋ 新对话</view>
		</view>

		<!-- 消息列表 -->
		<scroll-view class="msg-list" scroll-y :scroll-into-view="scrollTarget" scroll-with-animation>
			<!-- 欢迎区（无消息时） -->
			<view v-if="!messages.length" class="welcome">
				<text class="welcome-emoji">🍚</text>
				<text class="welcome-title">我是阿婆家的点餐助手</text>
				<text class="welcome-sub">可以问我：菜品口味、食材用料、忌口建议、健康搭配…</text>
				<view class="quick-list">
					<view v-for="(q, i) in quickQuestions" :key="i" class="quick-item ap-press" @tap="askQuick(q)">{{ q }}</view>
				</view>
			</view>

			<!-- 消息气泡 -->
			<view v-for="(m, i) in messages" :key="i" :id="'msg-' + i" :class="['msg-row', m.role === 'user' ? 'user' : 'assistant']">
				<!-- 助手头像：用单码位 emoji（组合 emoji 如 🍚 在部分小程序运行时会被拆成两个字符显示） -->
				<view v-if="m.role === 'assistant'" class="avatar">🍚</view>
				<view class="bubble-wrap">
					<view :class="['bubble', m.role === 'user' ? 'bubble-user' : 'bubble-assistant']">
						<!-- 流式输出中且尚无内容：显示打字中动画 -->
						<view v-if="m.streaming && !m.content" class="typing">
							<view class="dot"></view><view class="dot"></view><view class="dot"></view>
						</view>
						<text v-else class="bubble-text">{{ m.content }}<text v-if="m.streaming" class="caret">▍</text></text>
					</view>
				</view>
				<view v-if="m.role === 'user'" class="avatar user-avatar">😊</view>
			</view>
			<view class="list-bottom" id="list-bottom"></view>
		</scroll-view>

		<!-- 输入栏 -->
		<view class="input-bar">
			<input
				class="input"
				v-model="input"
				placeholder="问问阿婆家的菜品…"
				confirm-type="send"
				:disabled="sending"
				@confirm="send"
			/>
			<view :class="['send-btn ap-press', canSend ? '' : 'disabled']" @tap="send">
				{{ sending ? '···' : '发送' }}
			</view>
		</view>

		<!-- 会话列表弹层 -->
		<wd-popup v-model="showSessions" position="bottom" :safe-area-inset-bottom="true">
			<view class="session-panel">
				<view class="panel-head">
					<text class="panel-title">历史对话</text>
					<text class="close" @tap="showSessions = false">✕</text>
				</view>
				<scroll-view scroll-y class="panel-body">
					<view
						v-for="s in sessions"
						:key="s.sessionNo"
						:class="['session-item', s.sessionNo === sessionNo ? 'active' : '']"
						@tap="switchSession(s)"
					>
						<text class="s-title">{{ s.title || '新的对话' }}</text>
						<text class="s-meta">{{ s.messageCount || 0 }} 条 · {{ (s.lastActiveTime || '').slice(0, 16) }}</text>
					</view>
					<view v-if="!sessions.length" class="ap-empty">
						<text class="em">💬</text>
						还没有历史对话
					</view>
				</scroll-view>
			</view>
		</wd-popup>
	</view>
</template>

<script>
import { request, ensureLogin } from '@/utils/request'
import { streamChat } from '@/utils/ai-stream'

export default {
	data() {
		return {
			sessionNo: '',
			sessionTitle: '新的对话',
			messages: [],           // {role, content, streaming} 消息列表（流式逐字渲染）
			input: '',
			sending: false,
			scrollTarget: '',
			showSessions: false,
			sessions: [],
			quickQuestions: [
				'我感冒了想吃清淡的，推荐什么？',
				'有什么菜是不辣的？',
				'两个人吃点什么合适？',
				'对花生过敏能吃哪些？'
			]
		}
	},
	computed: {
		canSend() {
			return !this.sending && this.input.trim().length > 0
		}
	},
	onLoad() {
		// 从会话列表进来的情况由 onShow 处理
	},
	onShow() {
		if (!this.messages.length) {
			this.scrollToBottom()
		}
	},
	methods: {
		/** 滚动到底部（消息后调用） */
		scrollToBottom() {
			this.$nextTick(() => {
				this.scrollTarget = ''
				this.$nextTick(() => {
					this.scrollTarget = 'list-bottom'
				})
			})
		},
		/** 快捷提问 */
		askQuick(q) {
			this.input = q
			this.send()
		},
		/** 发送提问（流式：SSE 逐块渲染，打字机效果） */
		async send() {
			if (!this.canSend) return
			const question = this.input.trim()
			this.input = ''
			// 用户消息入列
			this.messages.push({ role: 'user', content: question })
			// 助手占位（流式中：先显示打字动画，收到首个增量后逐字追加）
			const aiMsg = { role: 'assistant', content: '', streaming: true }
			this.messages.push(aiMsg)
			this.sending = true
			this.scrollToBottom()
			try {
				await ensureLogin()
				if (this.sessionTitle === '新的对话' && question) {
					this.sessionTitle = question.length > 14 ? question.slice(0, 14) + '…' : question
				}
				const { promise } = streamChat({
					question,
					sessionNo: this.sessionNo || '',
					onStart: (sessionNo) => {
						this.sessionNo = sessionNo || this.sessionNo
					},
					onDelta: (text) => {
						aiMsg.content += text
						this.scrollToBottom()
					},
					onDone: () => {
						aiMsg.streaming = false
						if (!aiMsg.content) {
							aiMsg.content = '（没有收到回答，请重试）'
						}
						this.scrollToBottom()
					}
				})
				await promise
				aiMsg.streaming = false
			} catch (e) {
				aiMsg.streaming = false
				aiMsg.content = aiMsg.content
					? aiMsg.content + '\n\n（回答中断：' + (e.message || '请稍后重试') + '）'
					: '抱歉，我这边出了点问题：' + (e.message || '请稍后再试')
			} finally {
				this.sending = false
				this.scrollToBottom()
			}
		},
		/** 新建会话 */
		async startNewSession() {
			this.sessionNo = ''
			this.sessionTitle = '新的对话'
			this.messages = []
			this.showSessions = false
			this.scrollToBottom()
		},
		/** 历史会话 */
		async openSessions() {
			try {
				await ensureLogin()
				const body = await request({ url: '/api/ai/sessions' })
				this.sessions = body.data || []
				this.showSessions = true
			} catch (e) {
				uni.showToast({ title: e.message || '加载失败', icon: 'none' })
			}
		},
		/** 切换会话（加载历史消息；知识来源不下发前端，仅展示正文） */
		async switchSession(s) {
			try {
				const body = await request({ url: '/api/ai/session/' + s.sessionNo })
				const list = body.data || []
				this.messages = list.map(m => ({
					role: m.role,
					content: m.content
				}))
				this.sessionNo = s.sessionNo
				this.sessionTitle = s.title || '历史对话'
				this.showSessions = false
				this.scrollToBottom()
			} catch (e) {
				uni.showToast({ title: e.message || '加载失败', icon: 'none' })
			}
		}
	}
}
</script>

<style lang="scss" scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: $ap-bg;
}

/* 顶部栏 */
.topbar {
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	background-color: $ap-card;
	box-shadow: $ap-shadow;

	.topbar-left {
		flex: 1;
		display: flex;
		align-items: center;
		min-width: 0;

		.session-title {
			font-size: 28rpx;
			font-weight: 700;
			color: $ap-text;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		.session-arrow {
			margin-left: 8rpx;
			font-size: 24rpx;
			color: $ap-text-light;
		}
	}

	.new-btn {
		padding: 10rpx 24rpx;
		font-size: 24rpx;
		color: #FFFFFF;
		background-color: $ap-primary;
		border-radius: $ap-radius-pill;
	}
}

/* 消息区 */
.msg-list {
	flex: 1;
	padding: 20rpx;
	box-sizing: border-box;
}

.welcome {
	padding: 60rpx 40rpx;
	text-align: center;

	.welcome-emoji {
		font-size: 90rpx;
	}

	.welcome-title {
		display: block;
		margin-top: 20rpx;
		font-size: 34rpx;
		font-weight: 700;
		color: $ap-text;
	}

	.welcome-sub {
		display: block;
		margin-top: 12rpx;
		font-size: 24rpx;
		color: $ap-text-light;
	}

	.quick-list {
		margin-top: 40rpx;

		.quick-item {
			margin-bottom: 16rpx;
			padding: 20rpx 24rpx;
			font-size: 26rpx;
			color: $ap-text;
			background-color: $ap-card;
			border-radius: $ap-radius;
			box-shadow: $ap-shadow;
		}
	}
}

.msg-row {
	display: flex;
	margin-bottom: 24rpx;

	&.user {
		justify-content: flex-end;
	}

	.avatar {
		width: 64rpx;
		height: 64rpx;
		line-height: 64rpx;
		text-align: center;
		font-size: 36rpx;
		background-color: $ap-card;
		border-radius: 50%;
		flex-shrink: 0;
		box-shadow: $ap-shadow;

		&.user-avatar {
			background-color: $ap-secondary;
		}
	}

	.bubble-wrap {
		max-width: 76%;
		margin: 0 16rpx;

		.bubble {
			padding: 20rpx 24rpx;
			border-radius: $ap-radius;
			box-shadow: $ap-shadow;

			.bubble-text {
				font-size: 28rpx;
				line-height: 1.6;
				word-break: break-all;
			}

			&.bubble-assistant {
				background-color: $ap-card;
				color: $ap-text;
				border-top-left-radius: 6rpx;
			}

			&.bubble-user {
				background-color: $ap-primary;
				color: #FFFFFF;
				border-top-right-radius: 6rpx;
			}
		}

		/* 打字中三点动画 */
		.typing {
			display: flex;
			align-items: center;
			padding: 6rpx 0;

			.dot {
				width: 14rpx;
				height: 14rpx;
				margin-right: 10rpx;
				background-color: $ap-text-light;
				border-radius: 50%;
				animation: typing-bounce 1.2s infinite ease-in-out;

				&:nth-child(2) { animation-delay: 0.15s; }
				&:nth-child(3) { animation-delay: 0.3s; margin-right: 0; }
			}
		}
	}
}

@keyframes typing-bounce {
	0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
	30% { transform: translateY(-10rpx); opacity: 1; }
}

/* 流式输出光标（打字机效果，闪烁） */
.caret {
	margin-left: 2rpx;
	color: $ap-primary;
	animation: caret-blink 0.9s steps(1) infinite;
}

@keyframes caret-blink {
	0%, 50% { opacity: 1; }
	51%, 100% { opacity: 0; }
}

/* 输入栏 */
.input-bar {
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
	background-color: $ap-card;
	box-shadow: 0 -4rpx 16rpx rgba(255, 112, 67, 0.08);

	.input {
		flex: 1;
		height: 76rpx;
		padding: 0 24rpx;
		font-size: 28rpx;
		color: $ap-text;
		background-color: $ap-bg;
		border-radius: $ap-radius-pill;
	}

	.send-btn {
		margin-left: 16rpx;
		padding: 0 36rpx;
		height: 76rpx;
		line-height: 76rpx;
		font-size: 28rpx;
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

/* 会话弹层 */
.session-panel {
	padding: 32rpx;

	.panel-head {
		display: flex;
		align-items: center;

		.panel-title {
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

	.panel-body {
		max-height: 55vh;
		margin-top: 20rpx;

		.session-item {
			padding: 20rpx;
			margin-bottom: 14rpx;
			background-color: $ap-bg;
			border-radius: $ap-radius;

			&.active {
				box-shadow: 0 0 0 2rpx $ap-primary inset;
			}

			.s-title {
				font-size: 28rpx;
				font-weight: 700;
				color: $ap-text;
			}

			.s-meta {
				display: block;
				margin-top: 6rpx;
				font-size: 22rpx;
				color: $ap-text-light;
			}
		}
	}
}
</style>
