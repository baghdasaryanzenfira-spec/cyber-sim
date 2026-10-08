import { Alert, Box, Paper, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/endpoints'
import type { GraphEdge, GraphEdgeType, GraphNode, ScenarioGraph } from '../../api/types'
import { ErrorAlert, Loading } from '../../components/Feedback'
import { useLoad } from '../../hooks/useLoad'
import { MONO } from '../../theme'

const BOX_W = 180
const BOX_H = 46
const GAP_X = 90
const GAP_Y = 18
const PAD = 24
const MAX_LABEL = 24

interface Placed {
  node: GraphNode
  x: number
  y: number
}

const truncate = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1)}…` : s)

/** Layered layout: start, then actions by prerequisite depth, events right after what reveals them, resources last. */
function computeLayers(graph: ScenarioGraph): Map<string, number> {
  const layer = new Map<string, number>([['start', 0]])
  const prereq = new Map<string, string>()
  const revealedBy = new Map<string, string>()
  for (const e of graph.edges) {
    if (e.type === 'PREREQUISITE') prereq.set(e.to, e.from)
    if (e.type === 'REVEALS') revealedBy.set(e.to, e.from)
  }
  const actions = graph.nodes.filter((n) => n.type === 'ACTION')
  actions.forEach((a) => layer.set(a.id, 1))
  // relax until stable; the iteration cap and the layer bound protect against prerequisite cycles
  for (let i = 0; i <= actions.length; i++) {
    let changed = false
    for (const a of actions) {
      const p = prereq.get(a.id)
      const next = p ? (layer.get(p) ?? 1) + 1 : 1
      if (next !== layer.get(a.id) && next <= actions.length + 1) {
        layer.set(a.id, next)
        changed = true
      }
    }
    if (!changed) break
  }
  for (const n of graph.nodes.filter((x) => x.type === 'EVENT')) {
    const by = revealedBy.get(n.id)
    layer.set(n.id, by ? (layer.get(by) ?? 1) + 1 : 1)
  }
  const last = Math.max(0, ...layer.values()) + 1
  graph.nodes.filter((n) => n.type === 'RESOURCE').forEach((n) => layer.set(n.id, last))
  return layer
}

const TYPE_ORDER = { START: 0, ACTION: 1, EVENT: 2, RESOURCE: 3 } as const

function place(graph: ScenarioGraph): { placed: Map<string, Placed>; width: number; height: number } {
  const layer = computeLayers(graph)
  const columns = new Map<number, GraphNode[]>()
  for (const n of graph.nodes) {
    const l = layer.get(n.id) ?? 1
    columns.set(l, [...(columns.get(l) ?? []), n])
  }
  const placed = new Map<string, Placed>()
  let maxRows = 0
  for (const [l, nodes] of columns) {
    nodes.sort((a, b) => TYPE_ORDER[a.type] - TYPE_ORDER[b.type])
    maxRows = Math.max(maxRows, nodes.length)
    nodes.forEach((node, row) => placed.set(node.id, { node, x: PAD + l * (BOX_W + GAP_X), y: PAD + row * (BOX_H + GAP_Y) }))
  }
  const layers = Math.max(...columns.keys()) + 1
  return {
    placed,
    width: PAD * 2 + layers * BOX_W + (layers - 1) * GAP_X,
    height: PAD * 2 + maxRows * BOX_H + Math.max(0, maxRows - 1) * GAP_Y,
  }
}

const EDGE_STYLE: Record<GraphEdgeType, { color: string; dash?: string }> = {
  INITIAL: { color: '#22d3ee' },
  PREREQUISITE: { color: '#cbd5e1' },
  REVEALS: { color: '#22d3ee' },
  TARGETS: { color: '#64748b', dash: '5 4' },
  ABOUT: { color: '#64748b', dash: '5 4' },
  EFFECT: { color: '#fbbf24', dash: '5 4' },
}

function nodeColors(node: GraphNode): { stroke: string; fill: string; width: number } {
  switch (node.type) {
    case 'START': return { stroke: '#22d3ee', fill: 'rgba(34,211,238,0.14)', width: 1.5 }
    case 'RESOURCE': return { stroke: '#a78bfa', fill: 'rgba(167,139,250,0.14)', width: 1.5 }
    case 'EVENT':
      return node.attributes.evidence === true
        ? { stroke: '#34d399', fill: 'rgba(96,165,250,0.14)', width: 3 }
        : { stroke: '#60a5fa', fill: 'rgba(96,165,250,0.14)', width: 1.5 }
    case 'ACTION': {
      const outcome = node.attributes.outcome
      if (outcome === 'EXPECTED') return { stroke: '#34d399', fill: 'rgba(52,211,153,0.12)', width: 1.5 }
      if (outcome === 'HARMFUL') return { stroke: '#f87171', fill: 'rgba(248,113,113,0.12)', width: 2 }
      return { stroke: '#94a3b8', fill: 'rgba(148,163,184,0.10)', width: 1.5 }
    }
  }
}

function EdgePath({ edge, from, to }: { edge: GraphEdge; from: Placed; to: Placed }) {
  const style = EDGE_STYLE[edge.type]
  const x1 = from.x + BOX_W
  const y1 = from.y + BOX_H / 2
  const x2 = to.x
  const y2 = to.y + BOX_H / 2
  const dx = Math.max(40, Math.abs(x2 - x1) / 2)
  return (
    <path d={`M${x1},${y1} C${x1 + dx},${y1} ${x2 - dx},${y2} ${x2},${y2}`} fill="none" stroke={style.color}
      strokeWidth={1.5} strokeDasharray={style.dash} opacity={0.85} markerEnd={`url(#arrow-${edge.type})`} />
  )
}

