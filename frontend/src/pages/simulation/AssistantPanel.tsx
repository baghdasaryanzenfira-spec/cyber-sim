import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import LightbulbIcon from '@mui/icons-material/Lightbulb'
import SendIcon from '@mui/icons-material/Send'
import { Alert, Box, Button, Chip, CircularProgress, IconButton, Paper, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../../api/client'
import { simulationApi } from '../../api/endpoints'
import type { AiSource } from '../../api/types'
import { TranslateButton } from '../../components/TranslateButton'

interface ChatEntry {
  id: string
  kind: 'HINT' | 'QUESTION'
  question?: string
  answer: string
  source: AiSource
}

/** AI assistant: contextual hints (small score penalty) and free questions about the simulation. */
export function AssistantPanel({ simulationId, active, hintsUsed, hintPenalty, onHint }: {
  simulationId: number
  active: boolean
  hintsUsed: number
  hintPenalty?: number
  onHint: (hintsUsed: number) => void
}) {
  const { t } = useTranslation()
  const [entries, setEntries] = useState<ChatEntry[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    simulationApi.messages(simulationId)
      .then((messages) => setEntries(messages.map((m) => ({
        id: String(m.id), kind: m.type, question: m.question, answer: m.answer, source: m.source,
      }))))
      .catch(() => undefined)
  }, [simulationId])

  // Block body on purpose: newer browsers return a Promise from smooth scrollIntoView, and an effect must
  // return nothing or a cleanup function.
  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [entries.length])

  const run = async (call: () => Promise<{ text: string; source: AiSource; hintsUsed: number; type: 'HINT' | 'QUESTION' }>, q?: string) => {
    setBusy(true)
    setError(null)
    try {
      const reply = await call()
      setEntries((e) => [...e, { id: crypto.randomUUID(), kind: reply.type, question: q, answer: reply.text, source: reply.source }])
      if (reply.type === 'HINT') onHint(reply.hintsUsed)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const ask = (e: FormEvent) => {
    e.preventDefault()
    const q = question.trim()
    if (!q) return
    setQuestion('')
    void run(() => simulationApi.ask(simulationId, q), q)
  }

  return (
    <Paper sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" color="secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <AutoAwesomeIcon fontSize="inherit" /> {t('sim.assistant')}
        </Typography>
        <Chip size="small" label={t('sim.hintsUsed', { count: hintsUsed })} variant="outlined" color="secondary" />
      </Stack>
      <Box sx={{ flexGrow: 1, overflowY: 'auto', maxHeight: 380, minHeight: 200, my: 1 }}>
        {entries.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t('sim.assistantIntro')}
          </Typography>
        )}
        {entries.map((entry) => (
          <Box key={entry.id} sx={{ mb: 1.5 }}>
            {entry.question && (
              <Box sx={{ ml: 4, p: 1, borderRadius: 2, bgcolor: 'rgba(34,211,238,0.10)', mb: 0.75 }}>
                <Typography variant="body2">{entry.question}</Typography>
              </Box>
            )}
            <Box sx={{ mr: 2, p: 1.25, borderRadius: 2, bgcolor: 'rgba(167,139,250,0.10)', border: '1px solid rgba(167,139,250,0.2)' }}>
              {entry.kind === 'HINT' && (
                <Typography variant="caption" color="secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <LightbulbIcon fontSize="inherit" /> {t('sim.hint')}
                </Typography>
              )}
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{entry.answer}</Typography>
              <TranslateButton text={entry.answer} />
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {t(`aiSource.${entry.source}`)}
              </Typography>
            </Box>
          </Box>
        ))}
        {busy && <CircularProgress size={20} sx={{ m: 1 }} />}
        <div ref={bottom} />
      </Box>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      {active && (
        <>
          <Button variant="outlined" color="secondary" startIcon={<LightbulbIcon />} disabled={busy}
            onClick={() => run(() => simulationApi.hint(simulationId))} sx={{ mb: 1 }}>
            {t('sim.getHint')}{hintPenalty ? t('sim.hintPenalty', { count: hintPenalty }) : ''}
          </Button>
          <Box component="form" onSubmit={ask} sx={{ display: 'flex', gap: 1 }}>
            <TextField size="small" fullWidth placeholder={t('sim.askPlaceholder')} value={question}
              onChange={(e) => setQuestion(e.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
            <IconButton type="submit" color="secondary" disabled={busy || !question.trim()}><SendIcon /></IconButton>
          </Box>
        </>
      )}
    </Paper>
  )
}
