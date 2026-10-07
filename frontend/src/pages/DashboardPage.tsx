import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents'
import LightbulbIcon from '@mui/icons-material/Lightbulb'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import TaskAltIcon from '@mui/icons-material/TaskAlt'
import TrendingUpIcon from '@mui/icons-material/TrendingUp'
import { Box, Button, Chip, Grid, Link, List, ListItem, ListItemText, Paper, Typography } from '@mui/material'
import { LineChart } from '@mui/x-charts/LineChart'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { progressApi, scenarioApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { StatusChip } from '../components/Chips'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ScenarioCard } from '../components/ScenarioCard'
import { StatTile } from '../components/StatTile'
import { useLoad } from '../hooks/useLoad'

export function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const progress = useLoad(progressApi.me)
  const scenarios = useLoad(scenarioApi.list)

  if (progress.loading || scenarios.loading) return <Loading />
  const p = progress.data
  const active = p?.scenarios.filter((s) => s.activeSimulationId) ?? []

  return (
    <>
      <PageHeader title={t('dashboard.welcome', { name: user?.displayName })}
        subtitle={t('dashboard.subtitle')} />
      <ErrorAlert message={progress.error ?? scenarios.error} />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatTile label={t('dashboard.completed')} value={p?.totals.completed ?? 0} hint={t('dashboard.attempts', { count: p?.totals.attempts ?? 0 })}
            icon={<TaskAltIcon />} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatTile label={t('dashboard.averageScore')} value={p?.totals.averageScore ?? '—'} hint={t('dashboard.outOf100')}
            icon={<TrendingUpIcon />} color="success.main" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatTile label={t('dashboard.bestScore')} value={p?.totals.bestScore ?? '—'} icon={<EmojiEventsIcon />} color="warning.main" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatTile label={t('dashboard.hintsUsed')} value={p?.totals.hintsUsed ?? 0} hint={t('dashboard.minTraining', { count: p?.totals.trainingMinutes ?? 0 })}
            icon={<LightbulbIcon />} color="secondary.main" />
        </Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('dashboard.scoreHistory')}</Typography>
            {p && p.scoreHistory.length > 0 ? (
              <LineChart height={240}
                xAxis={[{ data: p.scoreHistory.map((_, i) => i + 1), label: t('dashboard.attempt'), scaleType: 'point' }]}
                yAxis={[{ min: 0, max: 100 }]}
                series={[{ data: p.scoreHistory.map((s) => s.scorePercent), label: t('dashboard.score'), color: '#22d3ee', area: true, showMark: true }]} />
            ) : (
              <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
                {t('dashboard.emptyChart')}
              </Typography>
            )}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('dashboard.unfinished')}</Typography>
            {active.length === 0 && <Typography color="text.secondary">{t('dashboard.noActive')}</Typography>}
            <List dense>
              {active.map((s) => (
                <ListItem key={s.scenarioId} secondaryAction={
                  <Button size="small" startIcon={<PlayArrowIcon />} component={RouterLink}
                    to={`/simulations/${s.activeSimulationId}`}>{t('dashboard.resume')}</Button>
                }>
                  <ListItemText primary={s.title} secondary={s.lastStatus && <StatusChip status={s.lastStatus} />} />
                </ListItem>
              ))}
            </List>
            <Box sx={{ mt: 2, p: 2, borderRadius: 2, bgcolor: 'rgba(167,139,250,0.08)' }}>
              <Typography variant="subtitle2" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <AutoAwesomeIcon fontSize="small" color="secondary" /> {t('dashboard.aiRecommendations')}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {t('dashboard.aiRecommendationsHint')} <Link component={RouterLink} to="/history">{t('dashboard.progressPage')}</Link>.
              </Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>

      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
        <Typography variant="h6">{t('dashboard.trainingScenarios')}</Typography>
        <Chip label={t('dashboard.available', { count: scenarios.data?.length ?? 0 })} size="small" />
      </Box>
      <Grid container spacing={2}>
        {scenarios.data?.map((s) => (
          <Grid key={s.id} size={{ xs: 12, md: 6, lg: 4 }}>
            <ScenarioCard scenario={s} bestScore={p?.scenarios.find((x) => x.scenarioId === s.id)?.bestScore} />
          </Grid>
        ))}
      </Grid>
    </>
  )
}
