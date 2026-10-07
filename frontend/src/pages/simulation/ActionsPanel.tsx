import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import GppMaybeIcon from '@mui/icons-material/GppMaybe'
import SearchIcon from '@mui/icons-material/Search'
import {
  Box, Button, ButtonBase, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Paper, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ActionOption, ActionPhase, ResourceView } from '../../api/types'
import { TranslateButton } from '../../components/TranslateButton'

/**
 * Catalogue of actions the student can choose. Actions only change the simulated environment —
 * there is no free-form command input (safety requirement).
 */
export function ActionsPanel({ actions, resources, disabled, onPerform }: {
  actions: ActionOption[]
  resources: ResourceView[]
  disabled: boolean
  onPerform: (action: ActionOption, note: string) => Promise<void>
}) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<ActionOption | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const resourceName = (key?: string) => resources.find((r) => r.key === key)?.name

  const confirm = async () => {
    if (!selected) return
    setBusy(true)
    try {
      await onPerform(selected, note)
      setSelected(null)
      setNote('')
    } finally {
      setBusy(false)
    }
  }

  const group = (phase: ActionPhase, title: string, icon: React.ReactNode) => (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>{icon}{title}</Typography>
      <Grid container spacing={1}>
        {actions.filter((a) => a.phase === phase).map((a) => (
          <Grid key={a.key} size={{ xs: 12, sm: 6, lg: 4 }}>
            <ButtonBase disabled={disabled} onClick={() => setSelected(a)} sx={{
              width: '100%', textAlign: 'left', display: 'block', p: 1.25, borderRadius: 2, border: '1px solid',
              borderColor: a.performed ? 'success.dark' : 'divider',
              bgcolor: a.performed ? 'rgba(52,211,153,0.06)' : 'rgba(15,23,42,0.6)',
              '&:hover': { borderColor: 'primary.main' },
            }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {a.performed && <CheckCircleIcon color="success" fontSize="small" />}
                <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }}>{a.label}</Typography>
                <Chip size="small" label={a.category} variant="outlined" sx={{ fontSize: 10, height: 20 }} />
              </Box>
              {a.targetResourceKey && (
                <Typography variant="caption" color="text.secondary">{t('sim.target', { name: resourceName(a.targetResourceKey) })}</Typography>
              )}
            </ButtonBase>
          </Grid>
        ))}
      </Grid>
    </Box>
  )

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="overline" color="primary">{t('sim.availableActions')}</Typography>
      {group('INVESTIGATION', t('sim.investigate'), <SearchIcon fontSize="small" color="info" />)}
      {group('RESPONSE', t('sim.respond'), <GppMaybeIcon fontSize="small" color="warning" />)}

      <Dialog open={selected != null} onClose={() => !busy && setSelected(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{selected?.label}</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 0.5 }}>{selected?.description}</Typography>
          {selected && <TranslateButton text={`${selected.label}\n${selected.description}`} />}
          {selected?.targetResourceKey && (
            <Typography variant="body2" color="text.secondary">{t('sim.targetResource', { name: resourceName(selected.targetResourceKey) })}</Typography>
          )}
          {selected?.performed && (
            <Typography variant="body2" color="warning.main" sx={{ mt: 1 }}>{t('sim.alreadyPerformed')}</Typography>
          )}
          <TextField fullWidth multiline minRows={2} label={t('sim.analystNote')} value={note} sx={{ mt: 2 }}
            onChange={(e) => setNote(e.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelected(null)} disabled={busy}>{t('common.cancel')}</Button>
          <Button variant="contained" onClick={confirm} disabled={busy}>{t('sim.execute')}</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
