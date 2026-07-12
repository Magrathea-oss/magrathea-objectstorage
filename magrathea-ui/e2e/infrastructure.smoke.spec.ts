import { test, expect } from './fixtures/test'

test('built Admin app launches against browser fixtures', async ({ page, axe }, testInfo) => {
  await page.goto('/admin')

  await expect(page.getByRole('heading', { name: 'Admin dashboard', exact: true, level: 1 })).toBeVisible()
  await expect(page.getByText('Fixture Admin API is available')).toBeVisible()

  const accessibilityScan = await axe.analyze()
  await testInfo.attach('axe-infrastructure-scan', {
    body: JSON.stringify(accessibilityScan, null, 2),
    contentType: 'application/json',
  })
})
