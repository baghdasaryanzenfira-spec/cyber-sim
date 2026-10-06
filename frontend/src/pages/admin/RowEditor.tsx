import {
  Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, MenuItem, TextField,
} from '@mui/material'
import { useEffect, useState } from 'react'

export type FieldType = 'text' | 'multiline' | 'number' | 'select' | 'bool' | 'json'

export interface FieldSpec {
  name: string
  label: string
  type: FieldType
  options?: string[]
  optional?: boolean
  width?: 4 | 6 | 8 | 12
  help?: string
}

type Row = Record<string, unknown>

/**
 * Generic dialog for editing one row (resource, event or action) of a scenario definition, driven by a field list.
 * JSON fields are edited as text and parsed on save.
 */
export function RowEditor({ open, title, fields, value, onClose, onSave }: {
  open: boolean
  title: string
  fields: FieldSpec[]
  value: Row | null
  onClose: () => void
  onSave: (row: Row) => void
}) {
  const [draft, setDraft] = useState<Row>({})
  const [jsonText, setJsonText] = useState<Record<string, string>>({})
  const [jsonError, setJsonError] = useState<string | null>(null)

  useEffect(() => {
    if (!value) return
    setDraft(value)
    setJsonText(Object.fromEntries(fields.filter((f) => f.type === 'json')
      .map((f) => [f.name, JSON.stringify(value[f.name] ?? {}, null, 2)])))
    setJsonError(null)
    // reset only when a different row is opened (fields are recreated on every parent render)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  const set = (name: string, v: unknown) => setDraft((d) => ({ ...d, [name]: v }))

  const save = () => {
    const row: Row = { ...draft }
    for (const f of fields) {
      if (f.type === 'json') {
        try {
          row[f.name] = JSON.parse(jsonText[f.name] || '{}')
        } catch {
          setJsonError(`${f.label} is not valid JSON`)
          return
        }
      }
      if (f.optional && (row[f.name] === '' || row[f.name] === undefined)) row[f.name] = null
    }
    onSave(row)
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          {fields.map((f) => (
            <Grid key={f.name} size={{ xs: 12, md: f.width ?? (f.type === 'multiline' || f.type === 'json' ? 12 : 6) }}>
              {f.type === 'bool' ? (
                <FormControlLabel label={f.label} control={
                  <Checkbox checked={Boolean(draft[f.name])} onChange={(e) => set(f.name, e.target.checked)} />} />
              ) : f.type === 'json' ? (
                <TextField fullWidth multiline minRows={3} label={f.label} value={jsonText[f.name] ?? ''}
                  error={jsonError?.startsWith(f.label)} helperText={jsonError?.startsWith(f.label) ? jsonError : f.help}
                  onChange={(e) => setJsonText((j) => ({ ...j, [f.name]: e.target.value }))}
                  slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: 12 } } }} />
              ) : f.type === 'select' ? (
                <TextField select fullWidth label={f.label} value={(draft[f.name] as string | null) ?? ''} helperText={f.help}
                  onChange={(e) => set(f.name, e.target.value)}>
                  {f.optional && <MenuItem value=""><em>none</em></MenuItem>}
                  {(f.options ?? []).map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                </TextField>
              ) : (
                <TextField fullWidth label={f.label} type={f.type === 'number' ? 'number' : 'text'}
                  multiline={f.type === 'multiline'} minRows={f.type === 'multiline' ? 2 : undefined} helperText={f.help}
                  value={(draft[f.name] as string | number | null) ?? ''}
                  onChange={(e) => set(f.name, f.type === 'number' ? Number(e.target.value) : e.target.value)} />
              )}
            </Grid>
          ))}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save}>Apply</Button>
      </DialogActions>
    </Dialog>
  )
}
