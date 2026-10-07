// TypeScript mirror of the backend DTOs (am.cybersim.*.dto). Keep in sync with the Java records.

export type Role = 'STUDENT' | 'ADMIN'
export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type Category = 'AUTHENTICATION' | 'IAM' | 'NETWORK' | 'STORAGE' | 'LOGGING' | 'INCIDENT_RESPONSE'
export type ResourceType =
  | 'VIRTUAL_MACHINE' | 'IAM_USER' | 'IAM_ROLE' | 'ACCESS_KEY' | 'STORAGE_BUCKET' | 'SECURITY_GROUP' | 'DATABASE' | 'LOAD_BALANCER'
export type EventType = 'LOG' | 'ALERT' | 'SYSTEM'
export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ActionPhase = 'INVESTIGATION' | 'RESPONSE'
export type ActionCategory = 'INSPECT' | 'IDENTIFY' | 'CONTAIN' | 'ERADICATE' | 'RECOVER' | 'HARDEN'
export type ActionOutcome = 'EXPECTED' | 'NEUTRAL' | 'HARMFUL'
export type SimulationStatus = 'CREATED' | 'RUNNING' | 'INVESTIGATING' | 'RESPONDING' | 'COMPLETED' | 'ABANDONED'
export type AiSource = 'AI' | 'MOCK' | 'FALLBACK'

export interface ApiError {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors: { field: string; message: string }[]
}

export interface User {
  id: number
  email: string
  displayName: string
  role: Role
  enabled: boolean
  createdAt: string
  lastLoginAt?: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export interface ScenarioSummary {
  id: number
  slug: string
  title: string
  summary: string
  difficulty: Difficulty
  category: Category
  estimatedMinutes: number
}

export interface ScenarioBriefing extends ScenarioSummary {
  description: string
  learningObjectives: string[]
  resourceCount: number
}

export interface ResourceView {
  key: string
  type: ResourceType
  name: string
  region: string
  status: string
  properties: Record<string, unknown>
}

export interface EventView {
  id: number
  eventKey?: string
  occurredAt: string
  type: EventType
  source: string
  severity: Severity
  resourceKey?: string
  message: string
  details: Record<string, unknown>
  flagged: boolean
  evidence?: boolean
}

export interface ActionOption {
  key: string
  label: string
  description: string
  phase: ActionPhase
  category: ActionCategory
  targetResourceKey?: string
  performed: boolean
}

export interface PerformedAction {
  sequence: number
  actionKey: string
  label: string
  phase: ActionPhase
  category: ActionCategory
  targetResourceKey?: string
  result: 'APPLIED' | 'DUPLICATE'
  resultMessage: string
  performedAt: string
  outcome?: ActionOutcome
  points?: number
  outOfOrder?: boolean
}

export interface SimulationDetail {
  id: number
  status: SimulationStatus
  scenario: ScenarioBriefing & { hintPenalty: number }
  hintsUsed: number
  createdAt: string
  startedAt?: string
  incidentStartedAt?: string
  completedAt?: string
  resources: ResourceView[]
  events: EventView[]
  availableActions: ActionOption[]
  performedActions: PerformedAction[]
}

export interface ActionResult {
  action: PerformedAction
  revealedEvents: number
  simulation: SimulationDetail
}

export interface SimulationSummary {
  id: number
  scenarioId: number
  scenarioTitle: string
  difficulty: Difficulty
  category: Category
  status: SimulationStatus
  scorePercent?: number
  actionCount: number
  hintsUsed: number
  createdAt: string
  startedAt?: string
  completedAt?: string
}

export type ScoreItemKind = 'EXPECTED' | 'OUT_OF_ORDER' | 'NEUTRAL' | 'HARMFUL' | 'DUPLICATE' | 'HINT_PENALTY'

export interface ScoreItem {
  actionKey?: string
  label: string
  category?: ActionCategory
  kind: ScoreItemKind
  points: number
  note: string
}

export interface MissedAction {
  actionKey: string
  label: string
  category: ActionCategory
  points: number
  explanation: string
}

export interface AiFeedback {
  summary: string
  strengths: string[]
  improvements: string[]
  missedEvidence: string[]
  orderIssues: string[]
  unnecessaryActions: string[]
  nextSteps: string[]
}

export interface EvidenceSummary {
  total: number
  found: number
  falseFlags: number
  items: { eventKey: string; message: string; note: string; revealed: boolean; flagged: boolean }[]
}

export interface SimulationResult {
  simulationId: number
  scenarioId: number
  scenarioTitle: string
  status: SimulationStatus
  scorePercent: number
  rawScore: number
  maxScore: number
  hintsUsed: number
  hintPenaltyTotal: number
  breakdown: ScoreItem[]
  missedActions: MissedAction[]
  evidence: EvidenceSummary
  performedActions: PerformedAction[]
  incidentExplanation: string
  recommendedSolution: string
  feedback?: AiFeedback
  feedbackSource?: AiSource
  durationSeconds?: number
  completedAt?: string
}

export interface AssistantReply {
  type: 'HINT' | 'QUESTION'
  text: string
  source: AiSource
  hintsUsed: number
  createdAt: string
}

export interface AssistantMessage {
  id: number
  type: 'HINT' | 'QUESTION'
  question?: string
  answer: string
  source: AiSource
  createdAt: string
}

export interface CategoryStat {
  category: Category
  attempts: number
  completed: number
  averageScore?: number
  bestScore?: number
}

export interface ProgressView {
  totals: {
    attempts: number
    completed: number
    abandoned: number
    averageScore?: number
    bestScore?: number
    hintsUsed: number
    trainingMinutes: number
  }
  categories: CategoryStat[]
  scenarios: {
    scenarioId: number
    title: string
    difficulty: Difficulty
    category: Category
    attempts: number
    bestScore?: number
    lastStatus?: SimulationStatus
    activeSimulationId?: number
  }[]
  scoreHistory: { simulationId: number; scenarioTitle: string; scorePercent: number; completedAt: string }[]
  recentAttempts: SimulationSummary[]
}

export interface Recommendation {
  topic: string
  category: Category
  reason: string
}

// ---------------------------------------------------------------- admin

export interface ResourceDef {
  key: string
  type: ResourceType
  name: string
  region: string
  status: string
  properties: Record<string, unknown>
}

export interface EventDef {
  key: string
  offsetSeconds: number
  type: EventType
  source: string
  severity: Severity
  resourceKey?: string | null
  message: string
  details: Record<string, unknown>
  evidence: boolean
  evidenceNote?: string | null
  revealedByActionKey?: string | null
}

export interface ActionDef {
  key: string
  label: string
  description: string
  phase: ActionPhase
  category: ActionCategory
  targetResourceKey?: string | null
  outcome: ActionOutcome
  points: number
  prerequisiteActionKey?: string | null
  effectStatus?: string | null
  resultMessage: string
  explanation: string
}

export interface ScenarioDefinition {
  slug: string
  title: string
  summary: string
  description: string
  difficulty: Difficulty
  category: Category
  estimatedMinutes: number
  incidentExplanation: string
  recommendedSolution: string
  hintPenalty: number
  outOfOrderPenalty: number
  active: boolean
  learningObjectives: string[]
  resources: ResourceDef[]
  events: EventDef[]
  actions: ActionDef[]
  hints: string[]
}


export interface AdminScenarioSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  category: Category
  active: boolean
  version: number
  sourceScenarioId?: number
  actionCount: number
  eventCount: number
  maxScore: number
  attemptCount: number
  updatedAt: string
}

