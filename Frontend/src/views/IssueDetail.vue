<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bindIssueTag, createIssueLink, createIssuePoll, createIssueSubIssue, deleteIssue, deleteIssueAttachment, deleteIssueLink, deleteIssuePoll, downloadIssueAttachment, editIssue, fetchIssueAttachments, fetchIssueLinks, fetchIssuePoll, fetchIssuePollBallot, fetchIssueSubIssues, fetchIssueSubscription, fetchTags, getApiErrorMessage, getApiErrorStatus, setIssuePinned, submitIssuePollBallot, subscribeToIssue, unbindIssueTag, unsubscribeFromIssue, uploadIssueAttachment, withdrawIssuePollBallot, type IssueAttachmentResponse, type IssueLinkResponse, type IssuePollResponse, type IssueSubIssueResponse, type TagResponse } from '../api/http'
import AppSelect from '../components/AppSelect.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { useAuthStore } from '../stores/auth'
import { useIssueStore } from '../stores/issues'
import { hasTextWithin } from '../utils/format'

const route = useRoute()
const router = useRouter()
const reply = ref('')
const submitted = ref(false)
const submittingReply = ref(false)
const replyError = ref('')
const closing = ref(false)
const closeError = ref('')
const closeMenuOpen = ref(false)
const closeReason = ref<'COMPLETED' | 'NOT_PLANNED' | 'MEANINGLESS' | 'DUPLICATE'>('COMPLETED')
const duplicateOfIssueId = ref('')
const reopening = ref(false)
const store = useIssueStore()
const auth = useAuthStore()
const issueId = computed(() => Number(route.params.id))
const issue = computed(() => store.byId(issueId.value))
const loading = ref(false)
const loadError = ref('')
const commentsError = ref('')
const editingCommentId = ref<number | null>(null)
const editingCommentText = ref('')
const deletingCommentId = ref<number | null>(null)
const commentActionLoading = ref(false)
const commentActionError = ref('')
const availableTags = ref<TagResponse[]>([])
const tagsLoading = ref(false)
const tagsError = ref('')
const selectedTagId = ref<number | null>(null)
const tagActionLoading = ref(false)
const tagActionError = ref('')
const links = ref<IssueLinkResponse[]>([])
const linksError = ref('')
const linksLoading = ref(false)
const targetIssueId = ref('')
const relationType = ref('RELATED')
const relationOptions = [
  { value: 'RELATED', label: '相关' },
  { value: 'BLOCKS', label: '阻塞' },
  { value: 'DUPLICATES', label: '重复' },
]
const linkActionLoading = ref(false)
const linkActionError = ref('')
const subIssues = ref<IssueSubIssueResponse[]>([])
const subIssuesTotal = ref(0)
const subIssuesCompleted = ref(0)
const subIssuesLoading = ref(false)
const subIssuesError = ref('')
const creatingSubIssue = ref(false)
const subIssueTitle = ref('')
const subIssueBody = ref('')
const subIssueError = ref('')
const editingIssue = ref(false)
const editTitle = ref('')
const editBody = ref('')
const issueEditLoading = ref(false)
const issueEditError = ref('')
const deleteIssueConfirming = ref(false)
const deletingIssue = ref(false)
const deleteIssueError = ref('')
const subscribed = ref(false)
const subscriberCount = ref(0)
const subscriptionLoading = ref(false)
const subscriptionError = ref('')
const poll = ref<IssuePollResponse | null>(null)
const pollLoading = ref(false)
const pollError = ref('')
const selectedPollOptions = ref<number[]>([])
const hasPollBallot = ref(false)
const pollQuestion = ref('')
const pollOptions = ref(['', ''])
const pollMultipleChoice = ref(false)
const pollActionLoading = ref(false)
const pollActionError = ref('')
const confirmingTagDelete = ref<number | null>(null)
const attachments = ref<IssueAttachmentResponse[]>([])
const attachmentsLoading = ref(false)
const attachmentActionLoading = ref(false)
const attachmentError = ref('')
let loadSequence = 0

async function loadIssue() {
  const sequence = ++loadSequence
  const id = issueId.value
  if (!Number.isInteger(id) || id <= 0) { loading.value = false; loadError.value = '反馈编号无效。'; return }
  loading.value = true
  loadError.value = ''
  commentsError.value = ''
  reply.value = ''
  submitted.value = false
  replyError.value = ''
  closeError.value = ''
  closeMenuOpen.value = false
  closeReason.value = 'COMPLETED'
  duplicateOfIssueId.value = ''
  tagActionError.value = ''
  linkActionError.value = ''
  linksError.value = ''
  subIssuesError.value = ''
  subIssues.value = []
  subIssuesTotal.value = 0
  subIssuesCompleted.value = 0
  subIssueTitle.value = ''
  subIssueBody.value = ''
  subIssueError.value = ''
  issueEditError.value = ''
  deleteIssueError.value = ''
  subscriptionError.value = ''
  poll.value = null
  selectedPollOptions.value = []
  hasPollBallot.value = false
  pollError.value = ''
  pollActionError.value = ''
  attachments.value = []
  attachmentError.value = ''
  links.value = []
  linksLoading.value = true
  subIssuesLoading.value = true
  availableTags.value = []
  tagsError.value = ''
  tagsLoading.value = true
  selectedTagId.value = null
  targetIssueId.value = ''
  editingIssue.value = false
  editingCommentId.value = null
  deletingCommentId.value = null
  confirmingTagDelete.value = null
  const loaded = await store.loadDetailFromApi(id)
  if (sequence !== loadSequence) return
  if (!loaded) loadError.value = issue.value ? '暂时无法加载服务端详情，当前显示本地数据。' : '反馈不存在，或链接中的编号有误。'
  else if (!await store.loadCommentsFromApi(id)) commentsError.value = '讨论加载失败，请重试。'
  if (loaded) {
    await loadPoll()
    await loadAttachments()
    if (auth.user) await loadSubscription()
  }
  try {
    const fetchedTags = await fetchTags()
    if (sequence === loadSequence) availableTags.value = fetchedTags
  } catch (requestError: unknown) {
    if (sequence === loadSequence) tagsError.value = getApiErrorMessage(requestError, '标签加载失败，请重试。')
  } finally {
    if (sequence === loadSequence) tagsLoading.value = false
  }
  try {
    const fetchedLinks = await fetchIssueLinks(id)
    if (sequence === loadSequence) links.value = fetchedLinks
  } catch (requestError: unknown) {
    if (sequence === loadSequence) linksError.value = getApiErrorMessage(requestError, '关联工单加载失败，请重试。')
  } finally {
    if (sequence === loadSequence) linksLoading.value = false
  }
  try {
    const fetchedSubIssues = await fetchIssueSubIssues(id)
    if (sequence === loadSequence) {
      subIssues.value = fetchedSubIssues.items
      subIssuesTotal.value = fetchedSubIssues.total
      subIssuesCompleted.value = fetchedSubIssues.completed
    }
  } catch (requestError: unknown) {
    if (sequence === loadSequence) subIssuesError.value = getApiErrorMessage(requestError, '子议题加载失败，请重试。')
  } finally {
    if (sequence === loadSequence) subIssuesLoading.value = false
  }
  if (sequence !== loadSequence) return
  loading.value = false
}

