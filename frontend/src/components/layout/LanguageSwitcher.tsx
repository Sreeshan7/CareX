import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { UI_LANGUAGES } from '../../i18n'

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation()
  return (
    <label className="flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm text-slate-600 hover:bg-slate-100">
      <Languages className="h-4 w-4" aria-hidden />
      <span className="sr-only">{t('common.language')}</span>
      <select
        value={i18n.language.slice(0, 2)}
        onChange={(e) => i18n.changeLanguage(e.target.value)}
        className="cursor-pointer bg-transparent text-sm font-medium focus:outline-none"
      >
        {UI_LANGUAGES.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
      </select>
    </label>
  )
}
