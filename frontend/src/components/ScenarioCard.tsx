import AccessTimeIcon from '@mui/icons-material/AccessTime'
import { Box, Button, Card, CardActions, CardContent, Stack, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router'
import type { ScenarioSummary } from '../api/types'
import { CategoryChip, DifficultyChip } from './Chips'

export function ScenarioCard({ scenario, bestScore }: { scenario: ScenarioSummary; bestScore?: number | null }) {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', border: '1px solid rgba(148,163,184,0.12)' }}>
      <CardContent sx={{ flexGrow: 1 }}>
        <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
          <DifficultyChip difficulty={scenario.difficulty} />
          <CategoryChip category={scenario.category} />
        </Stack>
        <Typography variant="h6" gutterBottom>{scenario.title}</Typography>
        <Typography variant="body2" color="text.secondary">{scenario.summary}</Typography>
      </CardContent>
      <CardActions sx={{ px: 2, pb: 2, justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
          <AccessTimeIcon fontSize="small" />
          <Typography variant="body2">{scenario.estimatedMinutes} min</Typography>
          {bestScore != null && (
            <Typography variant="body2" sx={{ ml: 1.5, color: 'success.main' }}>Best {bestScore}/100</Typography>
          )}
        </Box>
        <Button component={RouterLink} to={`/scenarios/${scenario.id}`} variant="outlined" size="small">
          Open briefing
        </Button>
      </CardActions>
    </Card>
  )
}
