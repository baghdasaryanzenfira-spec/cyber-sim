import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import CancelIcon from '@mui/icons-material/Cancel'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import {
  Box, Button, Chip, CircularProgress, Grid, List, ListItem, ListItemIcon, ListItemText, Paper, Table,
  TableBody, TableCell, TableHead, TablePagination, TableRow, Tooltip, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { examApi } from '../../api/endpoints'
import type { ExamDetail, StoredReview } from '../../api/types'
import { OutcomeChip, scoreColor } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { useLoad } from '../../hooks/useLoad'

/** True / false / not-claimed chip for the comparison between claimed and verified score. */
function MatchChip({ matches }: { matches?: boolean | null }) {
  const { t } = useTranslation()
  if (matches == null) return <Chip size="small" variant="outlined" label={t('exams.noClaim')} />
  return matches
    ? <Chip size="small" color="success" variant="outlined" label={t('exams.match')} />
    : <Chip size="small" color="error" label={t('exams.mismatch')} />
}

export function ExamsPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const exams = useLoad(() => examApi.list({ page, size }), [page, size])

  if (exams.loading && !exams.data) return <Loading />

  return (
    <>
      <PageHeader title={t('exams.title')} subtitle={t('exams.subtitle')} />
      <ErrorAlert message={exams.error} />
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('exams.submitted')}</TableCell><TableCell>{t('exams.student')}</TableCell>
              <TableCell>{t('exams.scenario')}</TableCell><TableCell align="right">{t('exams.claimed')}</TableCell>
              <TableCell align="right">{t('exams.verified')}</TableCell><TableCell>{t('exams.scoreCheck')}</TableCell>
              <TableCell>{t('exams.aiReview')}</TableCell><TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {exams.data?.items.map((e) => (
              <TableRow key={e.id} hover>
                <TableCell>{new Date(e.submittedAt).toLocaleString()}</TableCell>
                <TableCell>
                  {e.studentName ?? e.studentRef}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {e.externalId}
                  </Typography>
                </TableCell>
                <TableCell>{e.scenarioTitle} <Typography component="span" variant="caption" color="text.secondary">v{e.scenarioVersion}</Typography></TableCell>
                <TableCell align="right">{e.claimedScore ?? '—'}</TableCell>
                <TableCell align="right">
                  <Typography component="span" color={scoreColor(e.verifiedScore)} sx={{ fontWeight: 700 }}>
                    {e.verifiedScore}
                  </Typography>
                </TableCell>
                <TableCell><MatchChip matches={e.scoreMatches} /></TableCell>
                <TableCell>
                  {e.reviewed
                    ? <Chip size="small" color="secondary" variant="outlined" icon={<AutoAwesomeIcon />} label={`${e.reviewRating}/100`} />
                    : <Typography variant="caption" color="text.secondary">{t('exams.notReviewed')}</Typography>}
                </TableCell>
                <TableCell align="right">
                  <Button size="small" component={RouterLink} to={`/admin/exams/${e.id}`}>{t('common.open')}</Button>
                </TableCell>
              </TableRow>
            ))}
            {exams.data?.items.length === 0 && (
              <TableRow><TableCell colSpan={8}>
                <Typography color="text.secondary" sx={{ py: 2 }}>{t('exams.empty')}</Typography>
              </TableCell></TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination component="div" count={exams.data?.totalItems ?? 0} page={page} rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[10, 20, 50]}
          onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }} />
      </Paper>
    </>
  )
}

function ReviewCard({ review, onRun, busy, reviewedAt }: {
  review?: StoredReview | null
  onRun: () => void
  busy: boolean
  reviewedAt?: string | null
}) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 2.5, height: '100%', border: '1px solid rgba(167,139,250,0.3)' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="overline" color="secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <AutoAwesomeIcon fontSize="inherit" /> {t('exams.aiReview')}
        </Typography>
        <Button variant="contained" color="secondary" size="small" disabled={busy} onClick={onRun}
          startIcon={busy ? <CircularProgress size={16} /> : <AutoAwesomeIcon />}>
          {review ? t('exams.rerunReview') : t('exams.runReview')}
        </Button>
      </Box>
      {!review && !busy && (
        <Typography variant="body2" color="text.secondary">{t('exams.reviewHint')}</Typography>
      )}
      {review && (
        <>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1 }}>
            <Typography variant="h4" color={scoreColor(review.rating)}>{review.rating}</Typography>
            <Typography color="text.secondary">/ 100 · {t('exams.aiRating')}</Typography>
          </Box>
          <Typography sx={{ mb: 1.5 }}>{review.message}</Typography>
          {([['strengths', '#34d399'], ['mistakes', '#f87171'], ['recommendations', '#22d3ee']] as const)
            .map(([key, color]) => review[key].length > 0 && (
              <Box key={key} sx={{ mb: 1 }}>
                <Typography variant="subtitle2" sx={{ color }}>{t(`exams.${key}`)}</Typography>
                <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                  {review[key].map((item) => (
                    <Typography component="li" variant="body2" key={item}>{item}</Typography>
                  ))}
                </Box>
              </Box>
            ))}
          <Typography variant="caption" color="text.secondary">
            {t(`aiSource.long${review.source}`)}{reviewedAt ? ` · ${new Date(reviewedAt).toLocaleString()}` : ''}
          </Typography>
        </>
      )}
    </Paper>
  )
}

