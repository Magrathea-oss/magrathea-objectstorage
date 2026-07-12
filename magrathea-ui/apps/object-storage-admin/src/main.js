import { createApp } from 'vue'
import App from './App.vue'
import router from './router.js'
import i18n from './i18n/index.js'
import { normalizeBrowserLocale, supportedLocales } from './app-localization.js'
import {
  adminApiClientKey,
  createAdminApiClient,
  createS3HeadObjectClient,
  s3HeadObjectClientKey,
} from '@magrathea/object-storage-extension'
import {
  adminDocumentTitleForRoute,
  applyDocumentLocale,
  applyDocumentTitle,
  createBrowserConnectivity,
  createLocalStorageLocalePreference,
  installBrowserNavigationEffects,
  readBrowserRuntimeConfiguration,
} from './browser-adapters'
import { resolveInitialLocale } from '@magrathea/product-shell'
import '@magrathea/product-shell/theme.css'
import '@magrathea/object-storage-extension/style.css'
import './style.css'

async function start() {
  const runtime = readBrowserRuntimeConfiguration(document, window.location)
  const connectivity = createBrowserConnectivity(window, navigator)
  const localePreference = createLocalStorageLocalePreference(window.localStorage)
  const localeSelection = await resolveInitialLocale({
    registrations: supportedLocales,
    defaultLocale: normalizeBrowserLocale(navigator.language),
    preference: localePreference,
    reporter: {
      unsupportedSavedLocale: ({ savedLocale, selectedLocale }) => console.warn(
        'Unsupported saved locale; using a safe registered fallback.',
        { savedLocale, selectedLocale },
      ),
    },
  })
  i18n.global.locale.value = localeSelection.locale
  applyDocumentLocale(document, localeSelection)

  const applyLocale = (registration) => {
    i18n.global.locale.value = registration.locale
    applyDocumentLocale(document, registration)
  }
  const reportEnglishFallback = ({ locale, fallbackLocale, namespace, key, outcome }) => console.info(
    'Localized message used the English fallback.',
    { locale, fallbackLocale, namespace, key, outcome },
  )
  const app = createApp(App, {
    initialLocale: localeSelection.locale,
    localePreference,
    onLocaleApplied: applyLocale,
    onPageTitleChange: (pageTitle) => applyDocumentTitle(document, pageTitle, 'Magrathea Object Storage'),
    onEnglishFallback: reportEnglishFallback,
  })
  app.provide(adminApiClientKey, createAdminApiClient({
    baseUrl: runtime.adminApiBaseUrl,
    isOnline: connectivity.isOnline,
    timeoutMs: 30_000,
  }))

  // A deployment may inject an in-memory signer. Endpoint configuration is public,
  // but credentials are never read from runtime configuration or browser storage.
  const signer = window.__MAGRATHEA_S3_HEAD_SIGNER__
  const s3Client = runtime.s3DiagnosticsBaseUrl && signer
    ? createS3HeadObjectClient({
        s3BaseUrl: runtime.s3DiagnosticsBaseUrl,
        signRequest: signer,
        isOnline: connectivity.isOnline,
        timeoutMs: 30_000,
      })
    : undefined
  app.provide(s3HeadObjectClientKey, s3Client)

  installBrowserNavigationEffects({
    router,
    document,
    productName: 'Magrathea Object Storage',
    titleForRoute: adminDocumentTitleForRoute,
  })

  app.use(i18n).use(router).mount('#app')
}

void start()
