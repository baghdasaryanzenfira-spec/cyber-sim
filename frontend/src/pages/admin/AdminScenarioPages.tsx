import AddIcon from '@mui/icons-material/Add'
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import SaveIcon from '@mui/icons-material/Save'
import {
  Alert, Box, Button, Chip, Grid, IconButton, MenuItem, Paper, Snackbar, Switch, Tab, Table, TableBody, TableCell,
  TableHead, TableRow, Tabs, TextField, Tooltip, Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router'
import { apiErrorBody, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { ActionDef, ApiError, EventDef, ResourceDef, ScenarioDefinition } from '../../api/types'
import { CategoryChip, DifficultyChip, OutcomeChip, SeverityChip } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { useLoad } from '../../hooks/useLoad'
import { RowEditor, type FieldSpec } from './RowEditor'

// ------------------------------------------------------------------ list

export function AdminScenariosPage() {
  const navigate = useNavigate()
  const scenarios = useLoad(adminApi.scenarios)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const toggle = async (id: number, active: boolean) => {
    try {
      await adminApi.setScenarioActive(id, active)
      await scenarios.reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  const vary = async (id: number) => {
    setBusyId(id)
    setError(null)
    try {
      const result = await adminApi.generateVariation(id)
      setToast(`Variation "${result.scenario.definition.slug}" created as inactive draft (source: ${result.source}).`)
      await scenarios.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  if (scenarios.loading && !scenarios.data) return <Loading />
  return (
    <>
      <PageHeader title="Scenario management" subtitle="Create, edit, activate and vary training scenarios."
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/admin/scenarios/new')}>New scenario</Button>} />
      <ErrorAlert message={error ?? scenarios.error} />
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell><TableCell>Difficulty</TableCell><TableCell>Category</TableCell><TableCell>Version</TableCell>
              <TableCell>Actions</TableCell><TableCell>Events</TableCell><TableCell>Attempts</TableCell><TableCell>Active</TableCell><TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {scenarios.data?.map((s) => (
              <TableRow key={s.id} hover>
                <TableCell>
                  {s.title}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {s.slug}{s.sourceScenarioId ? ` · AI variation of #${s.sourceScenarioId}` : ''}
                  </Typography>
                </TableCell>
                <TableCell><DifficultyChip difficulty={s.difficulty} /></TableCell>
                <TableCell><CategoryChip category={s.category} /></TableCell>
                <TableCell>v{s.version}</TableCell>
                <TableCell>{s.actionCount}</TableCell>
                <TableCell>{s.eventCount}</TableCell>
                <TableCell>{s.attemptCount}</TableCell>
                <TableCell><Switch checked={s.active} onChange={(e) => toggle(s.id, e.target.checked)} /></TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  <Tooltip title="Generate a validated AI variation (inactive draft)">
                    <span>
                      <IconButton color="secondary" disabled={busyId != null} onClick={() => vary(s.id)}><AutoAwesomeIcon /></IconButton>
                    </span>
                  </Tooltip>
                  <IconButton component={RouterLink} to={`/admin/scenarios/${s.id}`}><EditIcon /></IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <Snackbar open={toast != null} autoHideDuration={6000} onClose={() => setToast(null)}>
        <Alert severity="success" variant="filled">{toast}</Alert>
      </Snackbar>
    </>
  )
}

// ------------------------------------------------------------------ editor

const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
const CATEGORIES = ['AUTHENTICATION', 'IAM', 'NETWORK', 'STORAGE', 'LOGGING', 'INCIDENT_RESPONSE']
const RESOURCE_TYPES = ['VIRTUAL_MACHINE', 'IAM_USER', 'IAM_ROLE', 'ACCESS_KEY', 'STORAGE_BUCKET', 'SECURITY_GROUP', 'DATABASE', 'LOAD_BALANCER']

const EMPTY: ScenarioDefinition = {
  slug: '', title: '', summary: '', description: '', difficulty: 'BEGINNER', category: 'INCIDENT_RESPONSE',
  estimatedMinutes: 15, incidentExplanation: '', recommendedSolution: '', hintPenalty: 2, outOfOrderPenalty: 5,
  active: false, learningObjectives: [''], resources: [], events: [], actions: [], hints: [],
}

const resourceFields: FieldSpec[] = [
  { name: 'key', label: 'Key', type: 'text', help: 'lower-case, e.g. vm-web-01' },
  { name: 'type', label: 'Type', type: 'select', options: RESOURCE_TYPES },
  { name: 'name', label: 'Name', type: 'text' },
  { name: 'region', label: 'Region', type: 'text' },
  { name: 'status', label: 'Initial status', type: 'text', help: 'e.g. RUNNING, ACTIVE, PUBLIC' },
  { name: 'properties', label: 'Properties (JSON)', type: 'json' },
]

function eventFields(def: ScenarioDefinition): FieldSpec[] {
  return [
    { name: 'key', label: 'Key', type: 'text', width: 4 },
    { name: 'offsetSeconds', label: 'Offset (seconds after incident start)', type: 'number', width: 4 },
    { name: 'type', label: 'Type', type: 'select', options: ['LOG', 'ALERT'], width: 4 },
    { name: 'source', label: 'Source (log name)', type: 'text', width: 4 },
    { name: 'severity', label: 'Severity', type: 'select', options: ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'], width: 4 },
    { name: 'resourceKey', label: 'Resource', type: 'select', optional: true, options: def.resources.map((r) => r.key), width: 4 },
    { name: 'message', label: 'Log message', type: 'multiline' },
    { name: 'details', label: 'Details (JSON)', type: 'json' },
    { name: 'evidence', label: 'This entry is evidence', type: 'bool', width: 4 },
    { name: 'revealedByActionKey', label: 'Revealed by investigation action', type: 'select', optional: true,
      options: def.actions.filter((a) => a.phase === 'INVESTIGATION').map((a) => a.key), help: 'empty = visible from the start', width: 8 },
    { name: 'evidenceNote', label: 'Why it is evidence (used in feedback)', type: 'multiline', optional: true },
  ]
}

function actionFields(def: ScenarioDefinition): FieldSpec[] {
  return [
    { name: 'key', label: 'Key', type: 'text', width: 4 },
    { name: 'label', label: 'Label', type: 'text', width: 8 },
    { name: 'description', label: 'Description', type: 'multiline' },
    { name: 'phase', label: 'Phase', type: 'select', options: ['INVESTIGATION', 'RESPONSE'], width: 4 },
    { name: 'category', label: 'Category', type: 'select', options: ['INSPECT', 'IDENTIFY', 'CONTAIN', 'ERADICATE', 'RECOVER', 'HARDEN'], width: 4 },
    { name: 'targetResourceKey', label: 'Target resource', type: 'select', optional: true, options: def.resources.map((r) => r.key), width: 4 },
    { name: 'outcome', label: 'Outcome (scoring)', type: 'select', options: ['EXPECTED', 'NEUTRAL', 'HARMFUL'], width: 4 },
    { name: 'points', label: 'Points', type: 'number', width: 4, help: '>0 expected, 0 neutral, <0 harmful' },
    { name: 'prerequisiteActionKey', label: 'Should come after', type: 'select', optional: true, options: def.actions.map((a) => a.key), width: 4 },
    { name: 'effectStatus', label: 'New status of target resource', type: 'text', optional: true, width: 6 },
    { name: 'resultMessage', label: 'Simulated console output', type: 'multiline' },
    { name: 'explanation', label: 'Explanation (shown in feedback)', type: 'multiline' },
  ]
}

type ListName = 'resources' | 'events' | 'actions'

export function ScenarioEditorPage() {
  const { id } = useParams()
  const isNew = id === 'new'
  const navigate = useNavigate()
  const [def, setDef] = useState<ScenarioDefinition>(EMPTY)
  const [meta, setMeta] = useState<{ version: number; maxScore: number } | null>(null)
  const [loading, setLoading] = useState(!isNew)
  const [tab, setTab] = useState(0)
  const [editing, setEditing] = useState<{ list: ListName; index: number; value: Record<string, unknown> } | null>(null)
  const [apiError, setApiError] = useState<ApiError | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [jsonText, setJsonText] = useState('')

  useEffect(() => {
    if (isNew) return
    adminApi.scenario(Number(id))
      .then((d) => { setDef(d.definition); setMeta({ version: d.version, maxScore: d.maxScore }) })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [id, isNew])

  useEffect(() => { if (tab === 5) setJsonText(JSON.stringify(def, null, 2)) }, [tab, def])

  if (loading) return <Loading />

  const set = <K extends keyof ScenarioDefinition>(key: K, value: ScenarioDefinition[K]) => setDef({ ...def, [key]: value })
  const maxScore = def.actions.filter((a) => a.outcome === 'EXPECTED').reduce((s, a) => s + a.points, 0)

  const save = async () => {
    setApiError(null)
    setError(null)
    try {
      const result = isNew ? await adminApi.createScenario(def) : await adminApi.updateScenario(Number(id), def)
      setMeta({ version: result.version, maxScore: result.maxScore })
      setSaved(true)
      if (isNew) navigate(`/admin/scenarios/${result.id}`, { replace: true })
    } catch (e) {
      const body = apiErrorBody(e)
      if (body?.fieldErrors?.length) setApiError(body)
      else setError(errorMessage(e))
    }
  }

  const listFields: Record<ListName, FieldSpec[]> = { resources: resourceFields, events: eventFields(def), actions: actionFields(def) }
  const newRow: Record<ListName, () => Record<string, unknown>> = {
    resources: () => ({ key: '', type: 'VIRTUAL_MACHINE', name: '', region: 'eu-central-1', status: 'RUNNING', properties: {} }),
    events: () => ({ key: '', offsetSeconds: 0, type: 'LOG', source: '', severity: 'INFO', message: '', details: {}, evidence: false }),
    actions: () => ({ key: '', label: '', description: '', phase: 'INVESTIGATION', category: 'INSPECT', outcome: 'NEUTRAL', points: 0, resultMessage: '', explanation: '' }),
  }
  const rows = (list: ListName) => def[list] as unknown as Record<string, unknown>[]
  const saveRow = (row: Record<string, unknown>) => {
    if (!editing) return
    const copy = [...rows(editing.list)]
    if (editing.index === copy.length) copy.push(row)
    else copy[editing.index] = row
    setDef({ ...def, [editing.list]: copy })
    setEditing(null)
  }
  const removeRow = (list: ListName, index: number) =>
    setDef({ ...def, [list]: rows(list).filter((_, i) => i !== index) })

  const rowActions = (list: ListName, index: number) => (
    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
      <IconButton size="small" onClick={() => setEditing({ list, index, value: rows(list)[index] })}><EditIcon fontSize="small" /></IconButton>
      <IconButton size="small" onClick={() => removeRow(list, index)}><DeleteIcon fontSize="small" /></IconButton>
    </TableCell>
  )
  const addButton = (list: ListName, label: string) => (
    <Button startIcon={<AddIcon />} sx={{ mt: 1 }} onClick={() => setEditing({ list, index: rows(list).length, value: newRow[list]() })}>{label}</Button>
  )

  return (
    <>
      <PageHeader title={isNew ? 'New scenario' : `Edit: ${def.title}`}
        subtitle={meta ? `Version ${meta.version} · max score ${maxScore}` : `Max score ${maxScore}`}
        actions={<>
          <Button component={RouterLink} to="/admin/scenarios">Back</Button>
          <Button variant="contained" startIcon={<SaveIcon />} onClick={save}>Save</Button>
        </>} />
      <ErrorAlert message={error} />
      {apiError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {apiError.message}
          <Box component="ul" sx={{ m: 0, pl: 2 }}>
            {apiError.fieldErrors.map((f, i) => <li key={i}><code>{f.field}</code>: {f.message}</li>)}
          </Box>
        </Alert>
      )}
      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable">
          <Tab label="General" />
          <Tab label={`Infrastructure (${def.resources.length})`} />
          <Tab label={`Logs, alerts & evidence (${def.events.length})`} />
          <Tab label={`Actions & scoring (${def.actions.length})`} />
          <Tab label={`Hints (${def.hints.length})`} />
          <Tab label="JSON" />
        </Tabs>
      </Paper>

      {tab === 0 && (
        <Paper sx={{ p: 3 }}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth label="Slug" value={def.slug} disabled={!isNew} onChange={(e) => set('slug', e.target.value)}
                helperText="Unique id, lower-case letters, digits and dashes" />
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}><TextField fullWidth label="Title" value={def.title} onChange={(e) => set('title', e.target.value)} /></Grid>
            <Grid size={12}><TextField fullWidth label="Summary" value={def.summary} onChange={(e) => set('summary', e.target.value)} /></Grid>
            <Grid size={12}><TextField fullWidth multiline minRows={4} label="Briefing / description" value={def.description} onChange={(e) => set('description', e.target.value)} /></Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField select fullWidth label="Difficulty" value={def.difficulty} onChange={(e) => set('difficulty', e.target.value as ScenarioDefinition['difficulty'])}>
                {DIFFICULTIES.map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField select fullWidth label="Category" value={def.category} onChange={(e) => set('category', e.target.value as ScenarioDefinition['category'])}>
                {CATEGORIES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 4, md: 2 }}><TextField fullWidth type="number" label="Minutes" value={def.estimatedMinutes} onChange={(e) => set('estimatedMinutes', Number(e.target.value))} /></Grid>
            <Grid size={{ xs: 4, md: 2 }}><TextField fullWidth type="number" label="Hint penalty" value={def.hintPenalty} onChange={(e) => set('hintPenalty', Number(e.target.value))} /></Grid>
            <Grid size={{ xs: 4, md: 2 }}><TextField fullWidth type="number" label="Order penalty" value={def.outOfOrderPenalty} onChange={(e) => set('outOfOrderPenalty', Number(e.target.value))} /></Grid>
            <Grid size={12}>
              <TextField fullWidth multiline minRows={3} label="Learning objectives (one per line)" value={def.learningObjectives.join('\n')}
                onChange={(e) => set('learningObjectives', e.target.value.split('\n'))} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth multiline minRows={5} label="Incident explanation (shown after completion)" value={def.incidentExplanation} onChange={(e) => set('incidentExplanation', e.target.value)} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth multiline minRows={5} label="Recommended solution" value={def.recommendedSolution} onChange={(e) => set('recommendedSolution', e.target.value)} /></Grid>
            <Grid size={12}><Box sx={{ display: 'flex', alignItems: 'center' }}><Switch checked={def.active} onChange={(e) => set('active', e.target.checked)} /> Active (visible to students)</Box></Grid>
          </Grid>
        </Paper>
      )}

      {tab === 1 && (
        <Paper sx={{ p: 2 }}>
          <Typography color="text.secondary" sx={{ mb: 1 }}>Initial state of the simulated cloud infrastructure.</Typography>
          <Table size="small">
            <TableHead><TableRow><TableCell>Key</TableCell><TableCell>Type</TableCell><TableCell>Name</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {def.resources.map((r: ResourceDef, i) => (
                <TableRow key={i}><TableCell>{r.key}</TableCell><TableCell>{r.type}</TableCell><TableCell>{r.name}</TableCell><TableCell>{r.status}</TableCell>{rowActions('resources', i)}</TableRow>
              ))}
            </TableBody>
          </Table>
          {addButton('resources', 'Add resource')}
        </Paper>
      )}

      {tab === 2 && (
        <Paper sx={{ p: 2 }}>
          <Typography color="text.secondary" sx={{ mb: 1 }}>
            Incident timeline. Mark evidence and choose which investigation action reveals hidden entries.
          </Typography>
          <Table size="small">
            <TableHead><TableRow><TableCell>Offset</TableCell><TableCell>Type</TableCell><TableCell>Severity</TableCell><TableCell>Source</TableCell><TableCell>Message</TableCell><TableCell>Evidence</TableCell><TableCell>Revealed by</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {[...def.events.entries()].sort((a, b) => a[1].offsetSeconds - b[1].offsetSeconds).map(([i, e]: [number, EventDef]) => (
                <TableRow key={i}>
                  <TableCell>+{e.offsetSeconds}s</TableCell><TableCell>{e.type}</TableCell><TableCell><SeverityChip severity={e.severity} /></TableCell>
                  <TableCell>{e.source}</TableCell>
                  <TableCell sx={{ maxWidth: 380, fontSize: 12 }}>{e.message}</TableCell>
                  <TableCell>{e.evidence && <Chip size="small" color="success" label="evidence" />}</TableCell>
                  <TableCell>{e.revealedByActionKey ?? 'start'}</TableCell>
                  {rowActions('events', i)}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {addButton('events', 'Add log entry / alert')}
        </Paper>
      )}

      {tab === 3 && (
        <Paper sx={{ p: 2 }}>
          <Typography color="text.secondary" sx={{ mb: 1 }}>
            Expected actions and scoring rules. Maximum score = sum of points of EXPECTED actions ({maxScore}).
          </Typography>
          <Table size="small">
            <TableHead><TableRow><TableCell>Key</TableCell><TableCell>Label</TableCell><TableCell>Phase</TableCell><TableCell>Outcome</TableCell><TableCell align="right">Points</TableCell><TableCell>After</TableCell><TableCell>Effect</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {def.actions.map((a: ActionDef, i) => (
                <TableRow key={i}>
                  <TableCell>{a.key}</TableCell><TableCell>{a.label}</TableCell><TableCell>{a.phase}</TableCell>
                  <TableCell><OutcomeChip outcome={a.outcome} /></TableCell>
                  <TableCell align="right" sx={{ color: a.points > 0 ? 'success.main' : a.points < 0 ? 'error.main' : 'text.secondary', fontWeight: 700 }}>{a.points}</TableCell>
                  <TableCell>{a.prerequisiteActionKey ?? ''}</TableCell>
                  <TableCell>{a.effectStatus ? `${a.targetResourceKey} → ${a.effectStatus}` : ''}</TableCell>
                  {rowActions('actions', i)}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {addButton('actions', 'Add action')}
        </Paper>
      )}

      {tab === 4 && (
        <Paper sx={{ p: 3 }}>
          <TextField fullWidth multiline minRows={6} label="Static hints, from general to specific (one per line)"
            helperText="Used by the offline tutor and as AI instructor notes"
            value={def.hints.join('\n')} onChange={(e) => set('hints', e.target.value.split('\n').filter((h) => h.trim()))} />
        </Paper>
      )}

      {tab === 5 && (
        <Paper sx={{ p: 2 }}>
          <TextField fullWidth multiline minRows={20} value={jsonText} onChange={(e) => setJsonText(e.target.value)}
            slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: 12 } } }} />
          <Button sx={{ mt: 1 }} onClick={() => {
            try { setDef(JSON.parse(jsonText)); setError(null) } catch { setError('The JSON document is not valid') }
          }}>Apply JSON</Button>
        </Paper>
      )}

      {editing && (
        <RowEditor open title={editing.index < rows(editing.list).length ? 'Edit' : 'Add'}
          fields={listFields[editing.list]}
          value={editing.value}
          onClose={() => setEditing(null)} onSave={saveRow} />
      )}
      <Snackbar open={saved} autoHideDuration={3000} onClose={() => setSaved(false)}>
        <Alert severity="success" variant="filled">Scenario saved and validated.</Alert>
      </Snackbar>
    </>
  )
}
