import { ToggleButton, ToggleButtonGroup } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { Language } from '../i18n'

/** EN / ՀԱՅ toggle; the choice is persisted in localStorage by the i18n module. */
export function LanguageSwitcher({ sx }: { sx?: object }) {
  const { i18n, t } = useTranslation()
  const change = (_: unknown, value: Language | null) => {
    if (value) void i18n.changeLanguage(value)
  }
  return (
    <ToggleButtonGroup size="small" exclusive value={i18n.language} onChange={change}
      aria-label={t('common.language')} sx={sx}>
      <ToggleButton value="en" sx={{ px: 1, py: 0.25, fontSize: 12 }}>EN</ToggleButton>
      <ToggleButton value="hy" sx={{ px: 1, py: 0.25, fontSize: 12 }}>ՀԱՅ</ToggleButton>
    </ToggleButtonGroup>
  )
}
