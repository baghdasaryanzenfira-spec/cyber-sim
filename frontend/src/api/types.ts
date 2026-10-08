// TypeScript mirror of the backend DTOs (am.cybersim.*.dto). Keep in sync with the Java records.

export type Role = 'ADMIN'
export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type Category = 'AUTHENTICATION' | 'IAM' | 'NETWORK' | 'STORAGE' | 'LOGGING' | 'INCIDENT_RESPONSE'
export type ResourceType =
  | 'VIRTUAL_MACHINE' | 'IAM_USER' | 'IAM_ROLE' | 'ACCESS_KEY' | 'STORAGE_BUCKET' | 'SECURITY_GROUP' | 'DATABASE' | 'LOAD_BALANCER'
export type EventType = 'LOG' | 'ALERT' | 'SYSTEM'
export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ActionPhase = 'INVESTIGATION' | 'RESPONSE'
export type ActionCategory = 'INSPECT' | 'IDENTIFY' | 'CONTAIN' | 'ERADICATE' | 'RECOVER' | 'HARDEN'
export type ActionOutcome = 'EXPECTED' | 'NEUTRAL' | 'HARMFUL'
export type AiSource = 'AI' | 'MOCK' | 'FALLBACK'
export type ScenarioStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type ScenarioType = 'SSH_BRUTE_FORCE' | 'COMPROMISED_CREDENTIALS' | 'PUBLIC_STORAGE_BUCKET'

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

// ---------------------------------------------------------------- scenario definition

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
  learningObjectives: string[]
  resources: ResourceDef[]
  events: EventDef[]
  actions: ActionDef[]
  hints: string[]
}

// ---------------------------------------------------------------- authoring

export interface AdminScenarioSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  category: Category
  status: ScenarioStatus
  revision: number
  publishedVersion?: number | null
  sourceScenarioId?: number | null
  actionCount: number
  eventCount: number
  maxScore: number
  updatedAt: string
}

export interface AdminScenarioDetail {
  id: number
  status: ScenarioStatus
  revision: number
  publishedVersion?: number | null
  sourceScenarioId?: number | null
  createdAt: string
  updatedAt: string
  maxScore: number
  definition: ScenarioDefinition
}

export interface GenerateRequest {
  type: ScenarioType
  title?: string
  difficulty?: Difficulty
  primaryAsset?: string
  attackerIp?: string
  region?: string
  brief?: string
  useAi?: boolean
}

export interface GenerateResponse {
  scenario: AdminScenarioDetail
  aiSource?: AiSource | null
}

export type GraphNodeType = 'START' | 'ACTION' | 'EVENT' | 'RESOURCE'
export type GraphEdgeType = 'INITIAL' | 'PREREQUISITE' | 'REVEALS' | 'TARGETS' | 'EFFECT' | 'ABOUT'

export interface GraphNode {
  id: string
  type: GraphNodeType
  key: string
  label: string
  attributes: Record<string, unknown>
}

export interface GraphEdge {
  from: string
  to: string
  type: GraphEdgeType
}

export interface ScenarioGraph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export type IssueSeverity = 'ERROR' | 'WARNING' | 'INFO'

export interface ValidationIssue {
  severity: IssueSeverity
  code: string
  path: string
  message: string
}

export interface ValidationStats {
  actions: number
  events: number
  resources: number
  evidenceEvents: number
  expectedActions: number
  harmfulActions: number
  neutralActions: number
  maxDepth: number
  categoriesCovered: number
}

export interface ValidationReport {
  valid: boolean
  errorCount: number
  warningCount: number
  stats?: ValidationStats | null
  issues: ValidationIssue[]
}

export type TestPathName = 'CORRECT' | 'DANGEROUS'

export interface TestStep {
  sequence: number
  actionKey: string
  label: string
  outcome: ActionOutcome
  points: number
  outOfOrder: boolean
  duplicate: boolean
  revealedEvents: number
  resourceEffect?: string | null
}

export interface TestCheck {
  name: string
  passed: boolean
  message: string
}

export interface TestPath {
  path: TestPathName
  description: string
  passed: boolean
  rawScore: number
  maxScore: number
  scorePercent: number
  evidenceRevealed: number
  evidenceTotal: number
  steps: TestStep[]
  checks: TestCheck[]
}

export interface TestReport {
  passed: boolean
  paths: TestPath[]
}

export type QualityGrade = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR'

export interface QualityComponent {
  name: string
  score: number
  max: number
  details: string
}

export interface QualityReport {
  score: number
  grade: QualityGrade
  meetsPublishThreshold: boolean
  components: QualityComponent[]
}

export interface Evaluation {
  validation: ValidationReport
  tests?: TestReport | null
  quality: QualityReport
  publishable: boolean
  blockers: string[]
}

export interface VersionSummary {
  id: number
  versionNumber: number
  qualityScore: number
  grade: QualityGrade
  changeNote?: string | null
  publishedBy?: string | null
  publishedAt: string
}

export interface VersionDetail {
  summary: VersionSummary
  definition: ScenarioDefinition
  quality: QualityReport
}

// ---------------------------------------------------------------- learner-module exams (ADR-13)

export interface SubmittedAction {
  actionKey: string
  note?: string | null
}

export interface VerificationStep {
  sequence: number
  actionKey: string
  label: string
  outcome: ActionOutcome
  points: number
  outOfOrder: boolean
  duplicate: boolean
}

export interface MissedExamAction {
  actionKey: string
  label: string
  category: ActionCategory
  points: number
  explanation: string
}

export interface Verification {
  scorePercent: number
  rawScore: number
  maxScore: number
  hintsUsed: number
  hintPenaltyTotal: number
  evidenceRevealed: number
  evidenceTotal: number
  steps: VerificationStep[]
  missedActions: MissedExamAction[]
}

export interface StoredReview {
  rating: number
  message: string
  strengths: string[]
  mistakes: string[]
  recommendations: string[]
  source: AiSource
}

export interface ExamRow {
  id: number
  externalId: string
  studentRef: string
  studentName?: string | null
  scenarioId: number
  scenarioTitle: string
  scenarioVersion: number
  claimedScore?: number | null
  verifiedScore: number
  scoreMatches?: boolean | null
  reviewed: boolean
  reviewRating?: number | null
  hintsUsed: number
  submittedAt: string
}

export interface ExamDetail {
  attempt: ExamRow
  submittedActions: SubmittedAction[]
  verification: Verification
  review?: StoredReview | null
  reviewedAt?: string | null
}

export interface ExamPage {
  items: ExamRow[]
  totalItems: number
  page: number
  size: number
}

