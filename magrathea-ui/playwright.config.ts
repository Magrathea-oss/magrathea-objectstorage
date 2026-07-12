import { defineConfig } from '@playwright/test'

const ci = Boolean(process.env.CI)

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/playwright',
  fullyParallel: true,
  forbidOnly: ci,
  retries: ci ? 2 : 0,
  workers: ci ? 1 : undefined,
  reporter: ci
    ? [['line'], ['html', { outputFolder: 'playwright-report', open: 'never' }]]
    : [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  expect: { timeout: 5_000 },
  webServer: {
    command: 'npm run preview:e2e',
    url: 'http://127.0.0.1:4173/admin',
    reuseExistingServer: !ci,
    timeout: 30_000,
  },
  projects: [
    { name: 'chromium-360', use: { browserName: 'chromium', viewport: { width: 360, height: 800 } } },
    { name: 'chromium-768', use: { browserName: 'chromium', viewport: { width: 768, height: 1024 } } },
    { name: 'chromium-1440', use: { browserName: 'chromium', viewport: { width: 1440, height: 900 } } },
  ],
})