watch(issueId, () => { void loadIssue() }, { immediate: true })
watch(() => auth.user?.id, (userId) => {
  if (userId && issue.value) { void loadSubscription(); if (poll.value) void loadPollBallot() }
  else { subscribed.value = false; subscriberCount.value = 0; subscriptionError.value = ''; selectedPollOptions.value = []; hasPollBallot.value = false }
})

async function loadPoll() {
  if (!issue.value) return
  pollLoading.value = true
  pollError.value = ''
  try {
    poll.value = await fetchIssuePoll(issue.value.id)
    if (auth.user) await loadPollBallot()
  } catch (requestError: unknown) {
    if (getApiErrorStatus(requestError) === 404) poll.value = null
    else pollError.value = getApiErrorMessage(requestError, '投票加载失败，请重试。')
  } finally { pollLoading.value = false }
}

async function loadPollBallot() {
  if (!issue.value || !poll.value || !auth.user) return
  try { selectedPollOptions.value = (await fetchIssuePollBallot(issue.value.id)).optionIds; hasPollBallot.value = selectedPollOptions.value.length > 0 }
  catch (requestError: unknown) { pollActionError.value = getApiErrorMessage(requestError, '你的选择加载失败，请重试。') }
}

function togglePollOption(optionId: number) {
  if (!poll.value) return
  if (!poll.value.multipleChoice) { selectedPollOptions.value = [optionId]; return }
  selectedPollOptions.value = selectedPollOptions.value.includes(optionId)
    ? selectedPollOptions.value.filter((id) => id !== optionId)
    : [...selectedPollOptions.value, optionId]
}

async function submitPollVote() {
  if (!issue.value || !poll.value) return
  if (!auth.user) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  if (!selectedPollOptions.value.length) { pollActionError.value = '请至少选择一个选项。'; return }
  pollActionLoading.value = true; pollActionError.value = ''
  try { poll.value = await submitIssuePollBallot(issue.value.id, selectedPollOptions.value); hasPollBallot.value = true }
  catch (requestError: unknown) { pollActionError.value = getApiErrorMessage(requestError, '提交投票失败，请稍后重试。') }
  finally { pollActionLoading.value = false }
}

async function withdrawPollVote() {
  if (!issue.value) return
  pollActionLoading.value = true; pollActionError.value = ''
  try { poll.value = await withdrawIssuePollBallot(issue.value.id); selectedPollOptions.value = []; hasPollBallot.value = false }
  catch (requestError: unknown) { pollActionError.value = getApiErrorMessage(requestError, '撤销投票失败，请稍后重试。') }
  finally { pollActionLoading.value = false }
}

function addPollOption() { if (pollOptions.value.length < 10) pollOptions.value.push('') }
function removePollOption(index: number) { if (pollOptions.value.length > 2) pollOptions.value.splice(index, 1) }

async function createPoll() {
  if (!issue.value) return
  const options = pollOptions.value.map((value) => value.trim())
  if (!pollQuestion.value.trim() || options.some((value) => !value)) { pollActionError.value = '请填写投票问题和所有选项。'; return }
  pollActionLoading.value = true; pollActionError.value = ''
  try {
    poll.value = await createIssuePoll(issue.value.id, { question: pollQuestion.value.trim(), multipleChoice: pollMultipleChoice.value, options })
    pollQuestion.value = ''; pollOptions.value = ['', '']; pollMultipleChoice.value = false
  } catch (requestError: unknown) { pollActionError.value = getApiErrorMessage(requestError, '创建投票失败，请稍后重试。') }
  finally { pollActionLoading.value = false }
}

async function removePoll() {
  if (!issue.value) return
  pollActionLoading.value = true; pollActionError.value = ''
  try { await deleteIssuePoll(issue.value.id); poll.value = null; selectedPollOptions.value = []; hasPollBallot.value = false }
  catch (requestError: unknown) { pollActionError.value = getApiErrorMessage(requestError, '删除投票失败，请稍后重试。') }
  finally { pollActionLoading.value = false }
}

