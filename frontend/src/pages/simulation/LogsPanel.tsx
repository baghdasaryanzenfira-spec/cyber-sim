import FlagIcon from '@mui/icons-material/Flag'
import OutlinedFlagIcon from '@mui/icons-material/OutlinedFlag'
import { Box, IconButton, MenuItem, Paper, Stack, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { EventView } from '../../api/types'
import { SeverityChip } from '../../components/Chips'
import { TranslateButton } from '../../components/TranslateButton'
import { MONO } from '../../theme'

export function formatTime(iso: string) {
  return new Date(iso).toISOString().substring(11, 19)
}

/**
 * Log / alert viewer with a simple evidence board: students flag log entries they consider evidence.
 * Analyst (SYSTEM) events are shown in the timeline panel instead.
 */
export function LogsPanel({ events, readOnly, onFlag }: {
  events: EventView[]
  readOnly: boolean
  onFlag: (event: EventView) => void
}) {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'all' | 'alerts' | 'evidence'>('all')
  const [source, setSource] = useState('')
  const telemetry = events.filter((e) => e.type !== 'SYSTEM')
  const sources = useMemo(() => [...new Set(telemetry.map((e) => e.source))].sort(), [telemetry])

  const visible = telemetry
    .filter((e) => tab !== 'alerts' || e.type === 'ALERT')
    .filter((e) => tab !== 'evidence' || e.flagged)
    .filter((e) => !source || e.source === source)

  return (
    <Paper sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" color="primary">{t('sim.logsAlerts')}</Typography>
        <TextField select size="small" label={t('sim.source')} value={source} onChange={(e) => setSource(e.target.value)}
          sx={{ minWidth: 170 }}>
          <MenuItem value="">{t('sim.allSources')}</MenuItem>
          {sources.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
      </Stack>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ minHeight: 36, mb: 1 }}>
        <Tab value="all" label={t('sim.tabAll', { count: telemetry.length })} sx={{ minHeight: 36 }} />
        <Tab value="alerts" label={t('sim.tabAlerts', { count: telemetry.filter((e) => e.type === 'ALERT').length })} sx={{ minHeight: 36 }} />
        <Tab value="evidence" label={t('sim.tabEvidence', { count: telemetry.filter((e) => e.flagged).length })} sx={{ minHeight: 36 }} />
      </Tabs>
      <Box sx={{ flexGrow: 1, overflowY: 'auto', maxHeight: 460, fontFamily: MONO, fontSize: 12.5 }}>
        {visible.length === 0 && (
          <Typography color="text.secondary" sx={{ p: 2, fontFamily: 'inherit' }}>
            {tab === 'evidence' ? t('sim.flagPrompt') : t('sim.noEntries')}
          </Typography>
        )}
        {visible.map((e) => (
          <Box key={e.id} sx={{
            display: 'flex', gap: 1, alignItems: 'flex-start', py: 0.75, px: 1, borderBottom: '1px solid',
            borderColor: 'divider',
            bgcolor: e.type === 'ALERT' ? 'rgba(248,113,113,0.06)' : 'transparent',
            borderLeft: e.evidence === true ? '3px solid #34d399' : e.flagged ? '3px solid #fbbf24' : '3px solid transparent',
          }}>
            <Box sx={{ flexGrow: 1, minWidth: 0 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 0.25 }}>
                <Box component="span" sx={{ color: 'text.secondary' }}>{formatTime(e.occurredAt)}</Box>
                <SeverityChip severity={e.severity} />
                <Box component="span" sx={{ color: 'primary.light' }}>{e.source}</Box>
              </Box>
              <Box sx={{ wordBreak: 'break-word', color: e.type === 'ALERT' ? '#fecaca' : 'text.primary' }}>{e.message}</Box>
              <Box sx={{ fontFamily: 'body1.fontFamily' }}><TranslateButton text={e.message} /></Box>
            </Box>
            {!readOnly && (
              <Tooltip title={e.flagged ? t('sim.flagRemove') : t('sim.flagAdd')}>
                <IconButton size="small" onClick={() => onFlag(e)} color={e.flagged ? 'warning' : 'default'}>
                  {e.flagged ? <FlagIcon fontSize="small" /> : <OutlinedFlagIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
            )}
          </Box>
        ))}
      </Box>
    </Paper>
  )
}
