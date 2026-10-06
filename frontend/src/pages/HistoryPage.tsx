import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import {
  Alert, Box, Button, Card, CardContent, Grid, Paper, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router'
import { progressApi, simulationApi } from '../api/endpoints'
import { CategoryChip, StatusChip, scoreColor } from '../components/Chips'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ProgressSummary } from '../components/ProgressView'
import { useLoad } from '../hooks/useLoad'

export function HistoryPage() {
  const progress = useLoad(progressApi.me)
  const history = useLoad(simulationApi.list)
  const recommendations = useLoad(progressApi.recommendations)

  if (progress.loading || history.loading) return <Loading />

  return (
    <>
      <PageHeader title="Progress & history" subtitle="Your training results and personalised study recommendations." />
      <ErrorAlert message={progress.error ?? history.error} />
      {progress.data && <ProgressSummary progress={progress.data} />}

      <Paper sx={{ p: 2, mb: 2, border: '1px solid rgba(167,139,250,0.3)' }}>
        <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <AutoAwesomeIcon color="secondary" /> AI learning recommendations
        </Typography>
        {recommendations.loading && <Skeleton height={80} />}
        {recommendations.error && <Alert severity="warning">{recommendations.error}</Alert>}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          {recommendations.data?.recommendations.map((r) => (
            <Grid key={r.topic} size={{ xs: 12, md: 4 }}>
              <Card sx={{ height: '100%', bgcolor: 'rgba(167,139,250,0.06)' }}>
                <CardContent>
                  <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{r.topic}</Typography>
                  <Box sx={{ my: 1 }}><CategoryChip category={r.category} /></Box>
                  <Typography variant="body2" color="text.secondary">{r.reason}</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" gutterBottom>Simulation history</Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Started</TableCell><TableCell>Scenario</TableCell><TableCell>Status</TableCell>
              <TableCell>Actions</TableCell><TableCell>Hints</TableCell><TableCell>Score</TableCell><TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {history.data?.map((s) => (
              <TableRow key={s.id} hover>
                <TableCell>{new Date(s.createdAt).toLocaleString()}</TableCell>
                <TableCell>{s.scenarioTitle}</TableCell>
                <TableCell><StatusChip status={s.status} /></TableCell>
                <TableCell>{s.actionCount}</TableCell>
                <TableCell>{s.hintsUsed}</TableCell>
                <TableCell>
                  <Typography color={scoreColor(s.scorePercent)} sx={{ fontWeight: 700 }}>
                    {s.scorePercent != null ? `${s.scorePercent}/100` : '—'}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  {s.status === 'COMPLETED' && <Button size="small" component={RouterLink} to={`/simulations/${s.id}/result`}>Result</Button>}
                  {['CREATED', 'RUNNING', 'INVESTIGATING', 'RESPONDING'].includes(s.status) &&
                    <Button size="small" component={RouterLink} to={`/simulations/${s.id}`}>Resume</Button>}
                </TableCell>
              </TableRow>
            ))}
            {history.data?.length === 0 && (
              <TableRow><TableCell colSpan={7}><Typography color="text.secondary">No simulations yet.</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
    </>
  )
}
