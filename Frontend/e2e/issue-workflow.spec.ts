import { test, expect, type Page, type Route } from '@playwright/test'

const now = '2026-08-27T04:00:00Z'

test('login, create, comment, close, reopen as admin and reject non-admin access', async ({ page }) => {
  test.setTimeout(45_000)
  const api = mockApi(page)

  await page.goto('/issues/new')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.getByLabel('用户名或邮箱').fill('player')
  await page.getByLabel('密码').fill('password123')
  await page.getByRole('main').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '创建反馈' })).toBeVisible()

  await page.getByLabel('标题').fill('测试反馈流程')
  await page.getByRole('button', { name: '反馈类型' }).click()
  await page.getByRole('option', { name: 'Bug' }).click()
  await page.getByLabel('具体内容').fill('通过浏览器自动化验证创建、评论和状态流转。')
  await page.getByRole('button', { name: '提交反馈' }).click()

  await expect(page).toHaveURL(/\/issues\/200$/)
  await expect(page.getByRole('heading', { name: '测试反馈流程' })).toBeVisible()
  expect(api.issue.tags).toEqual(['bug'])

  const metadata = page.getByRole('complementary', { name: '反馈元数据' })
  const discussion = page.getByRole('region', { name: '反馈讨论' })
  const metadataBox = await metadata.boundingBox()
  const discussionBox = await discussion.boundingBox()
  expect(metadataBox).not.toBeNull()
  expect(discussionBox).not.toBeNull()
  expect(discussionBox!.y).toBeGreaterThanOrEqual(metadataBox!.y + metadataBox!.height)

  await page.getByPlaceholder('写下你的回复…').fill('第一条自动化评论')
  await page.getByRole('button', { name: '发表评论' }).click()
  await expect(page.getByText('第一条自动化评论')).toBeVisible()

  await page.getByRole('button', { name: '关闭为已完成' }).click()
  await expect(page.getByText('✓ 已解决', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '重新打开' })).toHaveCount(0)

  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/\?denied=admin$/)

  await page.getByRole('button', { name: '退出' }).click()
  await page.goto('/login')
  await page.getByLabel('用户名或邮箱').fill('admin')
  await page.getByLabel('密码').fill('password123')
  await page.getByRole('main').getByRole('button', { name: '登录', exact: true }).click()
  await page.goto('/issues/200')
  await page.getByRole('button', { name: '重新打开' }).click()
  await expect(page.getByText('◉ 处理中', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  const editButton = page.getByRole('button', { name: '编辑', exact: true }).last()
  const deleteButton = page.getByRole('button', { name: '删除', exact: true }).last()
  await expect(editButton).toBeVisible()
  await expect(deleteButton).toBeVisible()
  const editBox = await editButton.boundingBox()
  const deleteBox = await deleteButton.boundingBox()
  expect(editBox).not.toBeNull()
  expect(deleteBox).not.toBeNull()
  expect(editBox!.x + editBox!.width).toBeLessThanOrEqual(deleteBox!.x)
})

function mockApi(page: Page) {
  let currentUser: { id: number; username: string; email: string; role: 'USER' | 'STAFF' | 'ADMIN' } | null = null
  const issue = {
    id: 200,
    title: '测试反馈流程',
    body: '通过浏览器自动化验证创建、评论和状态流转。',
    state: 'OPEN' as 'OPEN' | 'CLOSED',
    author: 'player',
    category: '生存服',
    tags: ['bug'],
    tagIds: [3],
    pinned: false,
    createdAt: now,
    updatedAt: now,
    comments: [] as Array<{ id: number; author: string; body: string; createdAt: string }>,
  }

  void page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/users/me' && method === 'GET') return currentUser ? json(route, 200, currentUser) : unauthorized(route)
    if (path === '/auth/login' && method === 'POST') {
      const username = request.postDataJSON().username as string
      const role = username === 'admin' ? 'ADMIN' : username === 'staff' ? 'STAFF' : 'USER'
      currentUser = { id: role === 'USER' ? 1 : role === 'STAFF' ? 2 : 3, username, email: `${username}@example.test`, role }
      return json(route, 200, currentUser)
    }
    if (path === '/auth/logout' && method === 'POST') { currentUser = null; return json(route, 204, undefined) }
    if (path === '/tags' && method === 'GET') return json(route, 200, [
      { id: 1, name: '反馈', color: '#397b63', issueCount: 0 },
      { id: 3, name: 'bug', color: '#b94b45', issueCount: 1 },
    ])
    if (path === '/categories' && method === 'GET') return json(route, 200, [
      { id: 1, name: '生存服', issueCount: 1 },
    ])
    if (path === '/issues' && method === 'POST') {
      const body = request.postDataJSON() as { title: string; body: string; tag: string; category: string }
      Object.assign(issue, { title: body.title, body: body.body, category: body.category, tags: [body.tag], tagIds: [body.tag === 'bug' ? 3 : 1] })
      return json(route, 201, issue)
    }
    if (path === '/issues' && method === 'GET') return json(route, 200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
    if (path === '/issues/counts' && method === 'GET') return json(route, 200, { all: 1, open: issue.state === 'OPEN' ? 1 : 0, closed: issue.state === 'CLOSED' ? 1 : 0 })
    if (path === '/issues/200' && method === 'GET') return json(route, 200, issue)
    if (path === '/issues/200/comments' && method === 'GET') return json(route, 200, { content: issue.comments, totalPages: 1, number: 0, size: 20 })
    if (path === '/issues/200/comments' && method === 'POST') {
      const comment = { id: issue.comments.length + 1, author: currentUser?.username ?? 'unknown', body: request.postDataJSON().body as string, createdAt: now }
      issue.comments.push(comment)
      return json(route, 201, comment)
    }
    if (path === '/issues/200/close' && method === 'POST') { issue.state = 'CLOSED'; return json(route, 200, issue) }
    if (path === '/issues/200/reopen' && method === 'POST') {
      if (currentUser?.role === 'USER') return json(route, 403, { status: 403, code: 'FORBIDDEN', message: '无权限' })
      expect(request.postDataJSON()).toEqual({})
      issue.state = 'OPEN'
      return json(route, 200, issue)
    }
    if (path === '/issues/200/links' && method === 'GET') return json(route, 200, [])
    if (path === '/issues/200/sub-issues' && method === 'GET') return json(route, 200, { items: [], total: 0, completed: 0 })
    if (path === '/issues/200/attachments' && method === 'GET') return json(route, 200, [])
    if (path === '/issues/200/poll' && method === 'GET') return json(route, 404, { status: 404, code: 'NOT_FOUND', message: '投票不存在' })
    if (path === '/issues/200/subscription' && method === 'GET') return json(route, 200, { subscribed: false, subscriberCount: 0 })
    return json(route, 404, { status: 404, code: 'NOT_FOUND', message: '测试 API 未定义' })
  })

  return { issue }
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({ status, contentType: 'application/json', body: body === undefined ? '' : JSON.stringify(body) })
}

function unauthorized(route: Route) {
  return json(route, 401, { status: 401, code: 'UNAUTHENTICATED', message: '请先登录' })
}
