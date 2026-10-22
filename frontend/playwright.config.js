import { defineConfig } from '@playwright/test'

if (!process.env.ERP_E2E_BASE_URL || !process.env.ERP_E2E_USER_SUFFIX) {
  throw new Error('Run ./mvnw -Dtest=BrowserFlowIntegrationTest test; the test owns its MySQL data and server.')
}

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: 45000,
  expect: { timeout: 10000 },
  reporter: [['line'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.ERP_E2E_BASE_URL,
    browserName: 'chromium',
    actionTimeout: 10000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
})
