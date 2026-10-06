import { Button, Chip, Paper, Stack, Switch, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import { errorMessage } from '../../api/client'
import { adminApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { scoreColor } from '../../components/Chips'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { PageHeader } from '../../components/Layout'
import { ProgressSummary } from '../../components/ProgressView'
import { useLoad } from '../../hooks/useLoad'
import { AttemptsTable } from './AdminAttemptsPages'

export function AdminUsersPage() {
  const { user: me } = useAuth()
  const users = useLoad(adminApi.users)
  const [search, setSearch] = useState('')
  const [error, setError] = useState<string | null>(null)

  const toggle = async (id: number, enabled: boolean) => {
    try {
      await adminApi.setUserEnabled(id, enabled)
      await users.reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  if (users.loading && !users.data) return <Loading />
  const q = search.toLowerCase()
  const rows = (users.data ?? []).filter((u) => !q || u.email.includes(q) || u.displayName.toLowerCase().includes(q))

  return (
    <>
      <PageHeader title="Users" subtitle="Registered accounts and their training activity."
        actions={<TextField size="small" label="Search" value={search} onChange={(e) => setSearch(e.target.value)} />} />
      <ErrorAlert message={error ?? users.error} />
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell><TableCell>E-mail</TableCell><TableCell>Role</TableCell><TableCell>Attempts</TableCell>
              <TableCell>Completed</TableCell><TableCell>Avg score</TableCell><TableCell>Last login</TableCell><TableCell>Enabled</TableCell><TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((u) => (
              <TableRow key={u.id} hover>
                <TableCell>{u.displayName}</TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell><Chip size="small" label={u.role} color={u.role === 'ADMIN' ? 'secondary' : 'default'} variant="outlined" /></TableCell>
                <TableCell>{u.attempts}</TableCell>
                <TableCell>{u.completed}</TableCell>
                <TableCell><Typography color={scoreColor(u.averageScore)} sx={{ fontWeight: 700 }}>{u.averageScore ?? '—'}</Typography></TableCell>
                <TableCell>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'never'}</TableCell>
                <TableCell>
                  <Tooltip title={u.id === me?.id ? 'You cannot disable your own account' : ''}>
                    <span><Switch checked={u.enabled} disabled={u.id === me?.id} onChange={(e) => toggle(u.id, e.target.checked)} /></span>
                  </Tooltip>
                </TableCell>
                <TableCell><Button size="small" component={RouterLink} to={`/admin/users/${u.id}`}>Progress</Button></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </>
  )
}

export function AdminUserDetailPage() {
  const { id } = useParams()
  const detail = useLoad(() => adminApi.user(Number(id)), [id])
  if (detail.loading) return <Loading />
  if (!detail.data) return <ErrorAlert message={detail.error} />
  const { user, progress } = detail.data
  return (
    <>
      <PageHeader title={user.displayName} subtitle={`${user.email} · ${user.role} · registered ${new Date(user.createdAt).toLocaleDateString()}`} />
      <ProgressSummary progress={progress} />
      <Stack spacing={1}>
        <Typography variant="h6">Attempts</Typography>
        <AttemptsTable filter={{ userId: user.id }} />
      </Stack>
    </>
  )
}