async function loadSubscription() {
  if (!issue.value || !auth.user) return
  subscriptionLoading.value = true
  subscriptionError.value = ''
  try {
    const response = await fetchIssueSubscription(issue.value.id)
    subscribed.value = response.subscribed
    subscriberCount.value = response.subscriberCount
  } catch (requestError: unknown) {
    subscriptionError.value = getApiErrorMessage(requestError, '订阅状态加载失败，请重试。')
  } finally { subscriptionLoading.value = false }
}

async function toggleSubscription() {
  if (!issue.value) return
  if (!auth.user) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  subscriptionLoading.value = true
  subscriptionError.value = ''
  try {
    const response = subscribed.value ? await unsubscribeFromIssue(issue.value.id) : await subscribeToIssue(issue.value.id)
    subscribed.value = response.subscribed
    subscriberCount.value = response.subscriberCount
  } catch (requestError: unknown) {
    subscriptionError.value = getApiErrorMessage(requestError, '订阅操作失败，请稍后重试。')
  } finally { subscriptionLoading.value = false }
}

async function retryComments() {
  if (!issue.value) return
  commentsError.value = ''
  if (!await store.loadCommentsFromApi(issue.value.id)) commentsError.value = '讨论加载失败，请重试。'
}

async function retryLinks() {
  if (!issue.value) return
  linksError.value = ''
  linksLoading.value = true
  try { links.value = await fetchIssueLinks(issue.value.id) }
  catch (requestError: unknown) { linksError.value = getApiErrorMessage(requestError, '关联工单加载失败，请重试。') }
  finally { linksLoading.value = false }
}

async function retrySubIssues() {
  if (!issue.value) return
  subIssuesLoading.value = true
  subIssuesError.value = ''
  try {
    const response = await fetchIssueSubIssues(issue.value.id)
    subIssues.value = response.items; subIssuesTotal.value = response.total; subIssuesCompleted.value = response.completed
  } catch (requestError: unknown) { subIssuesError.value = getApiErrorMessage(requestError, '子议题加载失败，请重试。') }
  finally { subIssuesLoading.value = false }
}

async function retryTags() {
  tagsLoading.value = true
  tagsError.value = ''
  try { availableTags.value = await fetchTags() }
  catch (requestError: unknown) { tagsError.value = getApiErrorMessage(requestError, '标签加载失败，请重试。') }
  finally { tagsLoading.value = false }
}

