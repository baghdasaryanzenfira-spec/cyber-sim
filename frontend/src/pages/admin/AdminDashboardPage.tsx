import AccountTreeIcon from '@mui/icons-material/AccountTree'
import ArchiveIcon from '@mui/icons-material/Archive'
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh'
import EditNoteIcon from '@mui/icons-material/EditNote'
import FactCheckIcon from '@mui/icons-material/FactCheck'
import HistoryIcon from '@mui/icons-material/History'
import ListAltIcon from '@mui/icons-material/ListAlt'
import PublishIcon from '@mui/icons-material/Publish'
import ScienceIcon from '@mui/icons-material/Science'
import SpeedIcon from '@mui/icons-material/Speed'
import TravelExploreIcon from '@mui/icons-material/TravelExplore'
import { Box, Button, Grid, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { adminApi } from '../../api/endpoints'
import type { ScenarioStatus } from '../../api/types'
import { ScenarioStatusChip } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { StatTile } from '../../components/StatTile'
import { useLoad } from '../../hooks/useLoad'

const STEPS: { key: string; icon: ReactNode }[] = [
  { key: 'generate', icon: <AutoFixHighIcon /> },
  { key: 'edit', icon: <EditNoteIcon /> },
  { key: 'graph', icon: <AccountTreeIcon /> },
  { key: 'validation', icon: <FactCheckIcon /> },
  { key: 'tests', icon: <ScienceIcon /> },
  { key: 'quality', icon: <SpeedIcon /> },
  { key: 'publish', icon: <PublishIcon /> },
  { key: 'versions', icon: <HistoryIcon /> },
]

function WorkflowStep({ index, stepKey, icon }: { index: number; stepKey: string; icon: ReactNode }) {
  const { t } = useTranslation()
  return (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
      <Box sx={{ color: 'primary.main', mt: 0.25 }}>{icon}</Box>
      <Box>
        <Typography sx={{ fontWeight: 600 }}>{index}. {t(`dashboard.steps.${stepKey}.title`)}</Typography>
        <Typography variant="body2" color="text.secondary">{t(`dashboard.steps.${stepKey}.text`)}</Typography>
      </Box>
    </Box>
  )
}

export function AdminDashboardPage() {
  const { t } = useTranslation()
  const scenarios = useLoad(adminApi.scenarios)

  if (scenarios.loading && !scenarios.data) return <Loading />
  const all = scenarios.data
  if (!all) return <ErrorAlert message={scenarios.error} />

  const count = (status: ScenarioStatus) => all.filter((s) => s.status === status).length
  const recent = [...all].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5)

  return (
    <>
      <PageHeader title={t('dashboard.title')} subtitle={t('dashboard.subtitle')}
        actions={<>
          <Button component={RouterLink} to="/admin/scenarios" startIcon={<ListAltIcon />}>{t('dashboard.allScenarios')}</Button>
          <Button component={RouterLink} to="/admin/generate" variant="contained" startIcon={<AutoFixHighIcon />}>
            {t('nav.generate')}
          </Button>
        </>} />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('dashboard.total')} value={all.length} icon={<TravelExploreIcon />} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('dashboard.drafts')} value={count('DRAFT')} icon={<EditNoteIcon />} color="info.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('dashboard.published')} value={count('PUBLISHED')} icon={<PublishIcon />} color="success.main" /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatTile label={t('dashboard.archived')} value={count('ARCHIVED')} icon={<ArchiveIcon />} color="text.secondary" /></Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="h6">{t('dashboard.workflowTitle')}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('dashboard.workflowSubtitle')}</Typography>
            <Box sx={{ display: 'grid', gap: 1.75 }}>
              {STEPS.map((s, i) => <WorkflowStep key={s.key} index={i + 1} stepKey={s.key} icon={s.icon} />)}
            </Box>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="h6" gutterBottom>{t('dashboard.recent')}</Typography>
            {recent.length === 0 ? (
              <Typography color="text.secondary">{t('dashboard.noScenarios')}</Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('list.titleCol')}</TableCell><TableCell>{t('list.status')}</TableCell>
                    <TableCell>{t('list.updated')}</TableCell><TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {recent.map((s) => (
                    <TableRow key={s.id} hover>
                      <TableCell>{s.title}</TableCell>
                      <TableCell><ScenarioStatusChip status={s.status} /></TableCell>
                      <TableCell>{new Date(s.updatedAt).toLocaleString()}</TableCell>
                      <TableCell align="right">
                        <Button size="small" component={RouterLink} to={`/admin/scenarios/${s.id}`}>{t('common.open')}</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Paper>
        </Grid>
      </Grid>
    </>
  )
}
