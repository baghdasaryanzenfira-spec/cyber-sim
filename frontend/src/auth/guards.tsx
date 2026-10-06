import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthContext'
import { Loading } from '../components/Feedback'

/** Requires a logged-in user; otherwise redirects to /login and remembers the target page. */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Loading />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Requires the ADMIN role. This is only UX — the backend enforces the same rule on /api/admin/**. */
export function RequireAdmin() {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}
