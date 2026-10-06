import { Chip, type ChipProps } from '@mui/material'
import type { ActionOutcome, Category, Difficulty, Severity, SimulationStatus } from '../api/types'

type Color = ChipProps['color']

const severityColor: Record<Severity, Color> = {
  INFO: 'default', LOW: 'info', MEDIUM: 'warning', HIGH: 'error', CRITICAL: 'error',
}

export function SeverityChip({ severity }: { severity: Severity }) {
  return (
    <Chip size="small" label={severity} color={severityColor[severity]}
      variant={severity === 'CRITICAL' ? 'filled' : 'outlined'} sx={{ minWidth: 72, height: 20, fontSize: 11 }} />
  )
}

const statusColor: Record<SimulationStatus, Color> = {
  CREATED: 'default', RUNNING: 'info', INVESTIGATING: 'primary', RESPONDING: 'warning',
  COMPLETED: 'success', ABANDONED: 'default',
}

export function StatusChip({ status }: { status: SimulationStatus }) {
  return <Chip size="small" label={status} color={statusColor[status]} />
}

const difficultyColor: Record<Difficulty, Color> = { BEGINNER: 'success', INTERMEDIATE: 'warning', ADVANCED: 'error' }

export function DifficultyChip({ difficulty }: { difficulty: Difficulty }) {
  return <Chip size="small" variant="outlined" label={difficulty} color={difficultyColor[difficulty]} />
}

export function CategoryChip({ category }: { category: Category }) {
  return <Chip size="small" variant="outlined" label={category.replace('_', ' ')} />
}

const outcomeColor: Record<ActionOutcome, Color> = { EXPECTED: 'success', NEUTRAL: 'default', HARMFUL: 'error' }

export function OutcomeChip({ outcome }: { outcome: ActionOutcome }) {
  return <Chip size="small" label={outcome} color={outcomeColor[outcome]} variant="outlined" />
}

/** Colour for a simulated resource status such as RUNNING, ISOLATED, COMPROMISED. */
export function resourceStatusColor(status: string): Color {
  if (['COMPROMISED', 'PUBLIC', 'TERMINATED', 'DELETED'].includes(status)) return 'error'
  if (['ISOLATED', 'DISABLED', 'SESSIONS_REVOKED', 'REBOOTING'].includes(status)) return 'warning'
  if (['RESTRICTED', 'PRIVATE', 'PASSWORD_RESET', 'MFA_ENFORCED'].includes(status)) return 'success'
  return 'info'
}

export function scoreColor(score?: number | null): 'success' | 'warning' | 'error' | 'inherit' {
  if (score == null) return 'inherit'
  if (score >= 80) return 'success'
  if (score >= 50) return 'warning'
  return 'error'
}
