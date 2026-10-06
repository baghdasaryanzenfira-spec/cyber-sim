import GroupIcon from '@mui/icons-material/Group'
import PercentIcon from '@mui/icons-material/Percent'
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck'
import TravelExploreIcon from '@mui/icons-material/TravelExplore'
import { Button, Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import { LineChart } from '@mui/x-charts/LineChart'
import { Link as RouterLink } from 'react-router'
import { adminApi } from '../../api/endpoints'
import { StatusChip, scoreColor } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { StatTile } from '../../components/StatTile'
import { useLoad } from '../../hooks/useLoad'

export function AdminDashboardPage() {
  const overview = useLoad(adminApi.overview)
  const recent = useLoad(() => adminApi.attempts({ size: 6 }))
  const mistakes = useLoad(adminApi.mistakes)

  if (overview.loading) return <Loading />
  const o = overview.data
  if (!o) return <ErrorAlert message={overview.error} />

  return (
    <>
      <PageHeader title="Admin dashboard" subtitle="Platform activity, scenario performance and common mistakes." />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Students" value={o.totals.students} hint={`${o.totals.users} users total`} icon={<GroupIcon />} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Active scenarios" value={o.totals.activeScenarios} icon={<TravelExploreIcon />} color="secondary.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Simulations" value={o.totals.simulations} hint={`${o.totals.completed} completed · ${o.totals.inProgress} in progress`} icon={<PlaylistAddCheckIcon />} color="success.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Average score" value={o.totals.averageScore ?? '—'} hint={`completion rate ${o.totals.completionRatePercent ?? 0}%`} icon={<PercentIcon />} color="warning.main" /></Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6">Attempts — last 14 days</Typography>
            <LineChart height={260}
              xAxis={[{ scaleType: 'point', data: o.attemptsPerDay.map((d) => d.day.substring(5)) }]} yAxis={[{ tickMinStep: 1 }]}
              series={[
                { data: o.attemptsPerDay.map((d) => d.attempts), label: 'Started', color: '#22d3ee', curve: 'linear' },
                { data: o.attemptsPerDay.map((d) => d.completed), label: 'Completed', color: '#34d399', curve: 'linear' },
              ]} />
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6">Average score per scenario</Typography>
            <BarChart height={260} layout="horizontal"
              yAxis={[{ scaleType: 'band', data: o.scenarios.map((s) => s.title.length > 22 ? s.title.substring(0, 22) + '…' : s.title), width: 150 }]}
              xAxis={[{ min: 0, max: 100 }]}
              series={[{ data: o.scenarios.map((s) => s.averageScore ?? 0), label: 'Avg score', color: '#a78bfa' }]} />
          </Paper>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>Recent attempts</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Student</TableCell><TableCell>Scenario</TableCell><TableCell>Status</TableCell><TableCell>Score</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {recent.data?.items.map((a) => (
                  <TableRow key={a.id} hover>
                    <TableCell>{a.userName}</TableCell>
                    <TableCell>{a.scenarioTitle}</TableCell>
                    <TableCell><StatusChip status={a.status} /></TableCell>
                    <TableCell><Typography color={scoreColor(a.scorePercent)} sx={{ fontWeight: 700 }}>{a.scorePercent ?? '—'}</Typography></TableCell>
                    <TableCell align="right"><Button size="small" component={RouterLink} to={`/admin/attempts/${a.id}`}>Details</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>Most common mistakes</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Harmful action</TableCell><TableCell align="right">Count</TableCell></TableRow></TableHead>
              <TableBody>
                {mistakes.data?.harmfulActions.slice(0, 5).map((m) => (
                  <TableRow key={m.scenarioTitle + m.actionKey}>
                    <TableCell>{m.label}<Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{m.scenarioTitle}</Typography></TableCell>
                    <TableCell align="right">{m.count}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Button component={RouterLink} to="/admin/analytics" sx={{ mt: 1 }}>All analytics</Button>
          </Paper>
        </Grid>
      </Grid>
    </>
  )
}
