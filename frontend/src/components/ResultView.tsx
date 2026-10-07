import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import CancelIcon from '@mui/icons-material/Cancel'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import FlagIcon from '@mui/icons-material/Flag'
import {
  Box, Chip, Grid, List, ListItem, ListItemIcon, ListItemText, Paper, Stack, Table, TableBody, TableCell, TableHead,
  TableRow, Typography,
} from '@mui/material'
import { Gauge, gaugeClasses } from '@mui/x-charts/Gauge'
import { useTranslation } from 'react-i18next'
import type { AiFeedback, AiSource, ScoreItemKind, SimulationResult } from '../api/types'
import { OutcomeChip } from './Chips'
import { TranslateButton } from './TranslateButton'

const kindColor: Record<ScoreItemKind, 'success' | 'warning' | 'default' | 'error' | 'secondary'> = {
  EXPECTED: 'success', OUT_OF_ORDER: 'warning', NEUTRAL: 'default', HARMFUL: 'error', DUPLICATE: 'default', HINT_PENALTY: 'secondary',
}

/** Flattens the feedback panel into one block so a reader translates it in a single request, not seven. */
function feedbackAsText(feedback: AiFeedback, t: (key: string) => string): string {
  const sections: [string, string[]][] = [
    [t('result.strengths'), feedback.strengths],
    [t('result.improvements'), feedback.improvements],
    [t('result.orderIssues'), feedback.orderIssues],
    [t('result.missedEvidence'), feedback.missedEvidence],
    [t('result.unnecessaryActions'), feedback.unnecessaryActions],
    [t('result.nextSteps'), feedback.nextSteps],
  ]
  return [feedback.summary, ...sections
    .filter(([, items]) => items.length > 0)
    .map(([title, items]) => `${title}:\n${items.map((i) => `- ${i}`).join('\n')}`)].join('\n\n')
}

function FeedbackList({ title, items, color }: { title: string; items: string[]; color: string }) {
  if (items.length === 0) return null
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" sx={{ color, mb: 0.5 }}>{title}</Typography>
      <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
        {items.map((i) => <Typography component="li" variant="body2" key={i} sx={{ mb: 0.5 }}>{i}</Typography>)}
      </Box>
    </Box>
  )
}

export function FeedbackPanel({ feedback, source }: { feedback?: AiFeedback; source?: AiSource }) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 3, height: '100%', border: '1px solid rgba(167,139,250,0.3)' }}>
      <Typography variant="overline" color="secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <AutoAwesomeIcon fontSize="inherit" /> {t('result.aiFeedback')}
      </Typography>
      {!feedback && <Typography color="text.secondary">{t('result.noFeedback')}</Typography>}
      {feedback && (
        <>
          <Typography sx={{ mb: 2, mt: 1 }}>{feedback.summary}</Typography>
          <FeedbackList title={t('result.strengths')} items={feedback.strengths} color="#34d399" />
          <FeedbackList title={t('result.improvements')} items={feedback.improvements} color="#fbbf24" />
          <FeedbackList title={t('result.orderIssues')} items={feedback.orderIssues} color="#fbbf24" />
          <FeedbackList title={t('result.missedEvidence')} items={feedback.missedEvidence} color="#f87171" />
          <FeedbackList title={t('result.unnecessaryActions')} items={feedback.unnecessaryActions} color="#94a3b8" />
          <FeedbackList title={t('result.nextSteps')} items={feedback.nextSteps} color="#22d3ee" />
          <TranslateButton text={feedbackAsText(feedback, t)} />
          {source && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              {t(`aiSource.long${source}`)}
            </Typography>
          )}
        </>
      )}
    </Paper>
  )
}

