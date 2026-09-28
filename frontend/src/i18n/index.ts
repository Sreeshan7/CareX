import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import hi from './locales/hi.json'
import ta from './locales/ta.json'

export const UI_LANGUAGES = [
  { code: 'en', label: 'English', assistant: 'en-IN' },
  { code: 'hi', label: 'हिन्दी', assistant: 'hi-IN' },
  { code: 'ta', label: 'தமிழ்', assistant: 'ta-IN' },
] as const

function initialLanguage(): string {
  try {
    const saved = localStorage.getItem('carex.lang')
    if (saved && ['en', 'hi', 'ta'].includes(saved)) return saved
  } catch { /* ignore */ }
  return 'en'
}

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, hi: { translation: hi }, ta: { translation: ta } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
})

i18n.on('languageChanged', (lng) => {
  try { localStorage.setItem('carex.lang', lng) } catch { /* ignore */ }
  document.documentElement.lang = lng
})

export default i18n
