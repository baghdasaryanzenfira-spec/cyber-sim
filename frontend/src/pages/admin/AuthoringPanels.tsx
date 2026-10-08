import CancelIcon from '@mui/icons-material/Cancel'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import PublishIcon from '@mui/icons-material/Publish'
import RestoreIcon from '@mui/icons-material/Restore'
import {
  Alert, Box, Button, Chip, Collapse, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,
  LinearProgress, Paper, Snackbar, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type {
  AdminScenarioDetail, Evaluation, IssueSeverity, QualityReport, ScenarioDefinition, ScenarioStatus, TestPath,
  ValidationIssue, VersionSummary,
} from '../../api/types'
import { GradeChip, OutcomeChip, scoreColor } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { useLoad } from '../../hooks/useLoad'
import type { ScenarioMeta } from './scenarioMeta'

export { GraphPanel } from './GraphPanel'

const PUBLISH_THRESHOLD = 70

// ------------------------------------------------------------------ validation

const SEVERITY_ORDER: IssueSeverity[] = ['ERROR', 'WARNING', 'INFO']
const SEVERITY_COLOR = { ERROR: 'error', WARNING: 'warning', INFO: 'default' } as const
const SEVERITY_TEXT = { ERROR: 'error.main', WARNING: 'warning.main', INFO: 'text.secondary' } as const

function IssueTable({ issues }: { issues: ValidationIssue[] }) {
  const { t } = useTranslation()
  const sorted = [...issues].sort((a, b) => SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity))
  return (
    <Table size="small" sx={{ mt: 1 }}>
      <TableHead>
        <TableRow>
          <TableCell>{t('review.severity')}</TableCell><TableCell>{t('review.code')}</TableCell>
          <TableCell>{t('review.path')}</TableCell><TableCell>{t('review.message')}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {sorted.map((i, n) => (
          <TableRow key={n}>
            <TableCell>
              <Chip size="small" variant="outlined" color={SEVERITY_COLOR[i.severity]} label={t(`enums.issueSeverity.${i.severity}`)} />
            </TableCell>
            <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{i.code}</TableCell>
            <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{i.path}</TableCell>
            <TableCell sx={{ color: SEVERITY_TEXT[i.severity] }}>{i.message}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function ValidationSection({ evaluation }: { evaluation: Evaluation }) {
  const { t } = useTranslation()
  const v = evaluation.validation
  return (
    <Paper sx={{ p: 2.5, mb: 2 }}>
      <Typography variant="h6" gutterBottom>{t('review.validation')}</Typography>
      <Alert severity={v.valid ? 'success' : 'error'}>{v.valid ? t('review.valid') : t('review.invalid')}</Alert>
      <Box sx={{ display: 'flex', gap: 1, mt: 1.5, flexWrap: 'wrap' }}>
        <Chip size="small" color={v.errorCount ? 'error' : 'default'} label={t('review.errors', { count: v.errorCount })} />
        <Chip size="small" color={v.warningCount ? 'warning' : 'default'} label={t('review.warnings', { count: v.warningCount })} />
        {v.stats && Object.entries(v.stats).map(([key, value]) => (
          <Chip key={key} size="small" variant="outlined" label={`${t(`review.stats.${key}`)}: ${value}`} />
        ))}
      </Box>
      {v.issues.length > 0 ? <IssueTable issues={v.issues} /> : (
        <Typography color="text.secondary" sx={{ mt: 1.5 }}>{t('review.noIssues')}</Typography>
      )}
    </Paper>
  )
}

// ------------------------------------------------------------------ test runner

function PathCard({ path }: { path: TestPath }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography sx={{ fontWeight: 700 }}>{t(`enums.testPath.${path.path}`)}</Typography>
        <Chip size="small" color={path.passed ? 'success' : 'error'} label={path.passed ? t('review.passed') : t('review.failed')} />
        <Chip size="small" variant="outlined" label={t('review.scoreLine', { percent: path.scorePercent, raw: path.rawScore, max: path.maxScore })} />
        <Chip size="small" variant="outlined" label={t('review.evidenceLine', { found: path.evidenceRevealed, total: path.evidenceTotal })} />
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ my: 1 }}>{path.description}</Typography>
      {path.checks.map((c) => (
        <Box key={c.name} sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', py: 0.25 }}>
          {c.passed
            ? <CheckCircleIcon fontSize="small" color="success" />
            : <CancelIcon fontSize="small" color="error" />}
          <Typography variant="body2"><b>{c.name}</b> — {c.message}</Typography>
        </Box>
      ))}
      <Button size="small" sx={{ mt: 1 }} onClick={() => setOpen(!open)} endIcon={open ? <ExpandLessIcon /> : <ExpandMoreIcon />}>
        {t('review.steps', { count: path.steps.length })}
      </Button>
      <Collapse in={open} unmountOnExit>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>#</TableCell><TableCell>{t('review.action')}</TableCell><TableCell>{t('editor.outcome')}</TableCell>
              <TableCell align="right">{t('editor.points')}</TableCell><TableCell>{t('review.flags')}</TableCell>
              <TableCell align="right">{t('review.revealed')}</TableCell><TableCell>{t('review.resourceEffect')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {path.steps.map((s) => (
              <TableRow key={s.sequence}>
                <TableCell>{s.sequence}</TableCell>
                <TableCell>{s.label}</TableCell>
                <TableCell><OutcomeChip outcome={s.outcome} /></TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, color: s.points > 0 ? 'success.main' : s.points < 0 ? 'error.main' : 'text.secondary' }}>
                  {s.points}
                </TableCell>
                <TableCell>
                  {s.outOfOrder && <Chip size="small" color="warning" variant="outlined" label={t('review.outOfOrder')} sx={{ mr: 0.5 }} />}
                  {s.duplicate && <Chip size="small" variant="outlined" label={t('review.duplicate')} />}
                </TableCell>
                <TableCell align="right">{s.revealedEvents}</TableCell>
                <TableCell>{s.resourceEffect ?? ''}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Collapse>
    </Paper>
  )
}

function TestsSection({ evaluation }: { evaluation: Evaluation }) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 2.5, mb: 2 }}>
      <Typography variant="h6" gutterBottom>{t('review.tests')}</Typography>
      {evaluation.tests
        ? evaluation.tests.paths.map((p) => <PathCard key={p.path} path={p} />)
        : <Alert severity="info">{t('review.testsSkipped')}</Alert>}
    </Paper>
  )
}

