import { http } from './client'
import type {
  ActionResult,
  AdminScenarioDetail,
  AdminScenarioSummary,
  AdminUserRow,
  AnalyticsOverview,
  AssistantMessage,
  AssistantReply,
  AttemptDetail,
  AttemptRow,
  AuthResponse,
  Mistakes,
  PageView,
  ProgressView,
  Recommendation,
  AiSource,
  ScenarioBriefing,
  ScenarioDefinition,
  ScenarioSummary,
  SimulationDetail,
  SimulationResult,
  SimulationStatus,
  SimulationSummary,
  User,
} from './types'

const data = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const authApi = {
  login: (email: string, password: string) => data(http.post<AuthResponse>('/auth/login', { email, password })),
  register: (email: string, displayName: string, password: string) =>
    data(http.post<User>('/auth/register', { email, displayName, password })),
  me: () => data(http.get<User>('/auth/me')),
}

export const scenarioApi = {
  list: () => data(http.get<ScenarioSummary[]>('/scenarios')),
  get: (id: number) => data(http.get<ScenarioBriefing>(`/scenarios/${id}`)),
}

export const simulationApi = {
  create: (scenarioId: number) => data(http.post<SimulationDetail>('/simulations', { scenarioId })),
  list: () => data(http.get<SimulationSummary[]>('/simulations')),
  get: (id: number) => data(http.get<SimulationDetail>(`/simulations/${id}`)),
  start: (id: number) => data(http.post<SimulationDetail>(`/simulations/${id}/start`)),
  act: (id: number, actionKey: string, note?: string) =>
    data(http.post<ActionResult>(`/simulations/${id}/actions`, { actionKey, note })),
  flag: (id: number, eventId: number, flagged: boolean) =>
    data(http.put<SimulationDetail>(`/simulations/${id}/events/${eventId}/flag`, { flagged })),
  complete: (id: number) => data(http.post<SimulationResult>(`/simulations/${id}/complete`)),
  abandon: (id: number) => data(http.post<SimulationDetail>(`/simulations/${id}/abandon`)),
  result: (id: number) => data(http.get<SimulationResult>(`/simulations/${id}/result`)),
  hint: (id: number) => data(http.post<AssistantReply>(`/simulations/${id}/assistant/hint`)),
  ask: (id: number, question: string) =>
    data(http.post<AssistantReply>(`/simulations/${id}/assistant/ask`, { question })),
  messages: (id: number) => data(http.get<AssistantMessage[]>(`/simulations/${id}/assistant/messages`)),
}

export const progressApi = {
  me: () => data(http.get<ProgressView>('/progress/me')),
  recommendations: () =>
    data(http.get<{ recommendations: Recommendation[]; source: AiSource }>('/progress/me/recommendations')),
}

export const adminApi = {
  users: () => data(http.get<AdminUserRow[]>('/admin/users')),
  user: (id: number) => data(http.get<{ user: User; progress: ProgressView }>(`/admin/users/${id}`)),
  setUserEnabled: (id: number, enabled: boolean) => data(http.patch<User>(`/admin/users/${id}/status`, { enabled })),
  scenarios: () => data(http.get<AdminScenarioSummary[]>('/admin/scenarios')),
  scenario: (id: number) => data(http.get<AdminScenarioDetail>(`/admin/scenarios/${id}`)),
  createScenario: (def: ScenarioDefinition) => data(http.post<AdminScenarioDetail>('/admin/scenarios', def)),
  updateScenario: (id: number, def: ScenarioDefinition) =>
    data(http.put<AdminScenarioDetail>(`/admin/scenarios/${id}`, def)),
  setScenarioActive: (id: number, active: boolean) =>
    data(http.patch<AdminScenarioDetail>(`/admin/scenarios/${id}/status`, { active })),
  generateVariation: (id: number) =>
    data(http.post<{ scenario: AdminScenarioDetail; source: AiSource }>(`/admin/scenarios/${id}/variations`)),
  attempts: (params: { userId?: number; scenarioId?: number; status?: SimulationStatus; page?: number; size?: number }) =>
    data(http.get<PageView<AttemptRow>>('/admin/simulations', { params })),
  attempt: (id: number) => data(http.get<AttemptDetail>(`/admin/simulations/${id}`)),
  overview: () => data(http.get<AnalyticsOverview>('/admin/analytics/overview')),
  mistakes: () => data(http.get<Mistakes>('/admin/analytics/mistakes')),
}
