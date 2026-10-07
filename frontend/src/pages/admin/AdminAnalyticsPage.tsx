import { Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/endpoints'
import type { MistakeStat } from '../../api/types'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { useLoad } from '../../hooks/useLoad'

function MistakeTable({ title, rows, color }: { title: string; rows: MistakeStat[]; color: string }) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="h6" sx={{ color }} gutterBottom>{title}</Typography>
      <Table size="small">
        <TableHead><TableRow><TableCell>{t('admin.action')}</TableCell><TableCell>{t('admin.scenario')}</TableCell><TableCell align="right">{t('admin.count')}</TableCell><TableCell align="right">{t('admin.percentOfCompleted')}</TableCell></TableRow></TableHead>
        <TableBody>
          {rows.map((m) => (
            <TableRow key={m.scenarioTitle + m.actionKey}>
              <TableCell>{m.label}</TableCell>
              <TableCell>{m.scenarioTitle}</TableCell>
              <TableCell align="right">{m.count}</TableCell>
              <TableCell align="right">{m.percent != null ? `${m.percent}%` : '—'}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 && <TableRow><TableCell colSpan={4}>{t('common.noData')}</TableCell></TableRow>}
        </TableBody>
      </Table>
    </Paper>
  )
}

export function AdminAnalyticsPage() {
  const { t } = useTranslation()
  const overview = useLoad(adminApi.overview)
  const mistakes = useLoad(adminApi.mistakes)
  if (overview.loading || mistakes.loading) return <Loading />
  const o = overview.data
  const m = mistakes.data
  if (!o || !m) return <ErrorAlert message={overview.error ?? mistakes.error} />

  return (
    <>
      <PageHeader title={t('admin.analyticsTitle')} subtitle={t('admin.analyticsSubtitle')} />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6">{t('admin.scoreDistribution')}</Typography>
            <BarChart height={260} xAxis={[{ scaleType: 'band', data: o.scoreDistribution.map((b) => b.range) }]} yAxis={[{ tickMinStep: 1 }]}
              series={[{ data: o.scoreDistribution.map((b) => b.count), label: t('admin.completedAttemptsSeries'), color: '#22d3ee' }]} />
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('admin.scenarioStats')}</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>{t('admin.scenario')}</TableCell><TableCell align="right">{t('admin.attempts')}</TableCell><TableCell align="right">{t('admin.completed')}</TableCell><TableCell align="right">{t('admin.avgScore')}</TableCell><TableCell align="right">{t('admin.avgTime')}</TableCell></TableRow></TableHead>
              <TableBody>
                {o.scenarios.map((s) => (
                  <TableRow key={s.scenarioId}>
                    <TableCell>{s.title}</TableCell>
                    <TableCell align="right">{s.attempts}</TableCell>
                    <TableCell align="right">{s.completed}</TableCell>
                    <TableCell align="right">{s.averageScore ?? '—'}</TableCell>
                    <TableCell align="right">{s.averageDurationMinutes != null ? t('common.minutes', { count: s.averageDurationMinutes }) : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title={t('admin.missedSteps')} rows={m.missedActions} color="#fbbf24" /></Grid>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title={t('admin.harmfulActions')} rows={m.harmfulActions} color="#f87171" /></Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title={t('admin.unnecessaryActions')} rows={m.unnecessaryActions} color="#94a3b8" /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('admin.aiUsage')}</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>{t('admin.task')}</TableCell><TableCell>{t('admin.status')}</TableCell><TableCell align="right">{t('admin.requests')}</TableCell><TableCell align="right">{t('admin.avgLatency')}</TableCell></TableRow></TableHead>
              <TableBody>
                {o.aiUsage.map((a) => (
                  <TableRow key={a.type + a.status}>
                    <TableCell>{a.type}</TableCell><TableCell>{a.status}</TableCell>
                    <TableCell align="right">{a.count}</TableCell>
                    <TableCell align="right">{a.averageLatencyMs != null ? `${a.averageLatencyMs} ms` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
      </Grid>
    </>
  )
}
