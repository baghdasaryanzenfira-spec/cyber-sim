import FlagCircleIcon from '@mui/icons-material/FlagCircle'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import TimerIcon from '@mui/icons-material/Timer'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Grid, LinearProgress, Paper,
  Snackbar, Stack, Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { simulationApi } from '../../api/endpoints'
import type { ActionOption, EventView } from '../../api/types'
import { CategoryChip, DifficultyChip, StatusChip } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { TranslateButton } from '../../components/TranslateButton'
import { useLoad } from '../../hooks/useLoad'
import { ActionsPanel } from './ActionsPanel'
import { AssistantPanel } from './AssistantPanel'
import { LogsPanel } from './LogsPanel'
import { ResourcesPanel } from './ResourcesPanel'
import { TimelinePanel } from './TimelinePanel'

function useElapsed(startedAt?: string, completedAt?: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!startedAt || completedAt) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [startedAt, completedAt])
  if (!startedAt) return '00:00'
  const end = completedAt ? new Date(completedAt).getTime() : now
  const seconds = Math.max(0, Math.floor((end - new Date(startedAt).getTime()) / 1000))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

/**
 * The central training screen:
 * scenario header / resources | logs & alerts | AI assistant / available actions / incident timeline.
 */
export function ActiveSimulationPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const simulationId = Number(id)
  const navigate = useNavigate()
  const { data: sim, setData: setSim, error, loading } = useLoad(() => simulationApi.get(simulationId), [simulationId])
  const [actionError, setActionError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [confirmFinish, setConfirmFinish] = useState(false)
  const [busy, setBusy] = useState(false)
  const elapsed = useElapsed(sim?.startedAt, sim?.completedAt)

  useEffect(() => {
    if (sim?.status === 'COMPLETED') navigate(`/simulations/${simulationId}/result`, { replace: true })
  }, [sim?.status, simulationId, navigate])

  if (loading) return <Loading />
  if (!sim) return <ErrorAlert message={error} />

  const active = ['RUNNING', 'INVESTIGATING', 'RESPONDING'].includes(sim.status)

  const begin = async () => {
    setBusy(true)
    try {
      setSim(await simulationApi.start(simulationId))
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const perform = async (action: ActionOption, note: string) => {
    setActionError(null)
    try {
      const result = await simulationApi.act(simulationId, action.key, note || undefined)
      setSim(result.simulation)
      setToast(result.action.resultMessage + (result.revealedEvents > 0 ? `  ${t('sim.newLogEntries', { count: result.revealedEvents })}` : ''))
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  const flag = async (event: EventView) => {
    try {
      setSim(await simulationApi.flag(simulationId, event.id, !event.flagged))
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  const finish = async () => {
    setBusy(true)
    try {
      await simulationApi.complete(simulationId)
      navigate(`/simulations/${simulationId}/result`)
    } catch (e) {
      setActionError(errorMessage(e))
      setBusy(false)
      setConfirmFinish(false)
    }
  }

  const abandon = async () => {
    try {
      await simulationApi.abandon(simulationId)
      navigate('/history')
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  return (
    <>
      <Paper sx={{ p: 2, mb: 2, background: 'linear-gradient(90deg, rgba(34,211,238,0.08), rgba(17,24,39,1) 60%)' }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="overline" color="text.secondary">{t('sim.incident')}</Typography>
            <Typography variant="h5">{sim.scenario.title}</Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
              <StatusChip status={sim.status} />
              <DifficultyChip difficulty={sim.scenario.difficulty} />
              <CategoryChip category={sim.scenario.category} />
            </Stack>
          </Box>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: 'text.secondary' }}>
              <TimerIcon fontSize="small" /><Typography sx={{ fontVariantNumeric: 'tabular-nums' }}>{elapsed}</Typography>
            </Stack>
            <Typography color="text.secondary">{t('sim.actionsCount', { count: sim.performedActions.length })}</Typography>
            {active && (
              <>
                <Button color="inherit" onClick={abandon}>{t('sim.abandon')}</Button>
                <Button variant="contained" color="success" startIcon={<FlagCircleIcon />} onClick={() => setConfirmFinish(true)}>
                  {t('sim.finish')}
                </Button>
              </>
            )}
          </Stack>
        </Stack>
      </Paper>

      <ErrorAlert message={actionError} />

      {sim.status === 'CREATED' && (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" gutterBottom>{t('sim.briefing')}</Typography>
          <Typography color="text.secondary" sx={{ maxWidth: 760, mx: 'auto', whiteSpace: 'pre-line', mb: 1 }}>
            {sim.scenario.description}
          </Typography>
          <Box sx={{ maxWidth: 760, mx: 'auto', mb: 3 }}>
            <TranslateButton text={sim.scenario.description} />
          </Box>
          <Button variant="contained" size="large" startIcon={<PlayArrowIcon />} onClick={begin} disabled={busy}>
            {t('sim.begin')}
          </Button>
        </Paper>
      )}

      {sim.status === 'ABANDONED' && <Alert severity="info">{t('sim.abandoned')}</Alert>}

      {sim.status !== 'CREATED' && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, md: 6, xl: 3 }} sx={{ order: { xs: 2, xl: 1 } }}><ResourcesPanel resources={sim.resources} /></Grid>
            <Grid size={{ xs: 12, xl: 6 }} sx={{ order: { xs: 1, xl: 2 } }}><LogsPanel events={sim.events} readOnly={!active} onFlag={flag} /></Grid>
            <Grid size={{ xs: 12, md: 6, xl: 3 }} sx={{ order: 3 }}>
              <AssistantPanel simulationId={simulationId} active={active} hintsUsed={sim.hintsUsed}
                hintPenalty={sim.scenario.hintPenalty}
                onHint={(hintsUsed) => setSim({ ...sim, hintsUsed })} />
            </Grid>
          </Grid>
          {active && (
            <Box sx={{ mb: 2 }}>
              <ActionsPanel actions={sim.availableActions} resources={sim.resources} disabled={!active} onPerform={perform} />
            </Box>
          )}
          <TimelinePanel events={sim.events} />
        </>
      )}

      <Snackbar open={toast != null} autoHideDuration={5000} onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert severity="info" variant="filled" onClose={() => setToast(null)}>{toast}</Alert>
      </Snackbar>

      <Dialog open={confirmFinish} onClose={() => !busy && setConfirmFinish(false)}>
        <DialogTitle>{t('sim.finishTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('sim.finishText')}
          </DialogContentText>
          {busy && <LinearProgress sx={{ mt: 2 }} />}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmFinish(false)} disabled={busy}>{t('sim.continueInvestigating')}</Button>
          <Button variant="contained" color="success" onClick={finish} disabled={busy}>{t('sim.finishConfirm')}</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