// ------------------------------------------------------------------ quality

const ratioColor = (ratio: number) => scoreColor(ratio * 100)

export function QualityBreakdown({ quality }: { quality: QualityReport }) {
  return (
    <>
      {quality.components.map((c) => {
        const ratio = c.max > 0 ? c.score / c.max : 0
        return (
          <Box key={c.name} sx={{ mb: 1.5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{c.name}</Typography>
              <Typography variant="body2">{c.score} / {c.max}</Typography>
            </Box>
            <LinearProgress variant="determinate" value={Math.min(100, ratio * 100)}
              color={ratioColor(ratio) === 'inherit' ? 'primary' : ratioColor(ratio)} sx={{ height: 8, borderRadius: 4 }} />
            <Typography variant="caption" color="text.secondary">{c.details}</Typography>
          </Box>
        )
      })}
    </>
  )
}

function QualitySection({ quality }: { quality: QualityReport }) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 2.5, mb: 2 }}>
      <Typography variant="h6" gutterBottom>{t('review.quality')}</Typography>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, mb: 1 }}>
        <Typography variant="h3" sx={{ fontWeight: 700 }} color={scoreColor(quality.score)}>{quality.score}</Typography>
        <Typography color="text.secondary">/ 100</Typography>
        <GradeChip grade={quality.grade} />
      </Box>
      <Typography variant="body2" color={quality.meetsPublishThreshold ? 'success.main' : 'warning.main'} sx={{ mb: 2 }}>
        {t(quality.meetsPublishThreshold ? 'review.thresholdMet' : 'review.thresholdNotMet', { threshold: PUBLISH_THRESHOLD })}
      </Typography>
      <QualityBreakdown quality={quality} />
    </Paper>
  )
}

