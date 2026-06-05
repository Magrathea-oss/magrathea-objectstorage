import { createI18n } from 'vue-i18n'
import en from './en.json'
import it from './it.json'
import es from './es.json'
import de from './de.json'
import cn from './cn.json'

export const locales = ['en', 'it', 'es', 'de', 'cn']

export const localeNames = {
  en: 'English',
  it: 'Italiano',
  es: 'Español',
  de: 'Deutsch',
  cn: '中文'
}

export default createI18n({
  legacy: false,
  locale: navigator.language?.split('-')[0] || 'en',
  fallbackLocale: 'en',
  messages: { en, it, es, de, cn }
})
