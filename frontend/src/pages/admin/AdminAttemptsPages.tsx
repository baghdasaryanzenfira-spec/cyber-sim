import {
  Box, Button, Chip, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow,
  TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import { adminApi } from '../../api/endpoints'
import type { SimulationStatus } from '../../api/types'
import { StatusChip, scoreColor } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { ResultView } from '../../components/ResultView'
import { useLoad } from '../../hooks/useLoad'
import { LogsPanel } from '../simulation/LogsPanel'
import { ResourcesPanel } from '../simulation/ResourcesPanel'
import { TimelinePanel } from '../simulation/TimelinePanel'

const STATUSES: SimulationStatus[] = ['CREATED', 'RUNNING', 'INVESTIGATING', 'RESPONDING', 'COMPLETED', 'ABANDONED']

export function AttemptsTable({ filter }: { filter: { userId?: number; scenarioId?: number; status?: SimulationStatus } }) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const attempts = useLoad(() => adminApi.attempts({ ...filter, page, size }),
    [filter.userId, filter.scenarioId, filter.status, page, size])

  return (
    <Paper>
      <ErrorAlert message={attempts.error} />
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>#</TableCell><TableCell>Started</TableCell><TableCell>Student</TableCell><TableCell>Scenario</TableCell>
            <TableCell>Status</TableCell><TableCell>Actions</TableCell><TableCell>Hints</TableCell><TableCell>Score</TableCell><TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {attempts.data?.items.map((a) => (
            <TableRow key={a.id} hover>
              <TableCell>{a.id}</TableCell>
              <TableCell>{new Date(a.createdAt).toLocaleString()}</TableCell>
              <TableCell>{a.userName}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{a.userEmail}</Typography></TableCell>
              <TableCell>{a.scenarioTitle}</TableCell>
              <TableCell><StatusChip status={a.status} /></TableCell>
              <TableCell>{a.actionCount}</TableCell>
              <TableCell>{a.hintsUsed}</TableCell>
              <TableCell><Typography color={scoreColor(a.scorePercent)} sx={{ fontWeight: 700 }}>{a.scorePercent ?? '—'}</Typography></TableCell>
              <TableCell align="right"><Button size="small" component={RouterLink} to={`/admin/attempts/${a.id}`}>Details</Button></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <TablePagination component="div" count={attempts.data?.totalItems ?? 0} page={page} rowsPerPage={size}
        onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[10, 20, 50]}
        onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }} />
    </Paper>
  )
}

export function AdminAttemptsPage() {
  const scenarios = useLoad(adminApi.scenarios)
  const [scenarioId, setScenarioId] = useState<number | ''>('')
  const [status, setStatus] = useState<SimulationStatus | ''>('')
  return (
    <>
      <PageHeader title="Simulation attempts" subtitle="Every attempt of every student, with full action history." />
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField select size="small" label="Scenario" value={scenarioId} sx={{ minWidth: 280 }}
          onChange={(e) => setScenarioId(e.target.value === '' ? '' : Number(e.target.value))}>
          <MenuItem value="">All scenarios</MenuItem>
          {scenarios.data?.map((s) => <MenuItem key={s.id} value={s.id}>{s.title}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Status" value={status} sx={{ minWidth: 180 }}
          onChange={(e) => setStatus(e.target.value as SimulationStatus | '')}>
          <MenuItem value="">All</MenuItem>
          {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
      </Stack>
      <AttemptsTable filter={{ scenarioId: scenarioId || undefined, status: status || undefined }} />
    </>
  )
}

export function AdminAttemptDetailPage() {
  const { id } = useParams()
  const detail = useLoad(() => adminApi.attempt(Number(id)), [id])
  if (detail.loading) return <Loading />
  if (!detail.data) return <ErrorAlert message={detail.error} />
  const { attempt, simulation, result, aiInteractions } = detail.data

  return (
    <>
      <PageHeader title={`Attempt #${attempt.id} — ${attempt.scenarioTitle}`}
        subtitle={`${attempt.userName} (${attempt.userEmail}) · started ${new Date(attempt.createdAt).toLocaleString()}`}
        actions={<StatusChip status={attempt.status} />} />
      {result ? <ResultView result={result} /> : (
        <Typography color="text.secondary" sx={{ mb: 2 }}>Not completed yet — showing the current state.</Typography>
      )}
      <Grid container spacing={2} sx={{ my: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}><ResourcesPanel resources={simulation.resources} /></Grid>
        <Grid size={{ xs: 12, md: 8 }}><LogsPanel events={simulation.events} readOnly onFlag={() => undefined} /></Grid>
      </Grid>
      <Box sx={{ mb: 2 }}><TimelinePanel events={simulation.events} /></Box>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" gutterBottom>AI interactions</Typography>
        <Table size="small">
          <TableHead>
            <TableRow><TableCell>Time</TableCell><TableCell>Type</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Request</TableCell><TableCell>Response</TableCell><TableCell align="right">Latency</TableCell></TableRow>
          </TableHead>
          <TableBody>
            {aiInteractions.map((i) => (
              <TableRow key={i.id}>
                <TableCell>{new Date(i.createdAt).toLocaleTimeString()}</TableCell>
                <TableCell><Chip size="small" label={i.type} /></TableCell>
                <TableCell>{i.provider}{i.model ? ` · ${i.model}` : ''}</TableCell>
                <TableCell><Chip size="small" label={i.status} color={i.status === 'SUCCESS' ? 'success' : 'warning'} variant="outlined" /></TableCell>
                <TableCell sx={{ maxWidth: 200 }}>{i.request}</TableCell>
                <TableCell sx={{ maxWidth: 420, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                  {i.response.length > 400 ? i.response.substring(0, 400) + '…' : i.response}
                </TableCell>
                <TableCell align="right">{i.latencyMs} ms</TableCell>
              </TableRow>
            ))}
            {aiInteractions.length === 0 && <TableRow><TableCell colSpan={7}>No AI usage in this attempt.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Paper>
    </>
  )
}
