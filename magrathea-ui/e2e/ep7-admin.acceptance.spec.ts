import type { Page } from '@playwright/test'
import { test, expect } from './fixtures/test'
import { S3_FIXTURE_BASE_URL } from './fixtures/s3'

const mainHeading = (page: Page) => page.locator('main h1')

/** REQ-ADMIN-001, REQ-ADMIN-002: browser-visible health and backend evidence. */
test('REQ-ADMIN-001/002 dashboard and backend status remain truthful', async ({ page }) => {
  await page.goto('/admin')
  await expect(mainHeading(page)).toHaveText('Admin dashboard')
  await expect(page.getByText('Admin API process is live')).toBeVisible()
  await expect(page.getByText('not-ready', { exact: true })).toBeVisible()
  await expect(page.getByText(/storage-device-catalog.*not-configured/)).toBeVisible()
  await expect(page.getByText('ready', { exact: true })).toHaveCount(0)

  await page.goto('/admin/backend-status')
  await expect(mainHeading(page)).toHaveText('Backend status')
  await expect(page.getByText('storage-engine', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('magrathea.object-store.backend = storage-engine')).toBeVisible()
  for (const source of ['storage-policies', 'storage-devices', 'disk-sets']) {
    await expect(page.getByText(new RegExp(`target/ep7-admin-api/config/${source}`))).toBeVisible()
  }
  await expect(page.getByText(/target\/ep7-admin-api\/storage-engine.*available/)).toBeVisible()
  await expect(page.getByText(/target\/ep7-admin-api\/recovery.*not-configured/)).toBeVisible()
})

/** REQ-ADMIN-003: the configured storage-class link must use the promised canonical route. */
test('REQ-ADMIN-003 policy catalog links to the canonical read-only detail', async ({ page }) => {
  await page.goto('/admin/storage-policies')
  await expect(page.getByText('Read-only configuration-as-code', { exact: true })).toBeVisible()
  await expect(page.getByText(/Changes require YAML configuration followed by catalog reload or redeployment/)).toBeVisible()
  await expect(page.getByRole('link', { name: /MINIO_STANDARD/ })).toHaveAttribute('href', '/admin/storage-policies/minio-standard')
})

/** REQ-ADMIN-004: policy validation refreshes the catalog and never mutates it. */
test('REQ-ADMIN-004 policy validation is observed as non-persistent', async ({ page }) => {
  const catalogMutationRequests: string[] = []
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.pathname !== '/admin/storage-policies/validate' && /^\/admin\/storage-policies(?:\/[^/]+)?$/.test(url.pathname) && ['POST', 'PUT', 'DELETE'].includes(request.method())) {
      catalogMutationRequests.push(`${request.method()} ${url.pathname}`)
    }
  })
  await page.goto('/admin/storage-policies/validate')
  await page.getByLabel('Storage class ID').fill('ARCHIVE_EC')
  await page.getByLabel('Data blocks').fill('8')
  await page.getByLabel('Parity blocks').fill('4')
  await page.getByLabel('Replication factor').fill('1')
  await page.getByRole('button', { name: 'Validate proposal' }).click()
  await expect(page.getByRole('heading', { name: 'Validation report: Valid' })).toBeVisible()
  await expect(page.getByText('Catalog unchanged — the proposal was not persisted.')).toBeVisible()
  await expect(page.getByText('Before: MINIO_STANDARD')).toBeVisible()
  await expect(page.getByText('After refresh: MINIO_STANDARD')).toBeVisible()
  expect(catalogMutationRequests).toEqual([])
})

/** REQ-ADMIN-005: a failed catalog is replaced, not supplemented, and can recover. */
test('REQ-ADMIN-005 policy catalog failure is recoverable without stale rows', async ({ page }) => {
  let attempts = 0
  await page.route('**/admin/storage-policies', async (route) => {
    if (route.request().resourceType() === 'document') return route.fallback()
    attempts += 1
    if (attempts === 1) {
      return route.fulfill({
        status: 503, contentType: 'application/json',
        json: { error: { code: 'catalog-unavailable', message: 'storage-policy catalog is unavailable', path: '/admin/storage-policies' } },
      })
    }
    return route.fulfill({ status: 200, contentType: 'application/json', json: {
      count: 1, storagePolicies: [{ storageClassId: 'MINIO_STANDARD', replication: { factor: 1 }, _links: {} }], _links: {},
    } })
  })

  await page.goto('/admin/storage-policies')
  await expect(page.getByText(/storage-policy catalog is unavailable/i)).toBeVisible()
  await expect(page.getByText('MINIO_STANDARD')).toHaveCount(0)
  const retry = page.getByRole('button', { name: 'Try again' })
  await retry.focus()
  await expect(retry).toBeFocused()
  await retry.press('Enter')
  await expect(page.getByRole('link', { name: /MINIO_STANDARD/ })).toBeVisible()
  expect(attempts).toBe(2)
})

