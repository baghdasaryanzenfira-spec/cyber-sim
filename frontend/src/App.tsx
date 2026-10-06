import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { RequireAdmin, RequireAuth } from './auth/guards'
import { Layout } from './components/Layout'
import { AdminAnalyticsPage } from './pages/admin/AdminAnalyticsPage'
import { AdminAttemptDetailPage, AdminAttemptsPage } from './pages/admin/AdminAttemptsPages'
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminScenariosPage, ScenarioEditorPage } from './pages/admin/AdminScenarioPages'
import { AdminUserDetailPage, AdminUsersPage } from './pages/admin/AdminUsersPages'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { DashboardPage } from './pages/DashboardPage'
import { HistoryPage } from './pages/HistoryPage'
import { ResultPage } from './pages/ResultPage'
import { ScenarioDetailPage, ScenarioListPage } from './pages/ScenarioPages'
import { ActiveSimulationPage } from './pages/simulation/ActiveSimulationPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [{
      element: <Layout />,
      children: [
        { index: true, element: <DashboardPage /> },
        { path: 'scenarios', element: <ScenarioListPage /> },
        { path: 'scenarios/:id', element: <ScenarioDetailPage /> },
        { path: 'simulations/:id', element: <ActiveSimulationPage /> },
        { path: 'simulations/:id/result', element: <ResultPage /> },
        { path: 'history', element: <HistoryPage /> },
        {
          path: 'admin',
          element: <RequireAdmin />,
          children: [
            { index: true, element: <AdminDashboardPage /> },
            { path: 'users', element: <AdminUsersPage /> },
            { path: 'users/:id', element: <AdminUserDetailPage /> },
            { path: 'scenarios', element: <AdminScenariosPage /> },
            { path: 'scenarios/:id', element: <ScenarioEditorPage /> },
            { path: 'attempts', element: <AdminAttemptsPage /> },
            { path: 'attempts/:id', element: <AdminAttemptDetailPage /> },
            { path: 'analytics', element: <AdminAnalyticsPage /> },
          ],
        },
      ],
    }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
