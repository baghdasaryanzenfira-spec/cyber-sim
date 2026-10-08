import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { RequireAdmin, RequireAuth } from './auth/guards'
import { Layout } from './components/Layout'
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage'
import { AdminScenariosPage, ScenarioEditorPage } from './pages/admin/AdminScenarioPages'
import { ExamDetailPage, ExamsPage } from './pages/admin/ExamsPages'
import { GeneratorPage } from './pages/admin/GeneratorPage'
import { LoginPage } from './pages/AuthPages'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [{
      element: <Layout />,
      children: [
        { index: true, element: <Navigate to="/admin" replace /> },
        {
          path: 'admin',
          element: <RequireAdmin />,
          children: [
            { index: true, element: <AdminDashboardPage /> },
            { path: 'generate', element: <GeneratorPage /> },
            { path: 'scenarios', element: <AdminScenariosPage /> },
            { path: 'scenarios/new', element: <ScenarioEditorPage /> },
            { path: 'scenarios/:id', element: <ScenarioEditorPage /> },
            { path: 'exams', element: <ExamsPage /> },
            { path: 'exams/:id', element: <ExamDetailPage /> },
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