function NodeBox({ p, evidenceLabel }: { p: Placed; evidenceLabel: string }) {
  const c = nodeColors(p.node)
  const evidence = p.node.type === 'EVENT' && p.node.attributes.evidence === true
  return (
    <g transform={`translate(${p.x},${p.y})`}>
      <title>{p.node.label}</title>
      <rect width={BOX_W} height={BOX_H} rx={8} fill={c.fill} stroke={c.stroke} strokeWidth={c.width} />
      <text x={10} y={20} fontSize={12} fontWeight={600} fill="#e5e7eb">{truncate(p.node.label, MAX_LABEL)}</text>
      <text x={10} y={36} fontSize={10} fill="#94a3b8" fontFamily={MONO}>{truncate(p.node.key, 28)}</text>
      {evidence && (
        <g transform={`translate(${BOX_W - 62},-8)`}>
          <rect width={58} height={15} rx={7} fill="#34d399" />
          <text x={29} y={11} fontSize={9} fontWeight={700} fill="#06281d" textAnchor="middle">{evidenceLabel}</text>
        </g>
      )}
    </g>
  )
}

function Legend() {
  const { t } = useTranslation()
  const nodeItems: [string, string][] = [
    ['#22d3ee', t('graph.nodeStart')], ['#34d399', t('graph.actionExpected')], ['#94a3b8', t('graph.actionNeutral')],
    ['#f87171', t('graph.actionHarmful')], ['#60a5fa', t('graph.event')], ['#a78bfa', t('graph.resource')],
  ]
  const edgeTypes: GraphEdgeType[] = ['PREREQUISITE', 'REVEALS', 'TARGETS', 'EFFECT']
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1.5 }}>
      {nodeItems.map(([color, label]) => (
        <Box key={label} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Box sx={{ width: 14, height: 14, borderRadius: 0.5, border: `2px solid ${color}` }} />
          <Typography variant="caption" color="text.secondary">{label}</Typography>
        </Box>
      ))}
      {edgeTypes.map((type) => (
        <Box key={type} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <svg width={28} height={10}>
            <line x1={0} y1={5} x2={28} y2={5} stroke={EDGE_STYLE[type].color} strokeWidth={2} strokeDasharray={EDGE_STYLE[type].dash} />
          </svg>
          <Typography variant="caption" color="text.secondary">{t(`enums.edgeType.${type}`)}</Typography>
        </Box>
      ))}
    </Box>
  )
}

/** Dependency graph of the SAVED scenario, drawn as layered SVG (no graph library needed). */
export function GraphPanel({ id, revision, dirty }: { id: number; revision: number; dirty: boolean }) {
  const { t } = useTranslation()
  const graph = useLoad(() => adminApi.graph(id), [id, revision])

  if (graph.loading && !graph.data) return <Loading />
  if (!graph.data) return <ErrorAlert message={graph.error} />
  const { placed, width, height } = place(graph.data)
  const edges = graph.data.edges.filter((e) => placed.has(e.from) && placed.has(e.to))

  return (
    <>
      {dirty && <Alert severity="info" sx={{ mb: 2 }}>{t('graph.unsavedNote')}</Alert>}
      <Paper sx={{ p: 2 }}>
        <Typography color="text.secondary" sx={{ mb: 1 }}>{t('graph.hint')}</Typography>
        <Box sx={{ overflowX: 'auto' }}>
          <svg width={width} height={height} role="img" aria-label={t('graph.title')}>
            <defs>
              {(Object.keys(EDGE_STYLE) as GraphEdgeType[]).map((type) => (
                <marker key={type} id={`arrow-${type}`} viewBox="0 0 10 10" refX={9} refY={5} markerWidth={7} markerHeight={7} orient="auto">
                  <path d="M0,0 L10,5 L0,10 z" fill={EDGE_STYLE[type].color} />
                </marker>
              ))}
            </defs>
            {edges.map((e, i) => <EdgePath key={i} edge={e} from={placed.get(e.from)!} to={placed.get(e.to)!} />)}
            {[...placed.values()].map((p) => <NodeBox key={p.node.id} p={p} evidenceLabel={t('graph.evidence')} />)}
          </svg>
        </Box>
        <Legend />
      </Paper>
    </>
  )
}
