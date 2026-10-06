import FlagCircleIcon from '@mui/icons-material/FlagCircle'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import TimerIcon from '@mui/icons-material/Timer'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Grid, LinearProgress, Paper,
  Snackbar, Stack, Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { simulationApi } from '../../api/endpoints'
import type { ActionOption, EventView } from '../../api/types'
import { CategoryChip, DifficultyChip, StatusChip } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
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
      setToast(result.action.resultMessage + (result.revealedEvents > 0 ? `  (+${result.revealedEvents} new log entries)` : ''))
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
            <Typography variant="overline" color="text.secondary">Incident</Typography>
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
            <Typography color="text.secondary">{sim.performedActions.length} actions</Typography>
            {active && (
              <>
                <Button color="inherit" onClick={abandon}>Abandon</Button>
                <Button variant="contained" color="success" startIcon={<FlagCircleIcon />} onClick={() => setConfirmFinish(true)}>
                  Finish &amp; get score
                </Button>
              </>
            )}
          </Stack>
        </Stack>
      </Paper>

      <ErrorAlert message={actionError} />

      {sim.status === 'CREATED' && (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" gutterBottom>Briefing</Typography>
          <Typography color="text.secondary" sx={{ maxWidth: 760, mx: 'auto', whiteSpace: 'pre-line', mb: 3 }}>
            {sim.scenario.description}
          </Typography>
          <Button variant="contained" size="large" startIcon={<PlayArrowIcon />} onClick={begin} disabled={busy}>
            Begin incident response
          </Button>
        </Paper>
      )}

      {sim.status === 'ABANDONED' && <Alert severity="info">This simulation was abandoned.</Alert>}

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
        <DialogTitle>Finish the simulation?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Your actions will be scored and the AI tutor will analyse your response. You cannot perform further actions afterwards.
          </DialogContentText>
          {busy && <LinearProgress sx={{ mt: 2 }} />}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmFinish(false)} disabled={busy}>Continue investigating</Button>
          <Button variant="contained" color="success" onClick={finish} disabled={busy}>Finish</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
