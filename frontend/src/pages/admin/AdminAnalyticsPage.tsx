import { Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import { adminApi } from '../../api/endpoints'
import type { MistakeStat } from '../../api/types'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { useLoad } from '../../hooks/useLoad'

function MistakeTable({ title, rows, color }: { title: string; rows: MistakeStat[]; color: string }) {
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="h6" sx={{ color }} gutterBottom>{title}</Typography>
      <Table size="small">
        <TableHead><TableRow><TableCell>Action</TableCell><TableCell>Scenario</TableCell><TableCell align="right">Count</TableCell><TableCell align="right">% of completed</TableCell></TableRow></TableHead>
        <TableBody>
          {rows.map((m) => (
            <TableRow key={m.scenarioTitle + m.actionKey}>
              <TableCell>{m.label}</TableCell>
              <TableCell>{m.scenarioTitle}</TableCell>
              <TableCell align="right">{m.count}</TableCell>
              <TableCell align="right">{m.percent != null ? `${m.percent}%` : '—'}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 && <TableRow><TableCell colSpan={4}>No data yet.</TableCell></TableRow>}
        </TableBody>
      </Table>
    </Paper>
  )
}

export function AdminAnalyticsPage() {
  const overview = useLoad(adminApi.overview)
  const mistakes = useLoad(adminApi.mistakes)
  if (overview.loading || mistakes.loading) return <Loading />
  const o = overview.data
  const m = mistakes.data
  if (!o || !m) return <ErrorAlert message={overview.error ?? mistakes.error} />

  return (
    <>
      <PageHeader title="Analytics" subtitle="Learning outcomes, common mistakes and AI usage across the platform." />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6">Score distribution</Typography>
            <BarChart height={260} xAxis={[{ scaleType: 'band', data: o.scoreDistribution.map((b) => b.range) }]} yAxis={[{ tickMinStep: 1 }]}
              series={[{ data: o.scoreDistribution.map((b) => b.count), label: 'Completed attempts', color: '#22d3ee' }]} />
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>Scenario statistics</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Scenario</TableCell><TableCell align="right">Attempts</TableCell><TableCell align="right">Completed</TableCell><TableCell align="right">Avg score</TableCell><TableCell align="right">Avg time</TableCell></TableRow></TableHead>
              <TableBody>
                {o.scenarios.map((s) => (
                  <TableRow key={s.scenarioId}>
                    <TableCell>{s.title}</TableCell>
                    <TableCell align="right">{s.attempts}</TableCell>
                    <TableCell align="right">{s.completed}</TableCell>
                    <TableCell align="right">{s.averageScore ?? '—'}</TableCell>
                    <TableCell align="right">{s.averageDurationMinutes != null ? `${s.averageDurationMinutes} min` : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title="Most frequently missed steps" rows={m.missedActions} color="#fbbf24" /></Grid>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title="Most common harmful actions" rows={m.harmfulActions} color="#f87171" /></Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}><MistakeTable title="Unnecessary actions" rows={m.unnecessaryActions} color="#94a3b8" /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>AI usage and reliability</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Task</TableCell><TableCell>Status</TableCell><TableCell align="right">Requests</TableCell><TableCell align="right">Avg latency</TableCell></TableRow></TableHead>
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
