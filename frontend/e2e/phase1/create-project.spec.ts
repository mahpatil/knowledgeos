import { test, expect } from '@playwright/test'

test.describe('Phase 1 – Project Creation', () => {
  test('user can create a software project and see it listed', async ({ page }) => {
    await page.goto('/')

    // Click "New Project" button
    await page.click('button:has-text("New Project")')

    // Fill in the form
    await page.fill('[data-testid="project-name-input"]', 'My Test Software Project')
    await page.selectOption('[data-testid="project-type-select"]', 'software')

    // Submit
    await page.click('button:has-text("Create")')

    // Should redirect to projects list or project detail
    await expect(page.locator('text=My Test Software Project')).toBeVisible({ timeout: 10000 })
  })
})
