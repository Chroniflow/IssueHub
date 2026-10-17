<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createPoll, deletePoll, fetchPollBallot, fetchPolls, getApiErrorMessage, submitPollBallot, withdrawPollBallot, type CommunityPollResponse } from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const polls = ref<CommunityPollResponse[]>([])
const loading = ref(true)
const error = ref('')
const creating = ref(false)
const question = ref('')
const options = ref(['', ''])
const multipleChoice = ref(false)
const selections = ref<Record<number, number[]>>({})
const voted = ref<Record<number, boolean>>({})
const actionId = ref<number | null>(null)
const actionError = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    polls.value = await fetchPolls()
    if (auth.user) await Promise.all(polls.value.map(async ({ poll }) => {
      try { const ballot = await fetchPollBallot(poll.id); selections.value[poll.id] = ballot.optionIds; voted.value[poll.id] = ballot.optionIds.length > 0 } catch { selections.value[poll.id] = [] }
    }))
  } catch (e) { error.value = getApiErrorMessage(e, '投票加载失败，请稍后重试。') }
  finally { loading.value = false }
}

function toggle(poll: CommunityPollResponse['poll'], optionId: number) {
  const current = selections.value[poll.id] ?? []
  selections.value[poll.id] = poll.multipleChoice
    ? current.includes(optionId) ? current.filter((id) => id !== optionId) : [...current, optionId]
    : [optionId]
}

async function vote(pollId: number) {
  const selected = selections.value[pollId] ?? []; if (!selected.length) return
  actionId.value = pollId; actionError.value = ''
  try { const updated = await submitPollBallot(pollId, selected); const item = polls.value.find((entry) => entry.poll.id === pollId); if (item) item.poll = updated; voted.value[pollId] = true }
  catch (e) { actionError.value = getApiErrorMessage(e, '提交投票失败。') }
  finally { actionId.value = null }
}

async function withdraw(pollId: number) {
  actionId.value = pollId; actionError.value = ''
  try { const updated = await withdrawPollBallot(pollId); const item = polls.value.find((entry) => entry.poll.id === pollId); if (item) item.poll = updated; selections.value[pollId] = []; voted.value[pollId] = false }
  catch (e) { actionError.value = getApiErrorMessage(e, '撤销投票失败。') }
  finally { actionId.value = null }
}

async function submitCreate() {
  const normalized = options.value.map((value) => value.trim())
  if (!question.value.trim() || normalized.some((value) => !value) || new Set(normalized.map((value) => value.toLowerCase())).size !== normalized.length) { actionError.value = '请填写问题和至少两个不重复的选项。'; return }
  creating.value = true; actionError.value = ''
  try { await createPoll({ question: question.value.trim(), multipleChoice: multipleChoice.value, options: normalized }); question.value = ''; options.value = ['', '']; multipleChoice.value = false; await load() }
  catch (e) { actionError.value = getApiErrorMessage(e, '创建投票失败。') }
  finally { creating.value = false }
}

async function remove(pollId: number) {
  actionId.value = pollId; actionError.value = ''
  try { await deletePoll(pollId); polls.value = polls.value.filter((entry) => entry.poll.id !== pollId) }
  catch (e) { actionError.value = getApiErrorMessage(e, '删除投票失败；已有用户参与的投票不能删除。') }
  finally { actionId.value = null }
}

function addOption() { if (options.value.length < 10) options.value.push('') }
function removeOption(index: number) { if (options.value.length > 2) options.value.splice(index, 1) }
onMounted(load)
</script>

<template>
  <main class="polls-page">
    <header class="issues-page-header"><div><h1>社区投票</h1><p>投票与问题反馈相互独立，用于收集玩家意见。</p></div></header>
    <section v-if="auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN'" class="poll-panel poll-creator community-poll-create">
      <header class="poll-header"><div><span>新投票</span><h2>发起社区投票</h2></div></header>
      <label>问题<input v-model="question" maxlength="200" placeholder="你想了解什么？" /></label>
      <div class="poll-option-editor"><label v-for="(_, index) in options" :key="index">选项 {{ index + 1 }}<span><input v-model="options[index]" maxlength="100" /><button v-if="options.length > 2" type="button" @click="removeOption(index)">×</button></span></label></div>
      <button v-if="options.length < 10" class="inline-action" type="button" @click="addOption">+ 添加选项</button>
      <label class="poll-multiple-toggle"><input v-model="multipleChoice" type="checkbox" /><span>允许多选</span></label>
      <button class="primary-button" type="button" :disabled="creating" @click="submitCreate">{{ creating ? '创建中…' : '创建投票' }}</button>
    </section>
    <p v-if="actionError" class="list-status warning">{{ actionError }}</p>
    <p v-if="loading" class="page-loading">正在加载投票…</p>
    <p v-else-if="error" class="list-status warning">{{ error }} <button class="inline-action" @click="load">重试</button></p>
    <section v-else class="community-poll-list">
      <article v-for="entry in polls" :key="entry.poll.id" class="poll-panel">
        <header class="poll-header"><div><span>由 {{ entry.creator }} 发起</span><h2>{{ entry.poll.question }}</h2></div><small>{{ entry.poll.multipleChoice ? '可多选' : '单选' }} · {{ entry.poll.participantCount }} 人参与</small></header>
        <div class="poll-options"><button v-for="option in entry.poll.options" :key="option.id" class="poll-option" :class="{ selected: (selections[entry.poll.id] ?? []).includes(option.id) }" type="button" :disabled="!auth.user" @click="toggle(entry.poll, option.id)"><span class="poll-control">{{ (selections[entry.poll.id] ?? []).includes(option.id) ? '✓' : '' }}</span><span class="poll-option-content"><strong>{{ option.text }}</strong><span class="poll-result-track"><span :style="{ width: `${entry.poll.participantCount ? Math.round(option.voteCount / entry.poll.participantCount * 100) : 0}%` }"></span></span></span><span>{{ option.voteCount }}</span></button></div>
        <div v-if="auth.user" class="poll-actions"><button class="primary-button" :disabled="actionId === entry.poll.id || !(selections[entry.poll.id] ?? []).length" @click="vote(entry.poll.id)">{{ voted[entry.poll.id] ? '更新选择' : '提交投票' }}</button><button v-if="voted[entry.poll.id]" class="secondary-button" @click="withdraw(entry.poll.id)">撤销投票</button><button v-if="entry.creatorId === auth.user.id || auth.user.role === 'ADMIN'" class="danger-link" @click="remove(entry.poll.id)">删除</button></div>
      </article>
      <p v-if="!polls.length" class="empty">暂无社区投票。</p>
    </section>
  </main>
</template>

<style scoped>
.polls-page { width: min(900px, calc(100% - 32px)); margin: 0 auto; padding: 28px 0 60px; }
.community-poll-create { width: 100%; margin: 20px 0; }
.community-poll-list { display: grid; gap: 18px; }
.community-poll-list .poll-panel { width: 100%; margin: 0; }
@media (max-width: 640px) {
  .polls-page { width: min(100% - 24px, 900px); padding-top: 20px; }
  .poll-header { flex-direction: column; gap: 6px; }
  .poll-header small { padding-top: 0; white-space: normal; }
  .poll-actions { align-items: stretch; flex-direction: column; }
  .poll-actions .danger-link { margin-left: 0; }
}
</style>
