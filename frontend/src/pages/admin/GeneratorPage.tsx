import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh'
import { Alert, Box, Button, FormControlLabel, Grid, MenuItem, Paper, Switch, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { apiErrorBody, errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import type { Difficulty, GenerateRequest, ScenarioType } from '../../api/types'
import { PageHeader } from '../../components/Layout'

const TYPES: ScenarioType[] = ['SSH_BRUTE_FORCE', 'COMPROMISED_CREDENTIALS', 'PUBLIC_STORAGE_BUCKET']
const DIFFICULTIES: Difficulty[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

// Same rules as the backend validation of the generate request.
const IPV4 = /^(\d{1,3}\.){3}\d{1,3}$/
const ASSET = /^[A-Za-z0-9._-]+$/
const REGION = /^[a-z0-9-]+$/

interface Form {
  type: ScenarioType
  title: string
  difficulty: Difficulty | ''
  primaryAsset: string
  attackerIp: string
  region: string
  brief: string
  useAi: boolean
}

const INITIAL: Form = {
  type: 'SSH_BRUTE_FORCE', title: '', difficulty: '', primaryAsset: '', attackerIp: '', region: '', brief: '', useAi: false,
}

const blankToUndefined = (v: string) => (v.trim() === '' ? undefined : v.trim())

export function GeneratorPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [form, setForm] = useState<Form>(INITIAL)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))

  const validate = (): Record<string, string> => {
    const errors: Record<string, string> = {}
    if (form.attackerIp.trim() && !IPV4.test(form.attackerIp.trim())) errors.attackerIp = t('generator.badIp')
    if (form.primaryAsset.trim() && !ASSET.test(form.primaryAsset.trim())) errors.primaryAsset = t('generator.badAsset')
    if (form.region.trim() && !REGION.test(form.region.trim())) errors.region = t('generator.badRegion')
    return errors
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    const request: GenerateRequest = {
      type: form.type,
      title: blankToUndefined(form.title),
      difficulty: form.difficulty || undefined,
      primaryAsset: blankToUndefined(form.primaryAsset),
      attackerIp: blankToUndefined(form.attackerIp),
      region: blankToUndefined(form.region),
      brief: blankToUndefined(form.brief),
      useAi: form.useAi,
    }
    setBusy(true)
    try {
      const result = await adminApi.generate(request)
      navigate(`/admin/scenarios/${result.scenario.id}`, { state: { generated: true, aiSource: result.aiSource ?? null } })
    } catch (err) {
      const body = apiErrorBody(err)
      if (body?.fieldErrors?.length) {
        setFieldErrors(Object.fromEntries(body.fieldErrors.map((f) => [f.field, f.message])))
      }
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title={t('generator.title')} subtitle={t('generator.subtitle')} />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField select fullWidth label={t('generator.type')} value={form.type}
              onChange={(e) => set('type', e.target.value as ScenarioType)}
              helperText={t(`generator.typeText.${form.type}`)}>
              {TYPES.map((x) => <MenuItem key={x} value={x}>{t(`enums.scenarioType.${x}`)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField select fullWidth label={t('editor.difficulty')} value={form.difficulty}
              onChange={(e) => set('difficulty', e.target.value as Difficulty | '')}>
              <MenuItem value=""><em>{t('generator.templateDefault')}</em></MenuItem>
              {DIFFICULTIES.map((d) => <MenuItem key={d} value={d}>{t(`enums.difficulty.${d}`)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField fullWidth label={t('generator.titleField')} value={form.title}
              onChange={(e) => set('title', e.target.value)} helperText={t('generator.optional')} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth label={t('generator.primaryAsset')} value={form.primaryAsset}
              onChange={(e) => set('primaryAsset', e.target.value)}
              error={Boolean(fieldErrors.primaryAsset)} helperText={fieldErrors.primaryAsset ?? t('generator.assetHelp')} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth label={t('generator.attackerIp')} value={form.attackerIp}
              onChange={(e) => set('attackerIp', e.target.value)}
              error={Boolean(fieldErrors.attackerIp)} helperText={fieldErrors.attackerIp ?? t('generator.ipHelp')} />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField fullWidth label={t('generator.region')} value={form.region}
              onChange={(e) => set('region', e.target.value)}
              error={Boolean(fieldErrors.region)} helperText={fieldErrors.region ?? t('generator.regionHelp')} />
          </Grid>
          <Grid size={12}>
            <TextField fullWidth multiline minRows={3} label={t('generator.brief')} value={form.brief}
              onChange={(e) => set('brief', e.target.value)} helperText={t('generator.briefHelp')} />
          </Grid>
          <Grid size={12}>
            <FormControlLabel label={t('generator.useAi')}
              control={<Switch checked={form.useAi} onChange={(e) => set('useAi', e.target.checked)} />} />
            <Typography variant="body2" color="text.secondary">{t('generator.useAiHelp')}</Typography>
          </Grid>
          <Grid size={12}>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button type="submit" variant="contained" size="large" startIcon={<AutoFixHighIcon />} disabled={busy}>
                {busy ? t('generator.generating') : t('generator.submit')}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </>
  )
}
