import { test, expect } from '@playwright/test'

test('protected and public routes follow the login-visible policy', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible()

  await page.getByRole('link', { name: '关于' }).click()
  await expect(page).toHaveURL(/\/about$/)
  await expect(page.getByRole('heading', { name: '关于这个反馈站' })).toBeVisible()

  await page.goto('/issues/142')
  await expect(page).toHaveURL(/\/login\?redirect=/)

  await page.goto('/this-route-does-not-exist')
  await expect(page.getByRole('heading', { name: '找不到这个页面' })).toBeVisible()
})
