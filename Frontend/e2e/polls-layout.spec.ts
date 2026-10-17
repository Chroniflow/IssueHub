import { test, expect, type Page, type Route } from '@playwright/test'

const poll = {
  id: 7,
  creatorId: 2,
  question: '下次活动应该选择哪个主题？',
  multipleChoice: false,
  participantCount: 12,
  options: [
    { id: 71, text: '建筑比赛', voteCount: 7 },
    { id: 72, text: '生存挑战', voteCount: 5 },
  ],
}

test('ordinary users can vote but cannot see poll creation controls', async ({ page }) => {
  mockPollApi(page, 'USER')
  await page.goto('/polls')
  await expect(page.getByRole('heading', { name: poll.question })).toBeVisible()
  await expect(page.getByRole('heading', { name: '发起社区投票' })).toHaveCount(0)
})

test('poll cards stay aligned on desktop and mobile', async ({ page }) => {
  mockPollApi(page, 'STAFF')
  await page.goto('/polls')
  await expect(page.getByRole('heading', { name: '发起社区投票' })).toBeVisible()

  for (const viewport of [{ width: 1280, height: 800 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport)
    const pageBox = await page.locator('.polls-page').boundingBox()
    const cards = await page.locator('.poll-panel').all()
    expect(pageBox).not.toBeNull()
    for (const card of cards) {
      const box = await card.boundingBox()
      expect(box).not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(pageBox!.x - 1)
      expect(box!.x + box!.width).toBeLessThanOrEqual(pageBox!.x + pageBox!.width + 1)
    }
  }
})

function mockPollApi(page: Page, role: 'USER' | 'STAFF') {
  void page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/users/me') return json(route, 200, { id: 1, username: 'tester', email: 'tester@example.test', role })
    if (path === '/site/config') return json(route, 200, { iconUrl: null, aboutContent: 'About' })
    if (path === '/polls') return json(route, 200, [{ poll, creator: 'staff', creatorId: 2, createdAt: '2026-10-06T00:00:00Z' }])
    if (path === '/polls/7/ballot') return json(route, 200, { optionIds: [] })
    return json(route, 404, { status: 404, code: 'NOT_FOUND', message: '测试 API 未定义' })
  })
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}
