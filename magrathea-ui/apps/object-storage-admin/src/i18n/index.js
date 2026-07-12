import { createI18n } from 'vue-i18n'
import en from './en.json'
import it from './it.json'
import es from './es.json'
import de from './de.json'
import cn from './cn.json'

export const locales = ['en', 'it', 'es', 'de', 'zh-CN']

export const localeNames = {
  en: 'English',
  it: 'Italiano',
  es: 'Español',
  de: 'Deutsch',
  'zh-CN': '简体中文'
}

export default createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: { en, it, es, de, 'zh-CN': cn }
})
