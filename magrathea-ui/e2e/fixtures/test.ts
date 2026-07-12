import AxeBuilder from '@axe-core/playwright'
import { expect, test as base } from '@playwright/test'
import { installAdminApiFixtures } from './admin-api'
import { installS3HeadObjectFixture } from './s3'

type BrowserAcceptanceFixtures = {
  axe: AxeBuilder
  adminApiFixtures: void
  enableS3HeadObjectFixture: () => Promise<void>
}

export const test = base.extend<BrowserAcceptanceFixtures>({
  adminApiFixtures: [async ({ page }, use) => {
    await installAdminApiFixtures(page)
    await use()
  }, { auto: true }],
  axe: async ({ page }, use) => {
    await use(new AxeBuilder({ page }))
  },
  enableS3HeadObjectFixture: async ({ page }, use) => {
    await use(() => installS3HeadObjectFixture(page))
  },
})

export { expect }
