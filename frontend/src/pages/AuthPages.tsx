import ShieldIcon from '@mui/icons-material/Shield'
import { Alert, Box, Button, Card, CardContent, Link, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router'
import { errorMessage } from '../api/client'
import { authApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box sx={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2,
      background: 'radial-gradient(circle at 20% 20%, rgba(34,211,238,0.12), transparent 40%), radial-gradient(circle at 80% 80%, rgba(167,139,250,0.12), transparent 40%)',
    }}>
      <Card sx={{ width: 420, maxWidth: '100%' }}>
        <CardContent sx={{ p: 4 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
            <ShieldIcon color="primary" fontSize="large" />
            <Typography variant="h5">CyberSim</Typography>
          </Stack>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            Cloud cyber incident simulation &amp; security specialist training
          </Typography>
          <Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>
          {children}
        </CardContent>
      </Card>
    </Box>
  )
}

export function LoginPage() {
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
    <AuthShell title="Sign in">
      {registered && <Alert severity="success" sx={{ mb: 2 }}>Account created — you can sign in now.</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box component="form" onSubmit={submit}>
        <Stack spacing={2}>
          <TextField label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" variant="contained" size="large" disabled={busy}>Sign in</Button>
          <Typography variant="body2" color="text.secondary">
            No account yet? <Link component={RouterLink} to="/register">Register</Link>
          </Typography>
        </Stack>
      </Box>
    </AuthShell>
  )
}

export function RegisterPage() {
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
    <AuthShell title="Create a student account">
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box component="form" onSubmit={submit}>
        <Stack spacing={2}>
          <TextField label="Display name" value={form.displayName} required
            onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          <TextField label="E-mail" type="email" value={form.email} required
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <TextField label="Password" type="password" value={form.password} required helperText="At least 8 characters"
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <Button type="submit" variant="contained" size="large" disabled={busy}>Register</Button>
          <Typography variant="body2" color="text.secondary">
            Already registered? <Link component={RouterLink} to="/login">Sign in</Link>
          </Typography>
        </Stack>
      </Box>
    </AuthShell>
  )
}
