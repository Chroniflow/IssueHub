import axios from 'axios'

export interface ApiErrorPayload {
  status?: number
  code?: string
  message?: string
  fields?: Record<string, string>
}

export function getApiErrorStatus(error: unknown) {
  if (axios.isAxiosError(error)) return error.response?.status
  return undefined
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return fallback
  if (!error.response) return '网络连接失败，请检查服务是否已启动。'
  const payload = error.response.data as ApiErrorPayload | undefined
  if (!payload) return fallback
  const fields = payload.fields ? Object.values(payload.fields).filter(Boolean) : []
  if (fields.length) return `${payload.message ?? fallback}：${fields.join('；')}`
  return payload.message ?? fallback
}

export function createIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `sih-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function idempotencyConfig(key?: string) {
  return key ? { headers: { 'Idempotency-Key': key } } : undefined
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 8000,
  headers: { 'Content-Type': 'application/json' },
})

export interface HealthResponse {
  status: 'UP' | 'DOWN'
  service: string
  database: 'UP' | 'DOWN'
  message: string | null
  timestamp: string
}

export interface SiteConfigResponse {
  iconUrl: string | null
}

export async function fetchSiteConfig() {
  const { data } = await http.get<SiteConfigResponse>('/site/config', { timeout: 2500 })
  return data
}

export async function fetchHealth() {
  const { data } = await http.get<HealthResponse>('/health', { timeout: 2500 })
  return data
}

export async function requestPasswordReset(usernameOrEmail: string) {
  const { data } = await http.post<{ message: string }>('/auth/password-reset/request', { usernameOrEmail })
  return data
}

export async function confirmPasswordReset(token: string, newPassword: string) {
  const { data } = await http.post<{ message: string }>('/auth/password-reset/confirm', { token, newPassword })
  return data
}

export interface RegistrationConfig {
  emailVerificationRequired: boolean
  turnstileEnabled: boolean
  turnstileRequired: boolean
}

export async function fetchRegistrationConfig() {
  const { data } = await http.get<RegistrationConfig>('/auth/registration/config')
  return data
}

export async function requestRegistrationCode(email: string, turnstileToken?: string) {
  const { data } = await http.post<{ message: string }>('/auth/registration/code', { email, turnstileToken })
  return data
}

export interface IssueListResponse {
  content: Array<{
    id: number
    title: string
    summary: string
    state: 'OPEN' | 'CLOSED'
    author: string
    authorAvatarUrl: string | null
    category: string
    tags: string[]
    pollParticipants: number
    createdAt: string
    updatedAt: string
  }>
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface IssueDetailResponse {
  id: number
  title: string
  body: string
  state: 'OPEN' | 'CLOSED'
  author: string
  authorAvatarUrl: string | null
  category: string
  tags: string[]
  tagIds: number[]
  createdAt: string
  updatedAt: string
  comments: Array<{
    id: number
    author: string
    authorAvatarUrl: string | null
    body: string
    createdAt: string
  }>
  parentIssueId: number | null
  parentIssueTitle: string | null
  closeReason: 'COMPLETED' | 'NOT_PLANNED' | 'DUPLICATE' | null
  duplicateOfIssueId: number | null
  duplicateOfIssueTitle: string | null
}

export interface UserResponse {
  id: number
  username: string
  email: string
  avatarUrl: string | null
  role: 'USER' | 'STAFF' | 'ADMIN'
}

export interface AdminUserResponse extends UserResponse { enabled: boolean; createdAt: string }
export interface AdminUserPageResponse {
  content: AdminUserResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
export async function fetchAdminUsers(params?: { page?: number; size?: number }) { const { data } = await http.get<AdminUserPageResponse>('/admin/users', { params }); return data }
export async function updateAdminUser(id: number, payload: { role?: 'USER' | 'STAFF'; enabled?: boolean }) { const { data } = await http.patch<AdminUserResponse>('/admin/users/' + id, payload); return data }
export async function kickoutAdminUser(id: number) { await http.post('/admin/users/' + id + '/kickout') }
export interface AdminConfigEntry { key: string; label: string; type: 'BOOLEAN' | 'INTEGER' | 'TEXT' | 'TEMPLATE' | 'URL' | 'SECRET'; value: string; environmentLocked: boolean }
export async function fetchAdminConfig() { const { data } = await http.get<AdminConfigEntry[]>('/admin/config'); return data }
export async function updateAdminConfig(key: string, value: string) { const { data } = await http.patch<AdminConfigEntry>('/admin/config', { key, value }); return data }

export interface TagResponse {
  id: number
  name: string
  color: string
  issueCount: number
}
export interface CategoryResponse { id: number; name: string; issueCount: number }
export async function fetchCategories() { const { data } = await http.get<CategoryResponse[]>('/categories'); return data }
export async function createCategory(name: string) { const { data } = await http.post<CategoryResponse>('/categories', { name }); return data }
export async function renameCategory(id: number, name: string) { const { data } = await http.patch<CategoryResponse>('/categories/' + id, { name }); return data }
export async function deleteCategory(id: number) { await http.delete('/categories/' + id) }

export async function fetchIssues(params?: { page?: number; size?: number; state?: 'OPEN' | 'CLOSED'; tag?: string; category?: string; query?: string; author?: string; sort?: 'updatedDesc' | 'updatedAsc' | 'createdDesc' | 'createdAsc' }) {
  const { data } = await http.get<IssueListResponse>('/issues', { params })
  return data
}

export interface IssueCountsResponse { all: number; open: number; closed: number }
export async function fetchIssueCounts(params?: { tag?: string; category?: string; query?: string; author?: string }) { const { data } = await http.get<IssueCountsResponse>('/issues/counts', { params }); return data }

export async function fetchTags() {
  const { data } = await http.get<TagResponse[]>('/tags')
  return data
}

export async function createTag(payload: { name: string; color?: string }) {
  const { data } = await http.post<TagResponse>('/tags', payload)
  return data
}

export async function deleteTag(id: number) {
  await http.delete('/tags/' + id)
}

export async function bindIssueTag(issueId: number, tagId: number, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<IssueDetailResponse>('/issues/' + issueId + '/tags', { tagId }, idempotencyConfig(idempotencyKey))
  return data
}

export async function unbindIssueTag(issueId: number, tagId: number, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.delete<IssueDetailResponse>('/issues/' + issueId + '/tags/' + tagId, idempotencyConfig(idempotencyKey))
  return data
}

export interface IssueLinkResponse { issueId: number; targetIssueId: number; relationType: 'RELATED' | 'BLOCKS' | 'DUPLICATES'; direction: 'OUTGOING' | 'INCOMING'; targetTitle: string }
export async function fetchIssueLinks(issueId: number) {
  const { data } = await http.get<IssueLinkResponse[]>('/issues/' + issueId + '/links')
  return data
}
export async function createIssueLink(issueId: number, targetIssueId: number, relationType = 'RELATED', idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<IssueLinkResponse>('/issues/' + issueId + '/links', { targetIssueId, relationType }, idempotencyConfig(idempotencyKey))
  return data
}
export async function deleteIssueLink(issueId: number, targetIssueId: number, idempotencyKey = createIdempotencyKey()) {
  await http.delete('/issues/' + issueId + '/links/' + targetIssueId, idempotencyConfig(idempotencyKey))
}

export async function fetchIssueDetail(id: number) {
  const { data } = await http.get<IssueDetailResponse>('/issues/' + id)
  return data
}

export interface IssueSubIssueResponse { id: number; title: string; state: 'OPEN' | 'CLOSED'; author: string; authorAvatarUrl: string | null; createdAt: string; updatedAt: string }
export interface IssueSubIssueListResponse { items: IssueSubIssueResponse[]; total: number; completed: number }
export async function fetchIssueSubIssues(id: number) { const { data } = await http.get<IssueSubIssueListResponse>('/issues/' + id + '/sub-issues'); return data }
export async function createIssueSubIssue(id: number, payload: { title: string; body: string; tag?: string; category?: string }, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<IssueDetailResponse>('/issues/' + id + '/sub-issues', payload, idempotencyConfig(idempotencyKey)); return data
}

export interface IssuePollPayload { question: string; multipleChoice: boolean; options: string[] }
export async function createIssue(payload: { title: string; body: string; tag?: string; category: string; poll?: IssuePollPayload }, idempotencyKey?: string) {
  const { data } = await http.post<IssueDetailResponse>('/issues', payload, idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined)
  return data
}
export async function editIssue(id: number, payload: { title: string; body: string }, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.patch<IssueDetailResponse>('/issues/' + id, payload, idempotencyConfig(idempotencyKey))
  return data
}
export async function deleteIssue(id: number, idempotencyKey = createIdempotencyKey()) { await http.delete('/issues/' + id, idempotencyConfig(idempotencyKey)) }
export interface IssueSubscriptionResponse { subscribed: boolean; subscriberCount: number }
export async function fetchIssueSubscription(id: number) { const { data } = await http.get<IssueSubscriptionResponse>('/issues/' + id + '/subscription'); return data }
export async function subscribeToIssue(id: number) { const { data } = await http.put<IssueSubscriptionResponse>('/issues/' + id + '/subscription'); return data }
export async function unsubscribeFromIssue(id: number) { const { data } = await http.delete<IssueSubscriptionResponse>('/issues/' + id + '/subscription'); return data }

export interface IssuePollResponse {
  id: number
  question: string
  multipleChoice: boolean
  participantCount: number
  options: Array<{ id: number; text: string; voteCount: number }>
}
export async function fetchIssuePoll(id: number) { const { data } = await http.get<IssuePollResponse>('/issues/' + id + '/poll'); return data }
export async function createIssuePoll(id: number, payload: { question: string; multipleChoice: boolean; options: string[] }) { const { data } = await http.post<IssuePollResponse>('/issues/' + id + '/poll', payload); return data }
export async function deleteIssuePoll(id: number) { await http.delete('/issues/' + id + '/poll') }
export async function fetchIssuePollBallot(id: number) { const { data } = await http.get<{ optionIds: number[] }>('/issues/' + id + '/poll/ballot'); return data }
export async function submitIssuePollBallot(id: number, optionIds: number[]) { const { data } = await http.put<IssuePollResponse>('/issues/' + id + '/poll/ballot', { optionIds }); return data }
export async function withdrawIssuePollBallot(id: number) { const { data } = await http.delete<IssuePollResponse>('/issues/' + id + '/poll/ballot'); return data }

export interface CommentResponse {
  id: number
  author: string
  authorAvatarUrl: string | null
  body: string
  createdAt: string
}

export interface CommentPageResponse {
  content: CommentResponse[]
  totalPages: number
  number: number
  size: number
}

export async function createIssueComment(id: number, body: string, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<CommentResponse>('/issues/' + id + '/comments', { body }, idempotencyConfig(idempotencyKey))
  return data
}

export async function fetchIssueComments(issueId: number, params?: { page?: number; size?: number }) {
  const { data } = await http.get<CommentPageResponse>('/issues/' + issueId + '/comments', { params })
  return data
}

export async function editIssueComment(issueId: number, commentId: number, body: string, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.patch<CommentResponse>('/issues/' + issueId + '/comments/' + commentId, { body }, idempotencyConfig(idempotencyKey))
  return data
}

export async function deleteIssueComment(issueId: number, commentId: number, idempotencyKey = createIdempotencyKey()) {
  await http.delete('/issues/' + issueId + '/comments/' + commentId, idempotencyConfig(idempotencyKey))
}

export async function closeIssue(id: number, payload?: { reason?: 'COMPLETED' | 'NOT_PLANNED' | 'DUPLICATE'; duplicateOfIssueId?: number }, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<IssueDetailResponse>('/issues/' + id + '/close', payload ?? {}, idempotencyConfig(idempotencyKey))
  return data
}

export async function reopenIssue(id: number, idempotencyKey = createIdempotencyKey()) {
  const { data } = await http.post<IssueDetailResponse>('/issues/' + id + '/reopen', undefined, idempotencyConfig(idempotencyKey))
  return data
}

export async function loginUser(payload: { username: string; password: string; turnstileToken?: string }) {
  const { data } = await http.post<UserResponse>('/auth/login', payload)
  return data
}

export async function registerUser(payload: { username: string; email: string; password: string; verificationCode?: string; turnstileToken?: string }) {
  const { data } = await http.post<UserResponse>('/auth/register', payload)
  return data
}

export async function fetchCurrentUser() {
  const { data } = await http.get<UserResponse>('/users/me')
  return data
}
export async function updateMyAvatar(avatarUrl: string | null) { const { data } = await http.patch<UserResponse>('/users/me/avatar', { avatarUrl }); return data }

export async function logoutUser() {
  await http.post('/auth/logout')
}
export async function changePassword(payload: { currentPassword: string; newPassword: string }) { await http.patch('/auth/me/password', payload) }
export async function changeEmail(payload: { email: string; currentPassword: string }) { const { data } = await http.patch<UserResponse>('/auth/me/email', payload); return data }

export interface ApiTokenView { id: number; name: string; scopes: string; expiresAt: string | null; lastUsedAt: string | null; revokedAt: string | null; createdAt: string }
export interface CreatedApiToken { id: number; name: string; token: string; scopes: string; expiresAt: string | null }
export async function fetchApiTokens() { const { data } = await http.get<ApiTokenView[]>('/users/me/tokens'); return data }
export async function createApiToken(payload: { name: string; permission: 'READ' | 'READ_WRITE'; expiresAt?: string }) { const { data } = await http.post<CreatedApiToken>('/users/me/tokens', payload); return data }
export async function rotateApiToken(id: number) { const { data } = await http.post<CreatedApiToken>('/users/me/tokens/' + id + '/rotate'); return data }
export async function revokeApiToken(id: number) { await http.delete('/users/me/tokens/' + id) }