const canManageIssueTags = computed(() => Boolean(issue.value && (auth.user?.username === issue.value.author || auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN')))
const canEditIssue = computed(() => Boolean(issue.value && (auth.user?.username === issue.value.author || auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN')))
const canCloseIssue = computed(() => Boolean(issue.value && auth.user && (auth.user.role !== 'USER' || auth.user.username === issue.value.author)))
const canReopenIssue = computed(() => auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN')
const pinning = ref(false)
async function togglePinned() { if (!issue.value) return; pinning.value = true; closeError.value = ''; try { const updated = await setIssuePinned(issue.value.id, !issue.value.pinned); issue.value.pinned = updated.pinned } catch (e: unknown) { closeError.value = getApiErrorMessage(e, '置顶状态更新失败。') } finally { pinning.value = false } }
const canManagePoll = computed(() => auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN')
const canDeletePoll = computed(() => Boolean(poll.value && auth.user && (poll.value.creatorId === auth.user.id || auth.user.role === 'STAFF' || auth.user.role === 'ADMIN')))
const canCreateSubIssue = computed(() => Boolean(issue.value && (auth.user?.username === issue.value.author || auth.user?.role === 'STAFF' || auth.user?.role === 'ADMIN')))
const canDeleteAttachment = (attachment: IssueAttachmentResponse) => Boolean(auth.user && (attachment.uploaderId === auth.user.id || auth.user.role === 'STAFF' || auth.user.role === 'ADMIN' || auth.user.username === issue.value?.author))

async function loadAttachments() {
  if (!issue.value) return
  attachmentsLoading.value = true; attachmentError.value = ''
  try { attachments.value = await fetchIssueAttachments(issue.value.id) }
  catch (error: unknown) { attachmentError.value = getApiErrorMessage(error, '附件加载失败，请重试。') }
  finally { attachmentsLoading.value = false }
}

async function uploadAttachment(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!issue.value || !file) return
  attachmentActionLoading.value = true; attachmentError.value = ''
  try { attachments.value.push(await uploadIssueAttachment(issue.value.id, file)); input.value = '' }
  catch (error: unknown) { attachmentError.value = getApiErrorMessage(error, '附件上传失败。请检查文件类型和大小。') }
  finally { attachmentActionLoading.value = false }
}

async function removeAttachment(attachment: IssueAttachmentResponse) {
  if (!issue.value) return
  attachmentActionLoading.value = true; attachmentError.value = ''
  try { await deleteIssueAttachment(issue.value.id, attachment.id); attachments.value = attachments.value.filter((item) => item.id !== attachment.id) }
  catch (error: unknown) { attachmentError.value = getApiErrorMessage(error, '附件删除失败。') }
  finally { attachmentActionLoading.value = false }
}

async function downloadAttachment(attachment: IssueAttachmentResponse) {
  if (!issue.value) return
  attachmentError.value = ''
  try { await downloadIssueAttachment(issue.value.id, attachment) }
  catch (error: unknown) { attachmentError.value = getApiErrorMessage(error, '附件下载失败。') }
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
function beginIssueEdit() { if (!issue.value) return; editTitle.value = issue.value.title; editBody.value = issue.value.body; issueEditError.value = ''; editingIssue.value = true }
async function saveIssueEdit() { if (!issue.value) return; if (!hasTextWithin(editTitle.value, 1, 120) || !hasTextWithin(editBody.value, 1, 20000)) { issueEditError.value = '标题长度须为 1-120 个字符，正文长度须为 1-20000 个字符。'; return } issueEditLoading.value = true; issueEditError.value = ''; try { await editIssue(issue.value.id, { title: editTitle.value.trim(), body: editBody.value.trim() }); await store.loadDetailFromApi(issue.value.id); editingIssue.value = false } catch (e: unknown) { issueEditError.value = getCommentActionError(e, '反馈编辑失败，请稍后重试。') } finally { issueEditLoading.value = false } }
async function confirmIssueDelete() { if (!issue.value) return; deletingIssue.value = true; deleteIssueError.value = ''; try { await deleteIssue(issue.value.id); router.replace('/') } catch (e: unknown) { deleteIssueError.value = getCommentActionError(e, '反馈删除失败，请稍后重试。') } finally { deletingIssue.value = false } }
const unboundTags = computed(() => availableTags.value.filter((tag) => !issue.value?.tags.some((current) => current.id === tag.id)))

async function bindSelectedTag() {
  if (!issue.value || selectedTagId.value === null) return
  tagActionLoading.value = true; tagActionError.value = ''
  try {
    await bindIssueTag(issue.value.id, selectedTagId.value)
    await store.loadDetailFromApi(issue.value.id)
    await store.loadCommentsFromApi(issue.value.id)
    selectedTagId.value = null
  } catch (requestError: unknown) { tagActionError.value = getCommentActionError(requestError, '标签绑定失败，请稍后重试。') }
  finally { tagActionLoading.value = false }
}

async function removeIssueTag(tagId: number) {
  if (!issue.value) return
  if (confirmingTagDelete.value !== tagId) { confirmingTagDelete.value = tagId; return }
  tagActionLoading.value = true; tagActionError.value = ''
  try { await unbindIssueTag(issue.value.id, tagId); await store.loadDetailFromApi(issue.value.id); await store.loadCommentsFromApi(issue.value.id); confirmingTagDelete.value = null }
  catch (requestError: unknown) { tagActionError.value = getCommentActionError(requestError, '标签解绑失败，请稍后重试。') }
  finally { tagActionLoading.value = false }
}

async function addIssueLink() {
  const target = Number(targetIssueId.value)
  if (!issue.value || !Number.isInteger(target) || target <= 0) { linkActionError.value = '请输入有效的目标工单编号。'; return }
  linkActionLoading.value = true; linkActionError.value = ''
  try { const link = await createIssueLink(issue.value.id, target, relationType.value); links.value = [...links.value, link]; targetIssueId.value = '' }
  catch (requestError: unknown) { linkActionError.value = getCommentActionError(requestError, '链接添加失败，请检查目标工单。') }
  finally { linkActionLoading.value = false }
}

async function submitSubIssue() {
  if (!issue.value) return
  const title = subIssueTitle.value.trim(); const body = subIssueBody.value.trim()
  if (!title || title.length > 120 || !body || body.length > 20000) { subIssueError.value = '标题须为 1-120 个字符，正文须为 1-20000 个字符。'; return }
  if (!auth.user) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  creatingSubIssue.value = true; subIssueError.value = ''
  try {
    const created = await createIssueSubIssue(issue.value.id, { title, body })
    subIssues.value = [...subIssues.value, { id: created.id, title: created.title, state: created.state, author: created.author, authorAvatarUrl: created.authorAvatarUrl, createdAt: created.createdAt, updatedAt: created.updatedAt }]
    subIssuesTotal.value++; subIssueTitle.value = ''; subIssueBody.value = ''
  } catch (requestError: unknown) { subIssueError.value = getApiErrorMessage(requestError, '子议题创建失败，请稍后重试。') }
  finally { creatingSubIssue.value = false }
}

async function removeIssueLink(targetId: number) {
  if (!issue.value) return
  linkActionLoading.value = true; linkActionError.value = ''
  try { await deleteIssueLink(issue.value.id, targetId); links.value = links.value.filter((link) => link.targetIssueId !== targetId) }
  catch (requestError: unknown) { linkActionError.value = getCommentActionError(requestError, '链接删除失败，请稍后重试。') }
  finally { linkActionLoading.value = false }
}

async function loadOlderComments() {
  if (!issue.value || store.commentsLoading || store.commentsPage + 1 >= store.commentsTotalPages) return
  const loaded = await store.loadCommentsFromApi(issue.value.id, store.commentsPage + 1)
  commentsError.value = loaded ? '' : '更早讨论加载失败，请重试。'
}

async function submitReply() {
  if (!reply.value.trim()) return
  if (!issue.value) return
  submittingReply.value = true
  replyError.value = ''
  try {
    await store.addCommentFromApi(issue.value.id, reply.value.trim())
    reply.value = ''
    submitted.value = true
  } catch (requestError: unknown) {
    replyError.value = getApiErrorStatus(requestError) === 401 ? '请先登录后再回复。' : getApiErrorMessage(requestError, '回复发送失败，请稍后重试。')
  } finally {
    submittingReply.value = false
  }
}

async function closeCurrentIssue() {
  if (!issue.value) return
  const duplicateId = Number(duplicateOfIssueId.value)
  if (closeReason.value === 'DUPLICATE' && (!Number.isInteger(duplicateId) || duplicateId <= 0 || duplicateId === issue.value.id)) {
    closeError.value = '请选择一个有效且不同的重复工单编号。'
    return
  }
  closing.value = true
  closeError.value = ''
  try {
    await store.closeFromApi(issue.value.id, { reason: closeReason.value, duplicateOfIssueId: closeReason.value === 'DUPLICATE' ? duplicateId : undefined })
    closeMenuOpen.value = false
    duplicateOfIssueId.value = ''
  } catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    closeError.value = status === 401 ? '请先登录后再关闭反馈。' : status === 403 ? '你没有权限关闭这条反馈。' : status === 409 ? '反馈状态已变化，请刷新后重试。' : getApiErrorMessage(requestError, '关闭失败，请稍后重试。')
  } finally {
    closing.value = false
  }
}

async function reopenCurrentIssue() {
  if (!issue.value) return
  reopening.value = true
  closeError.value = ''
  try {
    await store.reopenFromApi(issue.value.id)
  } catch (requestError: unknown) {
    const status = getApiErrorStatus(requestError)
    closeError.value = status === 401 ? '请先登录后再重新打开反馈。' : status === 403 ? '你没有权限重新打开这条反馈。' : status === 409 ? '反馈状态已变化，请刷新后重试。' : getApiErrorMessage(requestError, '重新打开失败，请稍后重试。')
  } finally {
    reopening.value = false
  }
}

function canManageComment(author: string) {
  return auth.user?.username === author || auth.user?.role === 'ADMIN'
}

function beginEditComment(comment: { id: number; text: string }) {
  deletingCommentId.value = null
  commentActionError.value = ''
  editingCommentId.value = comment.id
  editingCommentText.value = comment.text
}

function cancelCommentAction() {
  editingCommentId.value = null
  deletingCommentId.value = null
  editingCommentText.value = ''
  commentActionError.value = ''
}

async function saveComment() {
  const text = editingCommentText.value.trim()
  if (!issue.value || editingCommentId.value === null || !text) return
  commentActionLoading.value = true
  commentActionError.value = ''
  try {
    await store.editCommentFromApi(issue.value.id, editingCommentId.value, text)
    cancelCommentAction()
  } catch (requestError: unknown) {
    commentActionError.value = getCommentActionError(requestError, '评论编辑失败，请稍后重试。')
  } finally {
    commentActionLoading.value = false
  }
}

async function deleteComment(commentId: number) {
  if (!issue.value) return
  commentActionLoading.value = true
  commentActionError.value = ''
  try {
    await store.deleteCommentFromApi(issue.value.id, commentId)
    cancelCommentAction()
  } catch (requestError: unknown) {
    commentActionError.value = getCommentActionError(requestError, '评论删除失败，请稍后重试。')
  } finally {
    commentActionLoading.value = false
  }
}

function getCommentActionError(requestError: unknown, fallback: string) {
  const status = getApiErrorStatus(requestError)
  if (status === 401) return '请先登录后再操作评论。'
  if (status === 403) return '你没有权限操作这条评论。'
  return getApiErrorMessage(requestError, fallback)
}

function linkRelationLabel(link: IssueLinkResponse) {
  if (link.relationType === 'BLOCKS') return link.direction === 'OUTGOING' ? '当前反馈阻塞该工单' : '该工单阻塞当前反馈'
  if (link.relationType === 'DUPLICATES') return link.direction === 'OUTGOING' ? '当前反馈重复于该工单' : '该工单重复于当前反馈'
  return '相关'
}

function closeReasonLabel(reason: 'COMPLETED' | 'NOT_PLANNED' | 'MEANINGLESS' | 'DUPLICATE') {
  if (reason === 'NOT_PLANNED') return '关闭为非计划'
  if (reason === 'MEANINGLESS') return '关闭为无意义'
  if (reason === 'DUPLICATE') return '关闭为重复'
  return '关闭为已完成'
}

</script>

<template>
  <main class="detail-page">
    <button class="back-link" type="button" @click="router.push('/')">SimpIssueHub / 反馈</button>
    <p v-if="loading" class="page-loading">正在加载反馈详情…</p>
    <p v-else-if="loadError && issue" class="list-status warning">{{ loadError }} <button type="button" class="inline-action" @click="loadIssue">重试</button></p>
    <template v-if="issue">
      <header class="issue-detail-header">
        <div>
          <h1>{{ issue.title }} <span>#{{ issue.id }}</span></h1>
          <p><span class="state-pill" :class="issue.state.toLowerCase()">{{ issue.state === 'OPEN' ? '◉ 处理中' : '✓ 已解决' }}</span><strong>{{ issue.author }}</strong> 提交了这条反馈 · 更新于 {{ issue.updatedAt }}</p>
        </div>
        <button class="primary-button" type="button" @click="router.push('/issues/new')">新建反馈</button>
      </header>

      <div class="detail-layout">
        <section class="issue-timeline" aria-label="反馈内容">
          <article class="timeline-entry">
            <UserAvatar :username="issue.author" :url="issue.authorAvatarUrl" size="large" />
            <div class="timeline-card">
              <header><strong>{{ issue.author }}</strong><span>提交于 {{ issue.updatedAt }}</span><button v-if="canEditIssue && !editingIssue" type="button" @click="beginIssueEdit">编辑</button></header>
              <div class="timeline-body">
                <template v-if="editingIssue">
                  <input v-model="editTitle" class="issue-edit-title" maxlength="120" aria-label="反馈标题" />
                  <textarea v-model="editBody" class="issue-edit-body" rows="8" aria-label="反馈正文"></textarea>
                  <div class="detail-actions"><button class="primary-button" :disabled="issueEditLoading || !editTitle.trim() || !editBody.trim()" @click="saveIssueEdit">{{ issueEditLoading ? '保存中…' : '保存修改' }}</button><button class="secondary-button" type="button" :disabled="issueEditLoading" @click="editingIssue = false">取消</button></div>
                  <p v-if="issueEditError" class="form-error">{{ issueEditError }}</p>
                </template>
                <div v-else class="detail-body">{{ issue.body }}</div>
              </div>
            </div>
          </article>

          <section v-if="pollLoading" class="poll-panel poll-status">正在加载投票…</section>
          <section v-else-if="poll" class="poll-panel" aria-labelledby="poll-question">
            <header class="poll-header">
              <div><span>投票</span><h2 id="poll-question">{{ poll.question }}</h2></div>
              <small>{{ poll.multipleChoice ? '可多选' : '单选' }} · {{ poll.participantCount }} 人参与</small>
            </header>
            <div class="poll-options" :role="poll.multipleChoice ? 'group' : 'radiogroup'">
              <button v-for="option in poll.options" :key="option.id" class="poll-option" :class="{ selected: selectedPollOptions.includes(option.id) }" type="button" :role="poll.multipleChoice ? 'checkbox' : 'radio'" :aria-checked="selectedPollOptions.includes(option.id)" @click="togglePollOption(option.id)">
                <span class="poll-control" aria-hidden="true">{{ selectedPollOptions.includes(option.id) ? '✓' : '' }}</span>
                <span class="poll-option-content"><strong>{{ option.text }}</strong><span class="poll-result-track"><span :style="{ width: `${poll.participantCount ? Math.round(option.voteCount / poll.participantCount * 100) : 0}%` }"></span></span></span>
                <span class="poll-option-count">{{ option.voteCount }}</span>
              </button>
            </div>
            <div class="poll-actions">
              <button class="primary-button" type="button" :disabled="pollActionLoading || !selectedPollOptions.length" @click="submitPollVote">{{ pollActionLoading ? '提交中…' : hasPollBallot ? '更新选择' : '提交投票' }}</button>
              <button v-if="hasPollBallot" class="secondary-button" type="button" :disabled="pollActionLoading" @click="withdrawPollVote">撤销投票</button>
              <button v-if="canDeletePoll && poll.participantCount === 0" class="danger-link poll-delete" type="button" :disabled="pollActionLoading" @click="removePoll">删除投票</button>
            </div>
            <p v-if="pollActionError" class="form-error">{{ pollActionError }}</p>
          </section>
          <p v-else-if="pollError" class="list-status warning poll-load-error">{{ pollError }} <button type="button" class="inline-action" @click="loadPoll">重试</button></p>
          <section v-else-if="canManagePoll" class="poll-panel poll-creator">
            <header class="poll-header"><div><span>新建投票</span><h2>收集玩家意见</h2></div></header>
            <label>投票问题<input v-model="pollQuestion" maxlength="200" placeholder="例如：下次活动选择哪个主题？" /></label>
            <div class="poll-option-editor">
              <label v-for="(_, index) in pollOptions" :key="index">选项 {{ index + 1 }}<span><input v-model="pollOptions[index]" maxlength="100" :placeholder="`填写选项 ${index + 1}`" /><button v-if="pollOptions.length > 2" type="button" aria-label="删除该选项" @click="removePollOption(index)">×</button></span></label>
            </div>
            <button v-if="pollOptions.length < 10" class="inline-action add-poll-option" type="button" @click="addPollOption">+ 添加选项</button>
            <label class="poll-multiple-toggle"><input v-model="pollMultipleChoice" type="checkbox" /><span>允许用户选择多个选项</span></label>
            <div class="poll-actions"><button class="primary-button" type="button" :disabled="pollActionLoading" @click="createPoll">{{ pollActionLoading ? '创建中…' : '创建投票' }}</button></div>
            <p v-if="pollActionError" class="form-error">{{ pollActionError }}</p>
          </section>
        </section>

        <aside class="issue-sidebar" aria-label="反馈元数据">
          <section class="meta-section attachment-panel"><h2>附件 <span>{{ attachments.length }}</span></h2><p v-if="attachmentsLoading" class="list-status">正在加载…</p><ul v-else-if="attachments.length" class="attachment-list"><li v-for="attachment in attachments" :key="attachment.id" class="attachment-row"><button type="button" class="inline-action attachment-name" @click="downloadAttachment(attachment)">{{ attachment.name }}<small class="attachment-meta">{{ formatFileSize(attachment.sizeBytes) }} · {{ attachment.uploader }}</small></button><span class="attachment-actions"><button v-if="canDeleteAttachment(attachment)" type="button" class="danger-link" :disabled="attachmentActionLoading" title="删除附件" @click="removeAttachment(attachment)">删除</button></span></li></ul><p v-else class="meta-empty">暂无附件</p><label v-if="auth.user" class="attachment-upload"><input type="file" :disabled="attachmentActionLoading" accept=".png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.zip,.pdf,.txt,.log" @change="uploadAttachment" /><span>{{ attachmentActionLoading ? '处理中…' : '上传文件' }}</span></label><p class="meta-empty">支持图片、视频、ZIP、PDF 和文本；类型与大小由站点配置限制。</p><p v-if="attachmentError" class="form-error">{{ attachmentError }} <button type="button" class="inline-action" @click="loadAttachments">重试</button></p></section>
          <section v-if="issue.parentIssueId" class="meta-section"><h2>父议题</h2><RouterLink class="parent-issue-link" :to="`/issues/${issue.parentIssueId}`">#{{ issue.parentIssueId }} {{ issue.parentIssueTitle }}</RouterLink></section>
          <section class="meta-section sub-issues-section"><h2>子议题 <span>{{ subIssuesTotal }}</span></h2><div v-if="subIssuesTotal" class="sub-issue-progress"><span>{{ subIssuesCompleted }}/{{ subIssuesTotal }} 已完成</span><div class="sub-issue-progress-track"><span :style="{ width: `${Math.round(subIssuesCompleted / subIssuesTotal * 100)}%` }"></span></div></div><p v-if="subIssuesLoading" class="list-status">正在加载…</p><p v-else-if="subIssuesError" class="list-status warning">{{ subIssuesError }} <button type="button" class="inline-action" @click="retrySubIssues">重试</button></p><div v-else-if="subIssues.length" class="sub-issues-list"><RouterLink v-for="child in subIssues" :key="child.id" class="sub-issue-row" :to="`/issues/${child.id}`"><span class="sub-issue-state" :class="child.state.toLowerCase()">{{ child.state === 'CLOSED' ? '✓' : '○' }}</span><span>#{{ child.id }} {{ child.title }}</span></RouterLink></div><p v-else-if="!subIssuesLoading" class="meta-empty">暂无子议题</p><form v-if="canCreateSubIssue" class="sub-issue-form" @submit.prevent="submitSubIssue"><input v-model="subIssueTitle" maxlength="120" placeholder="子议题标题" aria-label="子议题标题" /><textarea v-model="subIssueBody" maxlength="20000" rows="3" placeholder="描述需要拆分的问题" aria-label="子议题正文"></textarea><button class="secondary-button" type="submit" :disabled="creatingSubIssue">{{ creatingSubIssue ? '创建中…' : '创建子议题' }}</button></form><p v-if="subIssueError" class="form-error">{{ subIssueError }}</p></section>
          <section class="meta-section"><h2>属于的服务器</h2><span class="tag category-badge">{{ issue.category }}</span></section>
          <section class="meta-section"><h2>标签</h2><div class="issue-tag-list"><span v-for="tag in issue.tags" :key="tag.id" class="tag-chip" :style="{ color: tag.color }">{{ tag.name }}<button v-if="canManageIssueTags && confirmingTagDelete !== tag.id" type="button" aria-label="解绑标签" :disabled="tagActionLoading" @click="removeIssueTag(tag.id)">×</button><template v-else-if="canManageIssueTags"><button type="button" class="danger-link" :disabled="tagActionLoading" @click="removeIssueTag(tag.id)">确认</button><button type="button" @click="confirmingTagDelete = null">取消</button></template></span><span v-if="!issue.tags.length" class="meta-empty">暂无标签</span></div><div v-if="canManageIssueTags" class="issue-tag-manager"><p v-if="tagsLoading" class="list-status">正在加载标签…</p><p v-else-if="tagsError" class="list-status warning">{{ tagsError }} <button type="button" class="inline-action" @click="retryTags">重试</button></p><div v-else-if="unboundTags.length" class="tag-bind-row"><AppSelect v-model="selectedTagId" :options="unboundTags.map((tag) => ({ value: tag.id, label: tag.name }))" placeholder="添加标签" ariaLabel="添加标签" /><button class="secondary-button tag-bind-button" type="button" :disabled="tagActionLoading || selectedTagId === null" @click="bindSelectedTag">添加</button></div><p v-if="tagActionError" class="form-error">{{ tagActionError }}</p></div></section>
          <section class="meta-section"><h2>通知</h2><button class="secondary-button subscription-button" type="button" :disabled="subscriptionLoading" :aria-pressed="subscribed" @click="toggleSubscription"><span aria-hidden="true">◉</span>{{ subscriptionLoading ? '处理中…' : auth.user ? subscribed ? '取消订阅' : '订阅' : '登录后订阅' }}<strong v-if="auth.user">{{ subscriberCount }}</strong></button><p v-if="subscriptionError" class="form-error subscription-error">{{ subscriptionError }} <button type="button" class="inline-action" @click="loadSubscription">重试</button></p></section>
          <section class="meta-section"><h2>关联工单 <span>{{ links.length }}</span></h2><p v-if="linksLoading" class="list-status">正在加载…</p><div v-else-if="links.length" class="links-list"><div v-for="link in links" :key="`${link.targetIssueId}-${link.relationType}`" class="link-row"><RouterLink :to="`/issues/${link.targetIssueId}`">#{{ link.targetIssueId }} {{ link.targetTitle }}</RouterLink><span>{{ linkRelationLabel(link) }}</span><button v-if="canManageIssueTags" type="button" class="danger-link" :disabled="linkActionLoading" @click="removeIssueLink(link.targetIssueId)">移除</button></div></div><p v-else-if="linksError" class="list-status warning">{{ linksError }} <button type="button" class="inline-action" @click="retryLinks">重试</button></p><p v-else class="meta-empty">暂无关联工单</p><form v-if="canManageIssueTags" class="link-form" @submit.prevent="addIssueLink"><input v-model="targetIssueId" inputmode="numeric" placeholder="工单编号" aria-label="目标工单编号" /><AppSelect v-model="relationType" :options="relationOptions" ariaLabel="关联关系" /><button class="secondary-button" type="submit" :disabled="linkActionLoading">添加</button></form><p v-if="linkActionError" class="form-error">{{ linkActionError }}</p></section>
          <section class="meta-section issue-actions"><h2>操作</h2><button v-if="canReopenIssue" class="secondary-button pin-button" type="button" :disabled="pinning" @click="togglePinned">{{ pinning ? '更新中…' : issue.pinned ? '取消置顶' : '置顶反馈' }}</button><div v-if="issue.state === 'OPEN' && canCloseIssue" class="close-action"><div class="close-button-group"><button class="secondary-button" type="button" :disabled="closing || reopening" @click="closeCurrentIssue">{{ closing ? '关闭中…' : closeReasonLabel(closeReason) }}</button><button class="secondary-button close-menu-toggle" type="button" aria-label="选择关闭原因" :aria-expanded="closeMenuOpen" :disabled="closing || reopening" @click="closeMenuOpen = !closeMenuOpen">⌄</button></div><div v-if="closeMenuOpen" class="close-reason-menu"><button type="button" :class="{ selected: closeReason === 'COMPLETED' }" @click="closeReason = 'COMPLETED'; closeMenuOpen = false"><strong>✓ 关闭为已完成</strong><small>完成、修复或解决</small></button><button type="button" :class="{ selected: closeReason === 'NOT_PLANNED' }" @click="closeReason = 'NOT_PLANNED'; closeMenuOpen = false"><strong>⊘ 关闭为非计划</strong><small>不修复、无法复现或过期</small></button><button type="button" :class="{ selected: closeReason === 'MEANINGLESS' }" @click="closeReason = 'MEANINGLESS'; closeMenuOpen = false"><strong>⊘ 关闭为无意义</strong><small>内容无效、无关或没有处理价值</small></button><button type="button" :class="{ selected: closeReason === 'DUPLICATE' }" @click="closeReason = 'DUPLICATE'; closeMenuOpen = false"><strong>⊘ 关闭为重复</strong><small>另一个工单已经覆盖此问题</small></button></div><label v-if="closeReason === 'DUPLICATE'" class="duplicate-target">重复工单编号<input v-model="duplicateOfIssueId" inputmode="numeric" placeholder="例如 42" /></label></div><button v-else-if="issue.state === 'CLOSED' && canReopenIssue" class="secondary-button" :disabled="closing || reopening" @click="reopenCurrentIssue">{{ reopening ? '重新打开中…' : '重新打开' }}</button><p v-if="issue.state === 'CLOSED' && issue.closeReason" class="close-reason-summary">关闭原因：{{ issue.closeReason === 'COMPLETED' ? '已完成' : issue.closeReason === 'NOT_PLANNED' ? '非计划' : issue.closeReason === 'MEANINGLESS' ? '无意义' : `重复于 #${issue.duplicateOfIssueId}` }}</p><button v-if="auth.user?.role === 'ADMIN'" class="danger-button" type="button" @click="deleteIssueConfirming = true">删除反馈</button><p v-if="closeError" class="form-error">{{ closeError }}</p><p v-if="deleteIssueError" class="form-error">{{ deleteIssueError }}</p><div v-if="deleteIssueConfirming" class="issue-delete-confirm"><span>确认删除？玩家将无法再访问。</span><button class="danger-button" type="button" :disabled="deletingIssue" @click="confirmIssueDelete">{{ deletingIssue ? '删除中…' : '确认删除' }}</button><button class="secondary-button" type="button" :disabled="deletingIssue" @click="deleteIssueConfirming = false">取消</button></div></section>
        </aside>

        <section class="issue-comments" aria-label="反馈讨论">
          <button v-if="store.commentsPage + 1 < store.commentsTotalPages" class="load-more-comments" type="button" :disabled="store.commentsLoading" @click="loadOlderComments">{{ store.commentsLoading ? '加载中…' : '加载更早讨论' }}</button>
          <p v-if="store.commentsLoading && issue.comments.length === 0" class="list-status">正在加载讨论…</p>
          <p v-else-if="commentsError && issue.comments.length === 0" class="list-status warning">{{ commentsError }} <button type="button" class="inline-action" @click="retryComments">重试</button></p>
          <article v-for="comment in issue.comments" :key="comment.id" class="timeline-entry comment">
            <UserAvatar :username="comment.author" :url="comment.authorAvatarUrl" size="large" />
            <div class="timeline-card comment-content">
              <header><strong>{{ comment.author }}</strong><span>评论于 {{ comment.time }}</span><span v-if="canManageComment(comment.author) && editingCommentId !== comment.id" class="timeline-header-actions"><button type="button" @click="beginEditComment(comment)">编辑</button><button type="button" class="danger-link" @click="editingCommentId = null; deletingCommentId = comment.id; commentActionError = ''">删除</button></span></header>
              <div class="timeline-body">
                <template v-if="editingCommentId === comment.id">
                  <textarea v-model="editingCommentText" class="comment-editor" rows="3" aria-label="编辑评论"></textarea>
                  <div class="comment-actions"><button type="button" :disabled="commentActionLoading || !editingCommentText.trim()" @click="saveComment">{{ commentActionLoading ? '保存中…' : '保存' }}</button><button type="button" :disabled="commentActionLoading" @click="cancelCommentAction">取消</button></div>
                </template>
                <p v-else>{{ comment.text }}</p>
                <div v-if="deletingCommentId === comment.id" class="delete-confirm"><span>确认删除这条评论？</span><button type="button" class="danger-link" :disabled="commentActionLoading" @click="deleteComment(comment.id)">{{ commentActionLoading ? '删除中…' : '确认删除' }}</button><button type="button" :disabled="commentActionLoading" @click="cancelCommentAction">取消</button></div>
              </div>
            </div>
          </article>
          <p v-if="!store.commentsLoading && !commentsError && issue.comments.length === 0" class="timeline-empty">还没有讨论。</p>
          <p v-if="commentsError && issue.comments.length > 0" class="list-status warning">{{ commentsError }} <button type="button" class="inline-action" @click="retryComments">重试</button></p>
          <small v-if="commentActionError" class="form-error comment-action-error">{{ commentActionError }}</small>

          <article class="timeline-entry reply-entry">
            <UserAvatar :username="auth.user?.username || '访客'" :url="auth.user?.avatarUrl" size="large" />
            <form class="timeline-card reply-form" @submit.prevent="submitReply"><header><strong>{{ auth.user?.username || '访客' }}</strong><span>添加评论</span></header><div class="timeline-body"><textarea v-model="reply" placeholder="写下你的回复…" rows="5"></textarea><div class="reply-actions"><small v-if="submitted" class="success">回复已添加</small><small v-if="replyError" class="form-error">{{ replyError }}</small><button class="primary-button" type="submit" :disabled="submittingReply || !reply.trim()">{{ submittingReply ? '发送中…' : '发表评论' }}</button></div></div></form>
          </article>
        </section>
      </div>
    </template>
    <section v-else-if="!loading" class="form-panel"><h1>反馈不存在</h1><p class="form-lede">该反馈可能已被删除，或链接中的编号有误。</p><button class="primary-button" @click="router.push('/')">返回列表</button></section>
  </main>
</template>