// ------------------------------------------------------------------ publish

function PublishSection({ id, evaluation, status, publishedVersion, dirty, onPublished }: {
  id: number
  evaluation: Evaluation
  status: ScenarioStatus
  publishedVersion: number | null
  dirty: boolean
  onPublished: (detail: AdminScenarioDetail) => void
}) {
  const { t } = useTranslation()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<number | null>(null)
  const next = (publishedVersion ?? 0) + 1
  const archived = status === 'ARCHIVED'
  const disabled = busy || dirty || archived || !evaluation.publishable

  const publish = async () => {
    setBusy(true)
    setError(null)
    try {
      const detail = await adminApi.publish(id, note.trim() || undefined)
      setNote('')
      setDone(detail.publishedVersion ?? next)
      onPublished(detail)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Paper sx={{ p: 2.5, mb: 2 }}>
      <Typography variant="h6" gutterBottom>{t('review.publish')}</Typography>
      <ErrorAlert message={error} />
      {evaluation.blockers.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t('review.blockers')}
          <Box component="ul" sx={{ m: 0, pl: 2 }}>{evaluation.blockers.map((b, i) => <li key={i}>{b}</li>)}</Box>
        </Alert>
      )}
      {archived && <Alert severity="info" sx={{ mb: 2 }}>{t('review.archivedNote')}</Alert>}
      <TextField fullWidth label={t('review.changeNote')} value={note} onChange={(e) => setNote(e.target.value)} sx={{ mb: 2 }} />
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Button variant="contained" startIcon={<PublishIcon />} disabled={disabled} onClick={publish}>
          {t('review.publishAs', { version: next })}
        </Button>
        {dirty && <Typography variant="body2" color="warning.main">{t('review.saveFirst')}</Typography>}
      </Box>
      <Snackbar open={done != null} autoHideDuration={5000} onClose={() => setDone(null)}>
        <Alert severity="success" variant="filled">{t('review.published', { version: done })}</Alert>
      </Snackbar>
    </Paper>
  )
}

/**
 * The review tab: validation, test runner, quality score and the publish panel. Checks run on the CURRENT
 * (possibly unsaved) editor content, so feedback is live; publishing always uses the stored draft.
 */
export function ReviewPanel({ id, def, meta, dirty, onPublished }: {
  id: number
  def: ScenarioDefinition
  meta: ScenarioMeta
  dirty: boolean
  onPublished: (detail: AdminScenarioDetail) => void
}) {
  const { t } = useTranslation()
  const [runKey, setRunKey] = useState(0)
  const check = useLoad(() => adminApi.evaluateDraft(def), [runKey])
  const evaluation = check.data

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Button variant="outlined" startIcon={<PlayArrowIcon />} disabled={check.loading} onClick={() => setRunKey(runKey + 1)}>
          {t('review.run')}
        </Button>
        {check.loading && <Typography variant="body2" color="text.secondary">{t('review.running')}</Typography>}
      </Box>
      <ErrorAlert message={check.error} />
      {!evaluation ? (check.loading && <Loading />) : (
        <>
          <ValidationSection evaluation={evaluation} />
          <TestsSection evaluation={evaluation} />
          <QualitySection quality={evaluation.quality} />
          <PublishSection id={id} evaluation={evaluation} status={meta.status} publishedVersion={meta.publishedVersion}
            dirty={dirty} onPublished={(d) => { onPublished(d); setRunKey(runKey + 1) }} />
        </>
      )}
    </>
  )
}

