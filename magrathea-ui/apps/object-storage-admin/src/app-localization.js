export const supportedLocales = Object.freeze([
  Object.freeze({ locale: 'en', localizedName: 'English', documentLanguage: 'en' }),
  Object.freeze({ locale: 'de', localizedName: 'Deutsch', documentLanguage: 'de' }),
  Object.freeze({ locale: 'es', localizedName: 'Español', documentLanguage: 'es' }),
  Object.freeze({ locale: 'it', localizedName: 'Italiano', documentLanguage: 'it' }),
  Object.freeze({ locale: 'zh-CN', localizedName: '简体中文', documentLanguage: 'zh-CN' }),
])

const shellMessages = Object.freeze({
  en: Object.freeze({ language: 'Language', retry: 'Try again', extensionError: 'The {extension} extension could not be loaded.' }),
  de: Object.freeze({ language: 'Sprache', retry: 'Erneut versuchen', extensionError: 'Die Erweiterung {extension} konnte nicht geladen werden.' }),
  es: Object.freeze({ language: 'Idioma', retry: 'Reintentar', extensionError: 'No se pudo cargar la extensión {extension}.' }),
  it: Object.freeze({ language: 'Lingua', retry: 'Riprova', extensionError: 'Impossibile caricare l’estensione {extension}.' }),
  'zh-CN': Object.freeze({ language: '语言', retry: '重试', extensionError: '无法加载 {extension} 扩展。' }),
})

export function normalizeBrowserLocale(value) {
  if (typeof value !== 'string') return 'en'
  const exact = supportedLocales.find(({ locale }) => locale.toLowerCase() === value.toLowerCase())
  if (exact) return exact.locale
  const language = value.split('-')[0].toLowerCase()
  return supportedLocales.find(({ locale }) => locale.split('-')[0].toLowerCase() === language)?.locale || 'en'
}

export function shellLabels(locale) {
  const messages = shellMessages[locale] || shellMessages.en
  return Object.freeze({
    locale: messages.language,
    retry: messages.retry,
    extensionLoadError: (extension) => messages.extensionError.replace('{extension}', extension),
  })
}
