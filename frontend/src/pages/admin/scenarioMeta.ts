import type { AdminScenarioDetail, ScenarioStatus } from '../../api/types'

/** Server-side facts about the scenario being edited (everything that is not part of the definition). */
export interface ScenarioMeta {
  status: ScenarioStatus
  revision: number
  publishedVersion: number | null
  maxScore: number
}

export const toMeta = (d: AdminScenarioDetail): ScenarioMeta => ({
  status: d.status, revision: d.revision, publishedVersion: d.publishedVersion ?? null, maxScore: d.maxScore,
})
