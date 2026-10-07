import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import {
  Box, Button, Grid, List, ListItem, ListItemIcon, ListItemText, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { errorMessage } from '../api/client'
import { progressApi, scenarioApi, simulationApi } from '../api/endpoints'
import type { Category, Difficulty } from '../api/types'
import { CategoryChip, DifficultyChip } from '../components/Chips'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ScenarioCard } from '../components/ScenarioCard'
import { TranslateButton } from '../components/TranslateButton'
import { useLoad } from '../hooks/useLoad'

export function ScenarioListPage() {
  const { t } = useTranslation()
  const scenarios = useLoad(scenarioApi.list)
  const progress = useLoad(progressApi.me)
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('')
  const [category, setCategory] = useState<Category | ''>('')

  if (scenarios.loading) return <Loading />
  const filtered = (scenarios.data ?? []).filter(
    (s) => (!difficulty || s.difficulty === difficulty) && (!category || s.category === category))
  const categories = [...new Set((scenarios.data ?? []).map((s) => s.category))]

  return (
    <>
      <PageHeader title={t('scenarios.title')} subtitle={t('scenarios.subtitle')} />
      <ErrorAlert message={scenarios.error} />
      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <TextField select size="small" label={t('scenarios.difficulty')} value={difficulty} sx={{ minWidth: 180 }}
          onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}>
          <MenuItem value="">{t('common.all')}</MenuItem>
          {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as Difficulty[]).map((d) => <MenuItem key={d} value={d}>{t(`enums.difficulty.${d}`)}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('scenarios.category')} value={category} sx={{ minWidth: 200 }}
          onChange={(e) => setCategory(e.target.value as Category | '')}>
          <MenuItem value="">{t('common.all')}</MenuItem>
          {categories.map((c) => <MenuItem key={c} value={c}>{t(`enums.category.${c}`)}</MenuItem>)}
        </TextField>
      </Stack>
      <Grid container spacing={2}>
        {filtered.map((s) => (
          <Grid key={s.id} size={{ xs: 12, md: 6, lg: 4 }}>
            <ScenarioCard scenario={s}
              bestScore={progress.data?.scenarios.find((x) => x.scenarioId === s.id)?.bestScore} />
          </Grid>
        ))}
      </Grid>
      {filtered.length === 0 && <Typography color="text.secondary">{t('scenarios.noMatch')}</Typography>}
    </>
  )
}

export function ScenarioDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const scenario = useLoad(() => scenarioApi.get(Number(id)), [id])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const start = async () => {
    setBusy(true)
    try {
      const sim = await simulationApi.create(Number(id))
      navigate(`/simulations/${sim.id}`)
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  if (scenario.loading) return <Loading />
  const s = scenario.data
  if (!s) return <ErrorAlert message={scenario.error} />

  return (
    <>
      <PageHeader title={s.title} subtitle={s.summary} actions={
        <Button variant="contained" size="large" startIcon={<PlayArrowIcon />} onClick={start} disabled={busy}>
          {t('scenarios.startSimulation')}
        </Button>
      } />
      <ErrorAlert message={error} />
      <Stack direction="row" spacing={1} sx={{ mb: 3 }}>
        <DifficultyChip difficulty={s.difficulty} />
        <CategoryChip category={s.category} />
      </Stack>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="overline" color="primary">{t('scenarios.briefing')}</Typography>
            {s.description.split('\n').filter(Boolean).map((para, i) => (
              <Typography key={i} sx={{ mt: 1.5, lineHeight: 1.7 }}>{para}</Typography>
            ))}
            <Box sx={{ mt: 1.5 }}><TranslateButton text={s.description} /></Box>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="overline" color="primary">{t('scenarios.objectives')}</Typography>
            <List dense>
              {s.learningObjectives.map((o) => (
                <ListItem key={o} disableGutters>
                  <ListItemIcon sx={{ minWidth: 32 }}><CheckCircleOutlineIcon color="success" fontSize="small" /></ListItemIcon>
                  <ListItemText primary={o} />
                </ListItem>
              ))}
            </List>
            <TranslateButton text={s.learningObjectives.join('\n')} />
            <Box sx={{ mt: 2, color: 'text.secondary' }}>
              <Typography variant="body2">{t('scenarios.estimatedTime', { count: s.estimatedMinutes })}</Typography>
              <Typography variant="body2">{t('scenarios.resourceCount', { count: s.resourceCount })}</Typography>
              <Typography variant="body2" sx={{ mt: 1 }}>
                {t('scenarios.isolatedNote')}
              </Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </>
  )
}
