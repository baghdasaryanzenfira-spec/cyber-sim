import { Box, Paper, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { EventView } from '../../api/types'
import { MONO } from '../../theme'
import { formatTime } from './LogsPanel'

const color = (e: EventView) =>
  e.type === 'SYSTEM' ? '#22d3ee' : e.severity === 'CRITICAL' || e.severity === 'HIGH' ? '#f87171' : '#64748b'

/** Chronological incident timeline: attacker/telemetry events (red/grey) and analyst actions (cyan). */
export function TimelinePanel({ events }: { events: EventView[] }) {
  const { t } = useTranslation()
  const important = events.filter((e) => e.type === 'SYSTEM' || e.type === 'ALERT' || e.severity === 'HIGH' || e.severity === 'CRITICAL')
  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="overline" color="primary">{t('sim.timeline')}</Typography>
      <Box sx={{ display: 'flex', gap: 1.5, overflowX: 'auto', pb: 1, mt: 1 }}>
        {important.map((e) => (
          <Box key={e.id} sx={{ minWidth: 200, maxWidth: 240, flexShrink: 0, borderTop: `3px solid ${color(e)}`, pt: 1 }}>
            <Typography sx={{ fontFamily: MONO, fontSize: 12, color: color(e) }}>
              {formatTime(e.occurredAt)} · {e.type === 'SYSTEM' ? t('sim.analyst') : e.source}
            </Typography>
            <Typography variant="body2" sx={{ fontSize: 12.5 }}>{e.message}</Typography>
          </Box>
        ))}
        {important.length === 0 && <Typography color="text.secondary">{t('sim.noEvents')}</Typography>}
      </Box>
    </Paper>
  )
}
