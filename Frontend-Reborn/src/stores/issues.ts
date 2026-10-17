import { defineStore } from 'pinia'
import { closeIssue, createIssue, createIssueComment, deleteIssueComment, editIssueComment, fetchHealth, fetchIssueComments, fetchIssueDetail, fetchIssues, getApiErrorMessage, getApiErrorStatus, reopenIssue, type IssuePollPayload } from '../api/http'
import { formatLocalDateTime } from '../utils/format'

export type IssueState = 'OPEN' | 'CLOSED'

export interface IssueComment {
  id: number
  author: string
  authorAvatarUrl: string | null
  text: string
  time: string
}

export interface Issue {
  id: number
  title: string
  summary: string
  body: string
  author: string
  authorAvatarUrl: string | null
  tag: string
  category: string
  tags: Array<{ id: number; name: string; color: string }>
  state: IssueState
  pollParticipants: number
  updatedAt: string
  comments: IssueComment[]
  parentIssueId?: number | null
  parentIssueTitle?: string | null
  closeReason?: 'COMPLETED' | 'NOT_PLANNED' | 'DUPLICATE' | null
  duplicateOfIssueId?: number | null
  duplicateOfIssueTitle?: string | null
}

export const useIssueStore = defineStore('issues', {
  state: () => ({
    loading: false,
    loadError: '',
    currentPage: 0,
    totalPages: 1,
    totalElements: 0,
    pageSize: 20,
    commentsPage: 0,
    commentsTotalPages: 1,
    commentsLoading: false,
    commentsRequestId: 0,
    issuesRequestId: 0,
    issues: [] as Issue[],
  }),
  getters: {
    byId: (state) => (id: number) => state.issues.find((issue) => issue.id === id),
    openCount: (state) => state.issues.filter((issue) => issue.state === 'OPEN').length,
    closedCount: (state) => state.issues.filter((issue) => issue.state === 'CLOSED').length,
  },
  actions: {
    async loadFromApi(options: { tag?: string; category?: string; state?: IssueState; page?: number; query?: string; author?: string; sort?: 'updatedDesc' | 'updatedAsc' | 'createdDesc' | 'createdAsc' } = {}) {
      const requestId = ++this.issuesRequestId
      this.loading = true
      this.loadError = ''
      const page = Math.max(options.page ?? 0, 0)
      try {
        const response = await fetchIssues({ page, size: this.pageSize, tag: options.tag, category: options.category, state: options.state, query: options.query, author: options.author, sort: options.sort })
        if (requestId !== this.issuesRequestId) return
        this.currentPage = response.number
        this.totalPages = Math.max(response.totalPages, 1)
        this.totalElements = response.totalElements
        this.issues = response.content.map((issue) => ({
          id: issue.id,
          title: issue.title,
          summary: issue.summary,
          body: issue.summary,
          author: issue.author,
          authorAvatarUrl: issue.authorAvatarUrl,
          tag: issue.tags[0] ?? '反馈',
          category: issue.category,
          tags: issue.tags.map((name, index) => ({ id: index, name, color: '#4d8a70' })),
          state: issue.state,
          pollParticipants: issue.pollParticipants,
          updatedAt: formatUpdatedAt(issue.updatedAt),
          comments: [],
        }))
      } catch (requestError: unknown) {
        if (requestId !== this.issuesRequestId) return
        this.loadError = await describeServiceFailure(requestError)
        this.currentPage = 0
        this.totalPages = 1
        this.totalElements = 0
        this.issues = []
      } finally {
        if (requestId === this.issuesRequestId) this.loading = false
      }
    },
    async goToPage(page: number, options: { tag?: string; category?: string; state?: IssueState; query?: string; author?: string; sort?: 'updatedDesc' | 'updatedAsc' | 'createdDesc' | 'createdAsc' } = {}) {
      if (page < 0 || page >= this.totalPages || this.loading) return
      await this.loadFromApi({ ...options, page })
    },
    async loadDetailFromApi(id: number) {
      try {
        const response = await fetchIssueDetail(id)
        const existing = this.byId(id)
        const mapped = {
          id: response.id,
          title: response.title,
          summary: response.body.slice(0, 80),
          body: response.body,
          author: response.author,
          authorAvatarUrl: response.authorAvatarUrl,
          tag: response.tags[0] ?? existing?.tag ?? '反馈',
          category: response.category,
          tags: response.tags.map((name, index) => ({ id: response.tagIds[index] ?? index, name, color: '#4d8a70' })),
          state: response.state,
          pollParticipants: existing?.pollParticipants ?? 0,
          updatedAt: formatUpdatedAt(response.updatedAt),
          comments: response.comments.length ? response.comments.map((comment) => ({
            id: comment.id,
            author: comment.author,
            authorAvatarUrl: comment.authorAvatarUrl,
            text: comment.body,
            time: formatUpdatedAt(comment.createdAt),
          })) : existing?.comments ?? [],
          parentIssueId: response.parentIssueId,
          parentIssueTitle: response.parentIssueTitle,
          closeReason: response.closeReason,
          duplicateOfIssueId: response.duplicateOfIssueId,
          duplicateOfIssueTitle: response.duplicateOfIssueTitle,
        } satisfies Issue
        const index = this.issues.findIndex((item) => item.id === id)
        if (index === -1) this.issues.push(mapped)
        else this.issues[index] = mapped
        return mapped
      } catch (requestError: unknown) {
        if (getApiErrorStatus(requestError) === 404) {
          this.issues = this.issues.filter((item) => item.id !== id)
        }
        return undefined
      }
    },
    async loadCommentsFromApi(id: number, page = 0) {
      const requestId = ++this.commentsRequestId
      this.commentsLoading = true
      try {
        const response = await fetchIssueComments(id, { page, size: this.pageSize })
        const mapped = response.content.slice().reverse().map((comment) => ({
          id: comment.id,
          author: comment.author,
          authorAvatarUrl: comment.authorAvatarUrl,
          text: comment.body,
          time: formatUpdatedAt(comment.createdAt),
        }))
        const issue = this.byId(id)
        if (issue) issue.comments = page === 0 ? mapped : [...mapped, ...issue.comments.filter((item) => !mapped.some((comment) => comment.id === item.id))]
        this.commentsPage = response.number
        this.commentsTotalPages = Math.max(response.totalPages, 1)
        return true
      } catch {
        return false
      } finally {
        if (requestId === this.commentsRequestId) this.commentsLoading = false
      }
    },
    async createFromApi(payload: Pick<Issue, 'title' | 'body' | 'tag' | 'category'> & { poll?: IssuePollPayload }, idempotencyKey?: string) {
      const response = await createIssue({ title: payload.title, body: payload.body, tag: payload.tag, category: payload.category, poll: payload.poll }, idempotencyKey)
      const created = {
        id: response.id,
        title: response.title,
        summary: response.body.slice(0, 80),
        body: response.body,
        author: response.author,
        authorAvatarUrl: response.authorAvatarUrl,
        tag: response.tags[0] ?? payload.tag,
        category: response.category,
        tags: response.tags.map((name, index) => ({ id: response.tagIds[index] ?? index, name, color: '#4d8a70' })),
        state: response.state,
        pollParticipants: 0,
        updatedAt: formatUpdatedAt(response.updatedAt),
        comments: [],
      } satisfies Issue
      this.issues.unshift(created)
      return created.id
    },
    async addCommentFromApi(id: number, text: string) {
      const response = await createIssueComment(id, text)
      const issue = this.byId(id)
      if (issue) issue.comments.push({ id: response.id, author: response.author, authorAvatarUrl: response.authorAvatarUrl, text: response.body, time: formatUpdatedAt(response.createdAt) })
    },
    async editCommentFromApi(issueId: number, commentId: number, text: string) {
      const response = await editIssueComment(issueId, commentId, text)
      const comment = this.byId(issueId)?.comments.find((item) => item.id === commentId)
      if (comment) comment.text = response.body
    },
    async deleteCommentFromApi(issueId: number, commentId: number) {
      await deleteIssueComment(issueId, commentId)
      const issue = this.byId(issueId)
      if (issue) issue.comments = issue.comments.filter((comment) => comment.id !== commentId)
    },
    async closeFromApi(id: number, payload?: { reason?: 'COMPLETED' | 'NOT_PLANNED' | 'DUPLICATE'; duplicateOfIssueId?: number }) {
      const response = await closeIssue(id, payload)
      const issue = this.byId(id)
      if (issue) {
        issue.state = response.state
        issue.updatedAt = formatUpdatedAt(response.updatedAt)
        issue.closeReason = response.closeReason
        issue.duplicateOfIssueId = response.duplicateOfIssueId
        issue.duplicateOfIssueTitle = response.duplicateOfIssueTitle
      }
      return response
    },
    async reopenFromApi(id: number) {
      const response = await reopenIssue(id)
      const issue = this.byId(id)
      if (issue) {
        issue.state = response.state
        issue.updatedAt = formatUpdatedAt(response.updatedAt)
        issue.closeReason = response.closeReason
        issue.duplicateOfIssueId = response.duplicateOfIssueId
        issue.duplicateOfIssueTitle = response.duplicateOfIssueTitle
      }
      return response
    },
    create(payload: Pick<Issue, 'title' | 'body' | 'tag' | 'category'>) {
      const id = Math.max(...this.issues.map((issue) => issue.id), 0) + 1
      this.issues.unshift({ id, ...payload, summary: payload.body.slice(0, 80), author: '你', authorAvatarUrl: null, state: 'OPEN', pollParticipants: 0, updatedAt: '刚刚', tags: [], comments: [] })
      return id
    },
    close(id: number) {
      const issue = this.byId(id)
      if (issue) issue.state = 'CLOSED'
    },
    addComment(id: number, text: string) {
      const issue = this.byId(id)
      if (issue) issue.comments.push({ id: Date.now(), author: '你', authorAvatarUrl: null, text, time: '刚刚' })
    },
  },
})

function formatUpdatedAt(value: string) {
  return formatLocalDateTime(value)
}

async function describeServiceFailure(requestError: unknown) {
  try {
    const health = await fetchHealth()
    if (health.database === 'DOWN') return '数据库当前不可用，请检查服务后重试。'
  } catch (healthError: unknown) {
    const response = healthError as { response?: { status?: number; data?: { database?: string } } }
    if (response.response?.status === 503 && response.response.data?.database === 'DOWN') return '数据库当前不可用，请检查服务后重试。'
    return '后端服务当前不可访问，请稍后重试。'
  }
  return getApiErrorMessage(requestError, '反馈列表请求失败，请稍后重试。')
}
