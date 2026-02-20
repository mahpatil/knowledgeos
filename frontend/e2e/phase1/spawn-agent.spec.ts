import { test, expect } from '@playwright/test'

test.describe('Phase 1 – Agent Spawning', () => {
  test('user can spawn an agent and see terminal output', async ({ page }) => {
    await page.goto('/')

    // Create a project first
    await page.click('button:has-text("New Project")')
    await page.fill('[data-testid="project-name-input"]', 'Agent Terminal Test')
    await page.selectOption('[data-testid="project-type-select"]', 'software')
    await page.click('button:has-text("Create")')

    // Navigate to project detail
    await page.click('text=Agent Terminal Test')

    // Create workspace
    await page.click('button:has-text("Add Workspace")')
    await page.fill('[data-testid="workspace-name-input"]', 'main')
    await page.selectOption('[data-testid="workspace-type-select"]', 'code')
    await page.click('button:has-text("Create Workspace")')

    // Spawn an agent
    await page.click('button:has-text("Spawn Agent")')
    await page.fill('[data-testid="agent-name-input"]', 'Implementer Agent')
    await page.selectOption('[data-testid="agent-model-select"]', 'claude')
    await page.selectOption('[data-testid="agent-role-select"]', 'Implementer')
    await page.click('button:has-text("Spawn")')

    // Terminal should appear and eventually show output
    await expect(page.locator('[data-testid="terminal-container"]')).toBeVisible({ timeout: 30000 })
  })
})
