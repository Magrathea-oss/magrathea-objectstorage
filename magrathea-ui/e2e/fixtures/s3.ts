import type { Page } from '@playwright/test'

export const S3_FIXTURE_BASE_URL = 'http://s3.fixture.test/'

/** Enables the app's optional S3 diagnostic adapter before the first navigation. */
export async function installS3HeadObjectFixture(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const runtimeWindow = window as Window & {
      __MAGRATHEA_S3_HEAD_SIGNER__?: (request: Request, context: { credentialProfile: string }) => Request
    }
    runtimeWindow.__MAGRATHEA_S3_HEAD_SIGNER__ = (request, context) => {
      const headers = new Headers(request.headers)
      headers.set('x-playwright-credential-profile', context.credentialProfile)
      return new Request(request, { headers })
    }
  })

  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.resourceType() !== 'document' || url.origin !== 'http://127.0.0.1:4173') {
      return route.fallback()
    }
    const response = await route.fetch()
    const html = (await response.text()).replace(
      '<meta name="magrathea-s3-diagnostics-base-url" content="" />',
      `<meta name="magrathea-s3-diagnostics-base-url" content="${S3_FIXTURE_BASE_URL}" />`,
    )
    return route.fulfill({ response, body: html, headers: { ...response.headers(), 'content-type': 'text/html; charset=utf-8' } })
  })

  await page.route(`${S3_FIXTURE_BASE_URL}**`, async (route) => {
    if (route.request().method() !== 'HEAD') {
      return route.fulfill({ status: 405, headers: { allow: 'HEAD' } })
    }
    return route.fulfill({
      status: 200,
      headers: {
        'access-control-allow-origin': '*',
        'access-control-expose-headers': 'content-length, etag, x-amz-request-id',
        'content-length': '4096',
        'content-type': 'application/octet-stream',
        etag: '"playwright-fixture-etag"',
        'x-amz-request-id': 'playwright-fixture-request',
      },
    })
  })
}