// ------------------------------------------------------------------ versions

function VersionDetails({ id, number }: { id: number; number: number }) {
  const { t } = useTranslation()
  const detail = useLoad(() => adminApi.version(id, number), [id, number])
  if (detail.loading && !detail.data) return <Loading />
  if (!detail.data) return <ErrorAlert message={detail.error} />
  const d = detail.data.definition
  return (
    <Box sx={{ p: 2 }}>
      <Typography sx={{ fontWeight: 600 }}>{d.title}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('versions.counts', { resources: d.resources.length, events: d.events.length, actions: d.actions.length })}
      </Typography>
      <QualityBreakdown quality={detail.data.quality} />
    </Box>
  )
}

function VersionRow({ scenarioId, v, canRestore, onRestore }: { scenarioId: number; v: VersionSummary; canRestore: boolean; onRestore: (v: VersionSummary) => void }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return (
    <>
      <TableRow hover>
        <TableCell>v{v.versionNumber}</TableCell>
        <TableCell>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>{v.qualityScore} <GradeChip grade={v.grade} /></Box>
        </TableCell>
        <TableCell>{v.changeNote ?? '—'}</TableCell>
        <TableCell>{new Date(v.publishedAt).toLocaleString()}</TableCell>
        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
          <Button size="small" onClick={() => setOpen(!open)}>{open ? t('versions.hide') : t('versions.view')}</Button>
          <Button size="small" startIcon={<RestoreIcon />} disabled={!canRestore} onClick={() => onRestore(v)}>
            {t('versions.restore')}
          </Button>
        </TableCell>
      </TableRow>
      {open && (
        <TableRow>
          <TableCell colSpan={5} sx={{ bgcolor: 'rgba(148,163,184,0.04)' }}><VersionDetails id={scenarioId} number={v.versionNumber} /></TableCell>
        </TableRow>
      )}
    </>
  )
}

/** Published versions of a scenario; restoring loads one into the editable draft (it never changes history). */
export function VersionsPanel({ id, refreshKey, onRestored }: {
  id: number
  refreshKey: number
  onRestored: (detail: AdminScenarioDetail) => void
}) {
  const { t } = useTranslation()
  const versions = useLoad(() => adminApi.versions(id), [id, refreshKey])
  const [target, setTarget] = useState<VersionSummary | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const restore = async () => {
    if (!target) return
    setBusy(true)
    setError(null)
    try {
      onRestored(await adminApi.restoreVersion(id, target.versionNumber))
      setTarget(null)
    } catch (e) {
      setError(errorMessage(e))
      setTarget(null)
    } finally {
      setBusy(false)
    }
  }

  if (versions.loading && !versions.data) return <Loading />
  if (!versions.data) return <ErrorAlert message={versions.error} />
  return (
    <Paper sx={{ p: 2 }}>
      <ErrorAlert message={error} />
      {versions.data.length === 0 ? (
        <Typography color="text.secondary">{t('versions.empty')}</Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('versions.version')}</TableCell><TableCell>{t('versions.quality')}</TableCell>
              <TableCell>{t('versions.changeNote')}</TableCell><TableCell>{t('versions.publishedAt')}</TableCell><TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {versions.data.map((v) => <VersionRow key={v.id} scenarioId={id} v={v} canRestore={!busy} onRestore={setTarget} />)}
          </TableBody>
        </Table>
      )}
      <Dialog open={target != null} onClose={() => setTarget(null)}>
        <DialogTitle>{t('versions.restoreTitle', { version: target?.versionNumber })}</DialogTitle>
        <DialogContent><DialogContentText>{t('versions.restoreText')}</DialogContentText></DialogContent>
        <DialogActions>
          <Button onClick={() => setTarget(null)}>{t('common.cancel')}</Button>
          <Button variant="contained" disabled={busy} onClick={restore}>{t('versions.restore')}</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