/** REQ-ADMIN-006, REQ-ADMIN-007: device and topology details expose no mutation UI. */
test('REQ-ADMIN-006/007 device and topology catalogs provide read-only deep links', async ({ page }) => {
  await page.goto('/admin/storage-devices/node-1-disk-0')
  await expect(page.getByText('/data/node-1/disk-0')).toBeVisible()
  await expect(page.getByText('DEGRADED', { exact: true })).toBeVisible()
  await expect(page.getByText('107,374,182,400 bytes')).toBeVisible()
  await expect(page.getByText('26,843,545,600 bytes')).toBeVisible()
  await expect(page.getByText(/No create, edit, retire, or delete action/)).toBeVisible()

  await page.goto('/admin/disk-sets/rack-a')
  await expect(page.getByText(/Failure domain:/)).toContainText('RACK')
  await expect(page.getByRole('link', { name: 'node-1-disk-0' })).toHaveAttribute('href', '/admin/storage-devices/node-1-disk-0')
  await expect(page.getByRole('link', { name: 'node-2-disk-0' })).toHaveAttribute('href', '/admin/storage-devices/node-2-disk-0')
  await expect(page.getByText(/No disk-set or membership mutation action/)).toBeVisible()
})

/** REQ-ADMIN-008: capacity is accounting-only and uses the selected bucket. */
test('REQ-ADMIN-008 bucket capacity lookup exposes accounting without object browsing', async ({ page }) => {
  await page.goto('/admin/capacity')
  await page.getByLabel('Bucket name').fill('archive-2026')
  await page.getByRole('button', { name: 'Look up capacity' }).click()
  await expect(page).toHaveURL(/\/admin\/capacity\/archive-2026$/)
  await expect(mainHeading(page)).toHaveText('Capacity: archive-2026')
  for (const value of ['7,340,032 bytes', '1,048,576 bytes', '10,737,418,240 bytes']) {
    await expect(page.getByText(value)).toBeVisible()
  }
  await expect(page.getByText('2', { exact: true })).toBeVisible()
  await expect(page.getByText('No bucket or object browsing is available.')).toBeVisible()
  await expect(page.locator('main').getByRole('link', { name: /object/i })).toHaveCount(0)
})

/** REQ-ADMIN-009, REQ-ADMIN-010: unavailable provider panels never display fixture evidence. */
test('REQ-ADMIN-009/010 report panels honestly identify unavailable providers', async ({ page }) => {
  for (const route of ['/admin/data-hygiene', '/admin/observability']) {
    await page.goto(route)
    await expect(page.getByRole('heading', { name: 'Provider not configured' })).toHaveCount(3)
    await expect(page.getByText(/report provider is not configured/)).toHaveCount(3)
    await expect(page.locator('pre')).toHaveCount(0)
    await expect(page.getByText(/playwright-fixture|sample|healthy default/i)).toHaveCount(0)
  }
})

/** REQ-ADMIN-011: interception proves destination, method, signer profile, and forbidden-route absence. */
test('REQ-ADMIN-011 HeadObject diagnostic reaches only the configured S3 origin', async ({ page, enableS3HeadObjectFixture }) => {
  await enableS3HeadObjectFixture()
  const observed: Array<{ method: string; url: string; profile?: string }> = []
  page.on('request', (request) => observed.push({
    method: request.method(), url: request.url(), profile: request.headers()['x-playwright-credential-profile'],
  }))

  await page.goto('/admin/s3-diagnostics')
  await page.getByLabel('Credential profile').fill('tenant-a-readonly')
  await page.getByLabel('Bucket').fill('diagnostics-2026')
  await page.getByLabel('Object key').fill('probes/readiness.txt')
  await page.getByRole('button', { name: 'Run HeadObject' }).click()

  await expect(page.getByText('S3 accepted the request')).toBeVisible()
  await expect(page.getByText('200 OK')).toBeVisible()
  await expect(page.getByText('playwright-fixture-request')).toBeVisible()
  await expect(page.getByText('"playwright-fixture-etag"')).toBeVisible()
  await expect(page.getByText('4096')).toBeVisible()
  const diagnostic = observed.find(({ method, url }) => method === 'HEAD' && url.startsWith(S3_FIXTURE_BASE_URL))
  expect(diagnostic).toEqual(expect.objectContaining({
    method: 'HEAD',
    url: `${S3_FIXTURE_BASE_URL}diagnostics-2026/probes/readiness.txt`,
    profile: 'tenant-a-readonly',
  }))
  expect(observed.filter(({ url }) => /\/admin\/(?:objects|buckets\/[^/]+\/objects)|storage-engine/i.test(url))).toEqual([])
})

