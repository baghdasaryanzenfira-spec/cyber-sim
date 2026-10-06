import { Box, Paper, Typography } from '@mui/material'
import type { ReactNode } from 'react'

export function StatTile({ label, value, hint, icon, color = 'primary.main' }: {
  label: string
  value: ReactNode
  hint?: string
  icon?: ReactNode
  color?: string
}) {
  return (
    <Paper sx={{ p: 2.5, height: '100%' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Typography variant="overline" color="text.secondary">{label}</Typography>
        {icon && <Box sx={{ color }}>{icon}</Box>}
      </Box>
      <Typography variant="h4" sx={{ color, mt: 0.5 }}>{value ?? '—'}</Typography>
      {hint && <Typography variant="body2" color="text.secondary">{hint}</Typography>}
    </Paper>
  )
}