/** Complete result of a simulation: used by the student result page and the admin attempt detail. */
export function ResultView({ result }: { result: SimulationResult }) {
  const { t } = useTranslation()
  const minutes = result.durationSeconds != null ? Math.round(result.durationSeconds / 60) : null
  const color = result.scorePercent >= 80 ? '#34d399' : result.scorePercent >= 50 ? '#fbbf24' : '#f87171'
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper sx={{ p: 3, height: '100%', textAlign: 'center' }}>
            <Typography variant="overline" color="text.secondary">{t('result.finalScore')}</Typography>
            <Gauge value={result.scorePercent} valueMax={100} height={190} startAngle={-110} endAngle={110}
              text={`${result.scorePercent}/100`}
              sx={{ [`& .${gaugeClasses.valueArc}`]: { fill: color }, [`& .${gaugeClasses.valueText}`]: { fontSize: 28, fontWeight: 700 } }} />
            <Stack direction="row" spacing={1} sx={{ justifyContent: 'center', flexWrap: 'wrap', rowGap: 1 }}>
              <Chip size="small" label={t('result.raw', { raw: result.rawScore, max: result.maxScore })} />
              <Chip size="small" label={t('result.hints', { count: result.hintsUsed, penalty: result.hintPenaltyTotal })} color="secondary" variant="outlined" />
              {minutes != null && <Chip size="small" label={t('common.minutes', { count: minutes })} variant="outlined" />}
              <Chip size="small" icon={<FlagIcon />} label={t('result.evidenceChip', { found: result.evidence.found, total: result.evidence.total })} variant="outlined" color="warning" />
            </Stack>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <FeedbackPanel feedback={result.feedback} source={result.feedbackSource} />
        </Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="overline" color="primary">{t('result.breakdown')}</Typography>
            <Table size="small">
              <TableHead>
                <TableRow><TableCell>#</TableCell><TableCell>{t('result.action')}</TableCell><TableCell>{t('result.assessment')}</TableCell><TableCell align="right">{t('result.points')}</TableCell></TableRow>
              </TableHead>
              <TableBody>
                {result.breakdown.map((item, i) => (
                  <TableRow key={i}>
                    <TableCell>{item.kind === 'HINT_PENALTY' ? '' : i + 1}</TableCell>
                    <TableCell>{item.label}</TableCell>
                    <TableCell><Chip size="small" label={t(`enums.scoreKind.${item.kind}`)} color={kindColor[item.kind]} variant="outlined" /></TableCell>
                    <TableCell align="right" sx={{ color: item.points > 0 ? 'success.main' : item.points < 0 ? 'error.main' : 'text.secondary', fontWeight: 600 }}>
                      {item.points > 0 ? `+${item.points}` : item.points}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="overline" color="error">{t('result.missedActions')}</Typography>
            {result.missedActions.length === 0 && <Typography color="success.main">{t('result.noneMissed')}</Typography>}
            <List dense>
              {result.missedActions.map((m) => (
                <ListItem key={m.actionKey} disableGutters alignItems="flex-start">
                  <ListItemIcon sx={{ minWidth: 30, mt: 0.5 }}><CancelIcon color="error" fontSize="small" /></ListItemIcon>
                  <ListItemText primary={t('result.missedPts', { label: m.label, points: m.points })} secondary={m.explanation} />
                </ListItem>
              ))}
            </List>
          </Paper>
          <Paper sx={{ p: 2 }}>
            <Typography variant="overline" color="warning">{t('result.evidence')}</Typography>
            <List dense>
              {result.evidence.items.map((e) => (
                <ListItem key={e.eventKey} disableGutters alignItems="flex-start">
                  <ListItemIcon sx={{ minWidth: 30, mt: 0.5 }}>
                    {e.flagged ? <CheckCircleIcon color="success" fontSize="small" /> : <CancelIcon color={e.revealed ? 'warning' : 'error'} fontSize="small" />}
                  </ListItemIcon>
                  <ListItemText primary={e.message} secondary={`${e.flagged ? t('result.flagged') : e.revealed ? t('result.seenNotFlagged') : t('result.neverUncovered')} — ${e.note}`} />
                </ListItem>
              ))}
            </List>
          </Paper>
        </Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="overline" color="primary">{t('result.whatHappened')}</Typography>
            <Typography sx={{ mt: 1, mb: 1, lineHeight: 1.7 }}>{result.incidentExplanation}</Typography>
            <TranslateButton text={result.incidentExplanation} />
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="overline" color="success">{t('result.recommendedResponse')}</Typography>
            <Typography sx={{ mt: 1, mb: 1, whiteSpace: 'pre-line', lineHeight: 1.7 }}>{result.recommendedSolution}</Typography>
            <TranslateButton text={result.recommendedSolution} />
          </Paper>
        </Grid>
      </Grid>

      <Paper sx={{ p: 2 }}>
        <Typography variant="overline" color="primary">{t('result.yourActions')}</Typography>
        <Table size="small">
          <TableHead>
            <TableRow><TableCell>#</TableCell><TableCell>{t('result.time')}</TableCell><TableCell>{t('result.action')}</TableCell><TableCell>{t('result.phase')}</TableCell><TableCell>{t('result.outcome')}</TableCell><TableCell align="right">{t('result.points')}</TableCell></TableRow>
          </TableHead>
          <TableBody>
            {result.performedActions.map((a) => (
              <TableRow key={a.sequence}>
                <TableCell>{a.sequence}</TableCell>
                <TableCell>{new Date(a.performedAt).toLocaleTimeString()}</TableCell>
                <TableCell>{a.label}{a.outOfOrder && <Chip size="small" label={t('result.outOfOrder')} color="warning" sx={{ ml: 1 }} />}{a.result === 'DUPLICATE' && <Chip size="small" label={t('result.repeated')} sx={{ ml: 1 }} />}</TableCell>
                <TableCell>{a.phase}</TableCell>
                <TableCell>{a.outcome && <OutcomeChip outcome={a.outcome} />}</TableCell>
                <TableCell align="right">{a.points}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </>
  )
}
