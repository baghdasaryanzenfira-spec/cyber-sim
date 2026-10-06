import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import {
  Box, Button, Grid, List, ListItem, ListItemIcon, ListItemText, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { errorMessage } from '../api/client'
import { progressApi, scenarioApi, simulationApi } from '../api/endpoints'
import type { Category, Difficulty } from '../api/types'
import { CategoryChip, DifficultyChip } from '../components/Chips'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ScenarioCard } from '../components/ScenarioCard'
import { useLoad } from '../hooks/useLoad'

export function ScenarioListPage() {
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
      <PageHeader title="Scenarios" subtitle="Choose a simulated cloud incident to investigate." />
      <ErrorAlert message={scenarios.error} />
      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <TextField select size="small" label="Difficulty" value={difficulty} sx={{ minWidth: 180 }}
          onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}>
          <MenuItem value="">All</MenuItem>
          {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as Difficulty[]).map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Category" value={category} sx={{ minWidth: 200 }}
          onChange={(e) => setCategory(e.target.value as Category | '')}>
          <MenuItem value="">All</MenuItem>
          {categories.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
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
      {filtered.length === 0 && <Typography color="text.secondary">No scenarios match the filter.</Typography>}
    </>
  )
}

export function ScenarioDetailPage() {
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
          Start simulation
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
            <Typography variant="overline" color="primary">Incident briefing</Typography>
            {s.description.split('\n').filter(Boolean).map((para, i) => (
              <Typography key={i} sx={{ mt: 1.5, lineHeight: 1.7 }}>{para}</Typography>
            ))}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="overline" color="primary">Learning objectives</Typography>
            <List dense>
              {s.learningObjectives.map((o) => (
                <ListItem key={o} disableGutters>
                  <ListItemIcon sx={{ minWidth: 32 }}><CheckCircleOutlineIcon color="success" fontSize="small" /></ListItemIcon>
                  <ListItemText primary={o} />
                </ListItem>
              ))}
            </List>
            <Box sx={{ mt: 2, color: 'text.secondary' }}>
              <Typography variant="body2">Estimated time: {s.estimatedMinutes} minutes</Typography>
              <Typography variant="body2">Simulated cloud resources: {s.resourceCount}</Typography>
              <Typography variant="body2" sx={{ mt: 1 }}>
                The environment is fully simulated and isolated — no real systems are affected.
              </Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </>
  )
}
