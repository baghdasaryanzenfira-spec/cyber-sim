import CloseIcon from '@mui/icons-material/Close'
import TranslateIcon from '@mui/icons-material/Translate'
import { Alert, Box, CircularProgress, IconButton, Link, Tooltip, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../api/client'
import { aiApi } from '../api/endpoints'
import type { AiSource } from '../api/types'

const TARGET = 'hy'

/**
 * Translates one piece of content text on demand and shows the result beneath the original.
 *
 * Content (scenario briefings, log lines, AI answers) stays in its authored language — only the reader who needs
 * it asks for a translation, and nothing is stored (ADR-12). Rendered as a quiet inline link so it can sit next to
 * body text without competing with it; `icon` gives a compact variant for dense lists such as the log viewer.
 */
export function TranslateButton({ text, icon = false }: { text: string; icon?: boolean }) {
  const { t, i18n } = useTranslation()
  const [result, setResult] = useState<{ text: string; source: AiSource } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // The UI is already Armenian; what is left untranslated here is the content.
  const label = i18n.language === 'hy' ? t('translate.toHy') : t('translate.toHyFromEn')

  const run = async () => {
    if (result) {
      setResult(null)
      return
    }
    setBusy(true)
    setError(null)
    try {
      setResult(await aiApi.translate(text, TARGET))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const trigger = icon ? (
    <Tooltip title={label}>
      <span>
        <IconButton size="small" onClick={run} disabled={busy || !text} color={result ? 'secondary' : 'default'}>
          {busy ? <CircularProgress size={14} /> : <TranslateIcon sx={{ fontSize: 16 }} />}
        </IconButton>
      </span>
    </Tooltip>
  ) : (
    <Link component="button" type="button" onClick={run} disabled={busy || !text} underline="hover"
      sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontSize: 12, color: 'secondary.main' }}>
      {busy ? <CircularProgress size={12} /> : <TranslateIcon sx={{ fontSize: 14 }} />}
      {result ? t('translate.hide') : label}
    </Link>
  )

  // A normal block, never `display: contents`: the result panel must stack under the trigger rather than
  // becoming a flex item of whatever row the trigger happens to sit in.
  return (
    <Box>
      {trigger}
      {error && <Alert severity="error" sx={{ mt: 0.5, py: 0 }}>{error}</Alert>}
      {result && (
        <Box sx={{
          mt: 0.75, p: 1.25, borderRadius: 2, bgcolor: 'rgba(167,139,250,0.08)',
          border: '1px solid rgba(167,139,250,0.25)', position: 'relative',
        }}>
          <Typography variant="body2" sx={{ whiteSpace: 'pre-line', pr: 3 }}>{result.text}</Typography>
          <Typography variant="caption" color="text.secondary">
            {result.source === 'AI' ? t('translate.byClaude') : t('translate.notTranslated')}
          </Typography>
          <IconButton size="small" onClick={() => setResult(null)} sx={{ position: 'absolute', top: 2, right: 2 }}>
            <CloseIcon sx={{ fontSize: 14 }} />
          </IconButton>
        </Box>
      )}
    </Box>
  )
}