/** REQ-ADMIN-012, REQ-ADMIN-020: direct URLs, history, breadcrumbs, and persisted locale. */
test('REQ-ADMIN-012/020 deep links survive locale reload and browser history', async ({ page }) => {
  await page.goto('/admin/storage-policies/minio-standard')
  await expect(page.locator('main h2:not(.visually-hidden)').filter({ hasText: 'MINIO_STANDARD' })).toBeVisible()
  const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
  await expect(breadcrumb.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/admin')
  await expect(breadcrumb.getByRole('link', { name: 'Storage policies' })).toHaveAttribute('href', '/admin/storage-policies')

  const focusOrder = () => page.locator('a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])').evaluateAll((elements) =>
    elements.map((element) => `${element.tagName}:${element.getAttribute('href') || element.getAttribute('type') || element.getAttribute('aria-controls') || ''}`),
  )
  const orderBeforeLocaleChange = await focusOrder()
  const locale = page.locator('.product-shell__locale select')
  await locale.focus()
  await locale.press('ArrowDown')
  await expect(locale).toHaveValue('de')
  await expect(locale).toBeFocused()
  expect(await focusOrder()).toEqual(orderBeforeLocaleChange)
  await expect(page.locator('html')).toHaveAttribute('lang', 'de')
  await page.reload()
  await expect(page).toHaveURL(/\/admin\/storage-policies\/minio-standard$/)
  await expect(page.getByRole('combobox', { name: 'Sprache' })).toHaveValue('de')
  await expect(page.locator('html')).toHaveAttribute('lang', 'de')
  await expect(page.locator('#primary-navigation a[aria-current="page"]').filter({ hasText: 'Storage policies' })).toHaveCount(1)

  await breadcrumb.getByRole('link', { name: 'Storage policies' }).click()
  await expect(page).toHaveURL(/\/admin\/storage-policies$/)
  await page.goBack()
  await expect(page).toHaveURL(/\/admin\/storage-policies\/minio-standard$/)
  await page.goForward()
  await expect(page).toHaveURL(/\/admin\/storage-policies$/)
})

/** REQ-ADMIN-013, REQ-ADMIN-021: exhaustive 12-entry fixture in every 360/768/1440 project. */
test('REQ-ADMIN-013/021 every navigation action is keyboard reachable, focused, unclipped, and overflow-free', async ({ page }) => {
  await page.goto('/admin/storage-policies')
  const skip = page.getByRole('link', { name: 'Skip to main content' })
  await page.keyboard.press('Tab')
  await expect(skip).toBeFocused()
  await skip.press('Enter')
  await expect(page.locator('#main-content')).toBeFocused()
  await page.reload()

  await page.evaluate(() => {
    const list = document.querySelector('#primary-navigation ul')
    const existingEntries = list?.querySelectorAll('a').length ?? 0
    for (let number = existingEntries + 1; number <= 12; number += 1) {
      const item = document.createElement('li')
      const link = document.createElement('a')
      link.href = `/example/fixture-${number}`
      link.dataset.fixtureNavigation = String(number)
      link.textContent = `Example entry ${number}`
      item.append(link)
      list?.append(item)
    }
  })

  const width = await page.evaluate(() => window.innerWidth)
  const menu = page.locator('button[aria-controls="primary-navigation"]')
  const navigation = page.locator('#primary-navigation')
  await expect(navigation).toHaveAttribute('aria-label', 'Primary navigation')
  const links = navigation.locator('a')
  await expect(links).toHaveCount(12)

  const firstLink = links.first()
  if (width < 768) {
    for (let presses = 0; presses < 8 && !(await menu.evaluate((element) => element === document.activeElement)); presses += 1) {
      await page.keyboard.press('Tab')
    }
    await expect(menu).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(menu).toHaveAttribute('aria-label', 'Close navigation')
    await expect(menu).toHaveAttribute('aria-expanded', 'true')
    await navigation.evaluate((element) => Promise.all(element.getAnimations().map((animation) => animation.finished)))
  } else {
    await expect(menu).toBeHidden()
  }

  for (let presses = 0; presses < 8 && !(await firstLink.evaluate((element) => element === document.activeElement)); presses += 1) {
    await page.keyboard.press('Tab')
  }

  const navigationBox = await navigation.boundingBox()
  expect(navigationBox).not.toBeNull()
  for (let index = 0; index < 12; index += 1) {
    const link = links.nth(index)
    await expect(link).toBeFocused()
    const focus = await link.evaluate((element) => {
      const style = getComputedStyle(element)
      return { boxShadow: style.boxShadow, outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth }
    })
    expect(focus.boxShadow !== 'none' || (focus.outlineStyle !== 'none' && focus.outlineWidth !== '0px')).toBe(true)
    const box = await link.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.x).toBeGreaterThanOrEqual(navigationBox!.x)
    expect(box!.x + box!.width).toBeLessThanOrEqual(navigationBox!.x + navigationBox!.width + 1)
    expect(box!.y).toBeGreaterThanOrEqual(navigationBox!.y)
    expect(box!.y + box!.height).toBeLessThanOrEqual(navigationBox!.y + navigationBox!.height + 1)
    if (index < 11) await page.keyboard.press('Tab')
  }

  const current = navigation.getByRole('link', { name: 'Storage policies' })
  await expect(current).toHaveAttribute('aria-current', 'page')
  await expect(navigation.locator('[aria-current="page"]')).toHaveCount(1)

  for (let index = 10; index >= 0; index -= 1) {
    await page.keyboard.press('Shift+Tab')
    await expect(links.nth(index)).toBeFocused()
  }

  if (width < 768) {
    await page.keyboard.press('Escape')
    await expect(menu).toBeFocused()
    await expect(menu).toHaveAttribute('aria-label', 'Open navigation')
    await expect(menu).toHaveAttribute('aria-expanded', 'false')
  }

  const overflow = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }))
  expect(overflow.document).toBeLessThanOrEqual(overflow.viewport)
  expect(overflow.body).toBeLessThanOrEqual(overflow.viewport)
})

