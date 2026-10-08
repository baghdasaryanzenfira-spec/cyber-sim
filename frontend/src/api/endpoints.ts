import { http } from './client'
import type {
  AdminScenarioDetail,
  AdminScenarioSummary,
  AiSource,
  AuthResponse,
  Evaluation,
  GenerateRequest,
  GenerateResponse,
  ScenarioDefinition,
  ScenarioGraph,
  User,
  VersionDetail,
  VersionSummary,
} from './types'

const data = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const authApi = {
  login: (email: string, password: string) => data(http.post<AuthResponse>('/auth/login', { email, password })),
  me: () => data(http.get<User>('/auth/me')),
}

export const aiApi = {
  /** Translate one piece of displayed text. Nothing is stored server-side. */
  translate: (text: string, language: string) =>
    data(http.post<{ text: string; source: AiSource }>('/ai/translate', { text, language })),
}

const base = (id: number) => `/admin/scenarios/${id}`

export const adminApi = {
  scenarios: () => data(http.get<AdminScenarioSummary[]>('/admin/scenarios')),
  scenario: (id: number) => data(http.get<AdminScenarioDetail>(base(id))),
  createScenario: (def: ScenarioDefinition) => data(http.post<AdminScenarioDetail>('/admin/scenarios', def)),
  updateScenario: (id: number, def: ScenarioDefinition) => data(http.put<AdminScenarioDetail>(base(id), def)),
  archiveScenario: (id: number, archived: boolean) =>
    data(http.patch<AdminScenarioDetail>(`${base(id)}/archive`, { archived })),
  deleteScenario: (id: number) => data(http.delete<void>(base(id))),
  generate: (req: GenerateRequest) => data(http.post<GenerateResponse>('/admin/scenarios/generate', req)),
  generateVariation: (id: number) =>
    data(http.post<{ scenario: AdminScenarioDetail; source: AiSource }>(`${base(id)}/variations`)),
  graph: (id: number) => data(http.get<ScenarioGraph>(`${base(id)}/graph`)),
  evaluateStored: (id: number) => data(http.post<Evaluation>(`${base(id)}/evaluate`)),
  evaluateDraft: (def: ScenarioDefinition) => data(http.post<Evaluation>('/admin/scenarios/evaluate', def)),
  publish: (id: number, changeNote?: string) =>
    data(http.post<AdminScenarioDetail>(`${base(id)}/publish`, { changeNote })),
  versions: (id: number) => data(http.get<VersionSummary[]>(`${base(id)}/versions`)),
  version: (id: number, n: number) => data(http.get<VersionDetail>(`${base(id)}/versions/${n}`)),
  restoreVersion: (id: number, n: number) =>
    data(http.post<AdminScenarioDetail>(`${base(id)}/versions/${n}/restore`)),
}