export function ExamDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const exam = useLoad(() => examApi.get(Number(id)), [id])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const runReview = async () => {
    setBusy(true)
    setError(null)
    try {
      exam.setData(await examApi.review(Number(id)))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  if (exam.loading && !exam.data) return <Loading />
  const d: ExamDetail | null = exam.data
  if (!d) return <ErrorAlert message={exam.error} />
  const a = d.attempt
  const v = d.verification

  return (
    <>
      <PageHeader title={t('exams.detailTitle', { student: a.studentName ?? a.studentRef })}
        subtitle={`${a.scenarioTitle} · v${a.scenarioVersion} · ${a.externalId}`}
        actions={<Button component={RouterLink} to="/admin/exams">{t('common.back')}</Button>} />
      <ErrorAlert message={error} />

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="overline" color="primary">{t('exams.verifiedResult')}</Typography>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, my: 1 }}>
              <Typography variant="h3" color={scoreColor(v.scorePercent)}>{v.scorePercent}</Typography>
              <Typography color="text.secondary">/ 100</Typography>
              <MatchChip matches={a.scoreMatches} />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {t('exams.verifiedExplainer')}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Chip size="small" variant="outlined" label={t('exams.raw', { raw: v.rawScore, max: v.maxScore })} />
              <Chip size="small" variant="outlined" label={t('exams.claimedChip', { score: a.claimedScore ?? '—' })} />
              <Chip size="small" variant="outlined" color="secondary" label={t('exams.hintsChip', { count: v.hintsUsed, penalty: v.hintPenaltyTotal })} />
              <Chip size="small" variant="outlined" color="warning" label={t('exams.evidenceChip', { found: v.evidenceRevealed, total: v.evidenceTotal })} />
            </Box>
            {v.missedActions.length > 0 && (
              <>
                <Typography variant="overline" color="error" sx={{ display: 'block', mt: 2 }}>
                  {t('exams.missed')}
                </Typography>
                <List dense>
                  {v.missedActions.map((m) => (
                    <ListItem key={m.actionKey} disableGutters alignItems="flex-start">
                      <ListItemIcon sx={{ minWidth: 30, mt: 0.5 }}><CancelIcon color="error" fontSize="small" /></ListItemIcon>
                      <ListItemText primary={`${m.label} (${m.points})`} secondary={m.explanation} />
                    </ListItem>
                  ))}
                </List>
              </>
            )}
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 7 }}>
          <ReviewCard review={d.review} onRun={runReview} busy={busy} reviewedAt={d.reviewedAt} />
        </Grid>
      </Grid>

      <Paper sx={{ p: 2 }}>
        <Typography variant="overline" color="primary">{t('exams.steps')}</Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>#</TableCell><TableCell>{t('exams.action')}</TableCell>
              <TableCell>{t('exams.assessment')}</TableCell><TableCell align="right">{t('exams.points')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {v.steps.map((s) => (
              <TableRow key={s.sequence}>
                <TableCell>{s.sequence}</TableCell>
                <TableCell>
                  {s.label}
                  {s.outOfOrder && <Chip size="small" color="warning" label={t('exams.outOfOrder')} sx={{ ml: 1 }} />}
                  {s.duplicate && <Chip size="small" label={t('exams.repeated')} sx={{ ml: 1 }} />}
                </TableCell>
                <TableCell><OutcomeChip outcome={s.outcome} /></TableCell>
                <TableCell align="right" sx={{ color: s.points > 0 ? 'success.main' : s.points < 0 ? 'error.main' : 'text.secondary', fontWeight: 600 }}>
                  {s.points > 0 ? `+${s.points}` : s.points}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {v.steps.every((s) => !s.duplicate) && v.steps.length > 0 && (
          <Tooltip title={t('exams.deterministicNote')}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
              <CheckCircleIcon fontSize="inherit" color="success" /> {t('exams.replayNote')}
            </Typography>
          </Tooltip>
        )}
      </Paper>
    </>
  )
}
