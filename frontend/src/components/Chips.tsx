import { Chip, type ChipProps } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ActionOutcome, Category, Difficulty, QualityGrade, ScenarioStatus, Severity } from '../api/types'

type Color = ChipProps['color']

const severityColor: Record<Severity, Color> = {
  INFO: 'default', LOW: 'info', MEDIUM: 'warning', HIGH: 'error', CRITICAL: 'error',
}

export function SeverityChip({ severity }: { severity: Severity }) {
  const { t } = useTranslation()
  return (
    <Chip size="small" label={t(`enums.severity.${severity}`)} color={severityColor[severity]}
      variant={severity === 'CRITICAL' ? 'filled' : 'outlined'} sx={{ minWidth: 72, height: 20, fontSize: 11 }} />
  )
}

const statusColor: Record<ScenarioStatus, Color> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'default' }

export function ScenarioStatusChip({ status }: { status: ScenarioStatus }) {
  const { t } = useTranslation()
  return (
    <Chip size="small" label={t(`enums.scenarioStatus.${status}`)} color={statusColor[status]}
      variant={status === 'PUBLISHED' ? 'filled' : 'outlined'} />
  )
}

const gradeColor: Record<QualityGrade, Color> = { EXCELLENT: 'success', GOOD: 'info', FAIR: 'warning', POOR: 'error' }

export function GradeChip({ grade }: { grade: QualityGrade }) {
  const { t } = useTranslation()
  return <Chip size="small" label={t(`enums.grade.${grade}`)} color={gradeColor[grade]} />
}

const difficultyColor: Record<Difficulty, Color> = { BEGINNER: 'success', INTERMEDIATE: 'warning', ADVANCED: 'error' }

export function DifficultyChip({ difficulty }: { difficulty: Difficulty }) {
  const { t } = useTranslation()
  return <Chip size="small" variant="outlined" label={t(`enums.difficulty.${difficulty}`)} color={difficultyColor[difficulty]} />
}

export function CategoryChip({ category }: { category: Category }) {
  const { t } = useTranslation()
  return <Chip size="small" variant="outlined" label={t(`enums.category.${category}`)} />
}

const outcomeColor: Record<ActionOutcome, Color> = { EXPECTED: 'success', NEUTRAL: 'default', HARMFUL: 'error' }

export function OutcomeChip({ outcome }: { outcome: ActionOutcome }) {
  const { t } = useTranslation()
  return <Chip size="small" label={t(`enums.outcome.${outcome}`)} color={outcomeColor[outcome]} variant="outlined" />
}

export function scoreColor(score?: number | null): 'success' | 'warning' | 'error' | 'inherit' {
  if (score == null) return 'inherit'
  if (score >= 80) return 'success'
  if (score >= 50) return 'warning'
  return 'error'
}
