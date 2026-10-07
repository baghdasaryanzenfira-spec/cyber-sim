import ShieldIcon from '@mui/icons-material/Shield'
import { Alert, Box, Button, Card, CardContent, Link, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router'
import { errorMessage } from '../api/client'
import { authApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { LanguageSwitcher } from '../components/LanguageSwitcher'

function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation()
  return (
    <Box sx={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2,
      background: 'radial-gradient(circle at 20% 20%, rgba(34,211,238,0.12), transparent 40%), radial-gradient(circle at 80% 80%, rgba(167,139,250,0.12), transparent 40%)',
    }}>
      <Card sx={{ width: 420, maxWidth: '100%' }}>
        <CardContent sx={{ p: 4 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
            <ShieldIcon color="primary" fontSize="large" />
            <Typography variant="h5" sx={{ flexGrow: 1 }}>CyberSim</Typography>
            <LanguageSwitcher />
          </Stack>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {t('auth.subtitle')}
          </Typography>
          <Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>
          {children}
        </CardContent>
      </Card>
    </Box>
  )
}

export function LoginPage() {
  const { t } = useTranslation()
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const registered = (location.state as { registered?: boolean } | null)?.registered

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const u = await login(email, password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? (u.role === 'ADMIN' ? '/admin' : '/'), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title={t('auth.signIn')}>
      {registered && <Alert severity="success" sx={{ mb: 2 }}>{t('auth.registered')}</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box component="form" onSubmit={submit}>
        <Stack spacing={2}>
          <TextField label={t('auth.email')} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          <TextField label={t('auth.password')} type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" variant="contained" size="large" disabled={busy}>{t('auth.signIn')}</Button>
          <Typography variant="body2" color="text.secondary">
            {t('auth.noAccount')} <Link component={RouterLink} to="/register">{t('auth.register')}</Link>
          </Typography>
        </Stack>
      </Box>
    </AuthShell>
  )
}

export function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', displayName: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await authApi.register(form.email, form.displayName, form.password)
      navigate('/login', { state: { registered: true } })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title={t('auth.registerTitle')}>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box component="form" onSubmit={submit}>
        <Stack spacing={2}>
          <TextField label={t('auth.displayName')} value={form.displayName} required
            onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          <TextField label={t('auth.email')} type="email" value={form.email} required
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <TextField label={t('auth.password')} type="password" value={form.password} required helperText={t('auth.passwordHelp')}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <Button type="submit" variant="contained" size="large" disabled={busy}>{t('auth.register')}</Button>
          <Typography variant="body2" color="text.secondary">
            {t('auth.alreadyRegistered')} <Link component={RouterLink} to="/login">{t('auth.signIn')}</Link>
          </Typography>
        </Stack>
      </Box>
    </AuthShell>
  )
}
