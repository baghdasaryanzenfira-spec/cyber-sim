import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { en } from './en'
import { hy } from './hy'

export type Language = 'en' | 'hy'

const STORAGE_KEY = 'cybersim.lang'

function storedLanguage(): Language {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'hy' ? 'hy' : 'en'
  } catch {
    return 'en'
  }
}

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, hy: { translation: hy } },
  lng: storedLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false }, // React escapes everything itself
})

i18n.on('languageChanged', (lng) => {
  try {
    localStorage.setItem(STORAGE_KEY, lng)
  } catch {
    // private mode etc. — the choice simply isn't persisted
  }
  document.documentElement.lang = lng
})

document.documentElement.lang = i18n.language

export default i18n