/** REQ-ADMIN-017: operating-system accessibility preferences alter the rendered shell at runtime. */
test('REQ-ADMIN-017 forced colors preserve focus and reduced motion disables shell movement', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/admin/storage-policies')
  const reduced = await page.locator('#primary-navigation').evaluate((element) => Number.parseFloat(getComputedStyle(element).transitionDuration))
  expect(reduced).toBeLessThanOrEqual(0.00001)

  await page.emulateMedia({ forcedColors: 'active', reducedMotion: 'reduce' })
  await page.keyboard.press('Tab')
  const skip = page.getByRole('link', { name: 'Skip to main content' })
  await expect(skip).toBeFocused()
  const forcedFocus = await skip.evaluate((element) => {
    const style = getComputedStyle(element)
    return { outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth, transitionDuration: style.transitionDuration }
  })
  expect(forcedFocus.outlineStyle).toBe('solid')
  expect(forcedFocus.outlineWidth).toBe('3px')
  expect(Number.parseFloat(forcedFocus.transitionDuration)).toBeLessThanOrEqual(0.00001)

  const width = await page.evaluate(() => window.innerWidth)
  if (width < 768) {
    const menu = page.locator('button[aria-controls="primary-navigation"]')
    for (let presses = 0; presses < 8 && !(await menu.evaluate((element) => element === document.activeElement)); presses += 1) {
      await page.keyboard.press('Tab')
    }
    await page.keyboard.press('Space')
    await expect(menu).toHaveAttribute('aria-expanded', 'true')
    await page.keyboard.press('Escape')
    await expect(menu).toBeFocused()
  } else {
    await expect(page.getByRole('navigation', { name: 'Primary navigation' })).toBeVisible()
  }
})

/** REQ-ADMIN-013, REQ-ADMIN-017..021: real-browser axe scan in every viewport project. */
test('EP-7 rendered administration has no detectable axe violations', async ({ page, axe }, testInfo) => {
  await page.goto('/admin/storage-policies/minio-standard')
  const result = await axe.analyze()
  await testInfo.attach('axe-ep7-scan', { body: JSON.stringify(result, null, 2), contentType: 'application/json' })
  expect(result.violations).toEqual([])
})
