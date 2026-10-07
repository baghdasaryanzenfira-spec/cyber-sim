import {
  Box, Button, Chip, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow,
  TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
            <TableCell>#</TableCell><TableCell>{t('history.started')}</TableCell><TableCell>{t('admin.student')}</TableCell><TableCell>{t('admin.scenario')}</TableCell>
            <TableCell>{t('admin.status')}</TableCell><TableCell>{t('history.actions')}</TableCell><TableCell>{t('history.hints')}</TableCell><TableCell>{t('admin.score')}</TableCell><TableCell />
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
              <TableCell align="right"><Button size="small" component={RouterLink} to={`/admin/attempts/${a.id}`}>{t('common.details')}</Button></TableCell>
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
  const { t } = useTranslation()
  const scenarios = useLoad(adminApi.scenarios)
  const [scenarioId, setScenarioId] = useState<number | ''>('')
  const [status, setStatus] = useState<SimulationStatus | ''>('')
  return (
    <>
      <PageHeader title={t('admin.attemptsTitle')} subtitle={t('admin.attemptsSubtitle')} />
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField select size="small" label={t('admin.scenario')} value={scenarioId} sx={{ minWidth: 280 }}
          onChange={(e) => setScenarioId(e.target.value === '' ? '' : Number(e.target.value))}>
          <MenuItem value="">{t('admin.allScenarios')}</MenuItem>
          {scenarios.data?.map((s) => <MenuItem key={s.id} value={s.id}>{s.title}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('admin.status')} value={status} sx={{ minWidth: 180 }}
          onChange={(e) => setStatus(e.target.value as SimulationStatus | '')}>
          <MenuItem value="">{t('common.all')}</MenuItem>
          {STATUSES.map((s) => <MenuItem key={s} value={s}>{t(`enums.status.${s}`)}</MenuItem>)}
        </TextField>
      </Stack>
      <AttemptsTable filter={{ scenarioId: scenarioId || undefined, status: status || undefined }} />
    </>
  )
}

export function AdminAttemptDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const detail = useLoad(() => adminApi.attempt(Number(id)), [id])
  if (detail.loading) return <Loading />
  if (!detail.data) return <ErrorAlert message={detail.error} />
  const { attempt, simulation, result, aiInteractions } = detail.data

  return (
    <>
      <PageHeader title={t('admin.attemptTitle', { id: attempt.id, title: attempt.scenarioTitle })}
        subtitle={t('admin.attemptSubtitle', { name: attempt.userName, email: attempt.userEmail, date: new Date(attempt.createdAt).toLocaleString() })}
        actions={<StatusChip status={attempt.status} />} />
      {result ? <ResultView result={result} /> : (
        <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.notCompleted')}</Typography>
      )}
      <Grid container spacing={2} sx={{ my: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}><ResourcesPanel resources={simulation.resources} /></Grid>
        <Grid size={{ xs: 12, md: 8 }}><LogsPanel events={simulation.events} readOnly onFlag={() => undefined} /></Grid>
      </Grid>
      <Box sx={{ mb: 2 }}><TimelinePanel events={simulation.events} /></Box>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" gutterBottom>{t('admin.aiInteractions')}</Typography>
        <Table size="small">
          <TableHead>
            <TableRow><TableCell>{t('admin.time')}</TableCell><TableCell>{t('admin.type')}</TableCell><TableCell>{t('admin.provider')}</TableCell><TableCell>{t('admin.status')}</TableCell><TableCell>{t('admin.request')}</TableCell><TableCell>{t('admin.response')}</TableCell><TableCell align="right">{t('admin.latency')}</TableCell></TableRow>
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
            {aiInteractions.length === 0 && <TableRow><TableCell colSpan={7}>{t('admin.noAiUsage')}</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Paper>
    </>
  )
}
