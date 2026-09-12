import { defineConfig, devices } from '@playwright/test'

const applicationBase = process.env.AACV_E2E_BASE === '/crawler' ? '/crawler/' : '/'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    channel: 'msedge',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'desktop-edge',
      use: { ...devices['Desktop Edge'] },
    },
  ],
  webServer: {
    command: `npm run ${process.env.AACV_E2E_PREVIEW === '1' ? 'preview' : 'dev'} -- --host 127.0.0.1 --port 4173 --strictPort --base=${applicationBase}`,
    url: `http://127.0.0.1:4173${applicationBase}`,
    reuseExistingServer: false,
    timeout: 30_000,
  },
})
