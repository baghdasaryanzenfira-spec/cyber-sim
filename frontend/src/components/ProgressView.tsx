import { Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import { useTranslation } from 'react-i18next'
import type { ProgressView as Progress } from '../api/types'
import { CategoryChip, DifficultyChip } from './Chips'
import { StatTile } from './StatTile'

/** Progress statistics; shared by the student history page and the admin user detail page. */
export function ProgressSummary({ progress }: { progress: Progress }) {
  const { t } = useTranslation()
  const categories = progress.categories
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('history.attempts')} value={progress.totals.attempts} hint={t('history.abandonedCount', { count: progress.totals.abandoned })} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('history.completed')} value={progress.totals.completed} color="success.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('history.averageScore')} value={progress.totals.averageScore ?? '—'} color="warning.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('history.trainingTime')} value={t('common.minutes', { count: progress.totals.trainingMinutes })} color="secondary.main" /></Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6">{t('history.resultsByTopic')}</Typography>
            {categories.length > 0 && (
              <BarChart height={240}
                xAxis={[{ scaleType: 'band', data: categories.map((c) => t(`enums.category.${c.category}`)) }]}
                yAxis={[{ min: 0, max: 100 }]}
                series={[
                  { data: categories.map((c) => c.averageScore ?? 0), label: t('history.averageScore'), color: '#22d3ee' },
                  { data: categories.map((c) => c.bestScore ?? 0), label: t('history.bestScore'), color: '#a78bfa' },
                ]} />
            )}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('history.scenarioProgress')}</Typography>
            <Table size="small">
              <TableHead><TableRow><TableCell>{t('history.scenario')}</TableCell><TableCell>{t('history.level')}</TableCell><TableCell>{t('history.attempts')}</TableCell><TableCell>{t('history.best')}</TableCell></TableRow></TableHead>
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