export interface AdminScenarioDetail {
  id: number
  version: number
  active: boolean
  sourceScenarioId?: number
  createdAt: string
  updatedAt: string
  maxScore: number
  definition: ScenarioDefinition
}

export interface AdminUserRow extends User {
  attempts: number
  completed: number
  averageScore?: number
}

export interface PageView<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface AttemptRow {
  id: number
  userId: number
  userEmail: string
  userName: string
  scenarioId: number
  scenarioTitle: string
  status: SimulationStatus
  scorePercent?: number
  actionCount: number
  hintsUsed: number
  createdAt: string
  completedAt?: string
}

export interface AiInteractionView {
  id: number
  type: string
  provider: string
  model?: string
  status: string
  request?: string
  response: string
  latencyMs: number
  inputTokens?: number
  outputTokens?: number
  createdAt: string
}

export interface AttemptDetail {
  attempt: AttemptRow
  simulation: SimulationDetail
  result?: SimulationResult
  aiInteractions: AiInteractionView[]
}

export interface AnalyticsOverview {
  totals: {
    users: number
    students: number
    activeScenarios: number
    simulations: number
    completed: number
    inProgress: number
    averageScore?: number
    completionRatePercent?: number
  }
  scenarios: { scenarioId: number; title: string; attempts: number; completed: number; averageScore?: number; averageDurationMinutes?: number }[]
  scoreDistribution: { range: string; count: number }[]
  attemptsPerDay: { day: string; attempts: number; completed: number }[]
  aiUsage: { type: string; status: string; count: number; averageLatencyMs?: number }[]
}

export interface MistakeStat {
  scenarioTitle: string
  actionKey: string
  label: string
  count: number
  percent?: number
}

export interface Mistakes {
  harmfulActions: MistakeStat[]
  missedActions: MistakeStat[]
  unnecessaryActions: MistakeStat[]
}
