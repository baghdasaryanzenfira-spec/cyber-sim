import { Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import type { ProgressView as Progress } from '../api/types'
import { CategoryChip, DifficultyChip } from './Chips'
import { StatTile } from './StatTile'

/** Progress statistics; shared by the student history page and the admin user detail page. */
export function ProgressSummary({ progress }: { progress: Progress }) {
  const categories = progress.categories
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Attempts" value={progress.totals.attempts} hint={`${progress.totals.abandoned} abandoned`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Completed" value={progress.totals.completed} color="success.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Average score" value={progress.totals.averageScore ?? '—'} color="warning.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label="Training time" value={`${progress.totals.trainingMinutes} min`} color="secondary.main" /></Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6">Results by topic</Typography>
            {categories.length > 0 && (
              <BarChart height={240}
                xAxis={[{ scaleType: 'band', data: categories.map((c) => c.category.replace('_', ' ')) }]}
                yAxis={[{ min: 0, max: 100 }]}
                series={[
                  { data: categories.map((c) => c.averageScore ?? 0), label: 'Average score', color: '#22d3ee' },
                  { data: categories.map((c) => c.bestScore ?? 0), label: 'Best score', color: '#a78bfa' },
                ]} />
            )}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>Scenario progress</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>Scenario</TableCell><TableCell>Level</TableCell><TableCell>Attempts</TableCell><TableCell>Best</TableCell></TableRow></TableHead>
              <TableBody>
                {progress.scenarios.map((s) => (
                  <TableRow key={s.scenarioId}>
                    <TableCell>{s.title}<br /><CategoryChip category={s.category} /></TableCell>
                    <TableCell><DifficultyChip difficulty={s.difficulty} /></TableCell>
                    <TableCell>{s.attempts}</TableCell>
                    <TableCell>{s.bestScore ?? '—'}</TableCell>
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
