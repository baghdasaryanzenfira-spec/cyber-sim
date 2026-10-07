import CloudIcon from '@mui/icons-material/Cloud'
import DnsIcon from '@mui/icons-material/Dns'
import KeyIcon from '@mui/icons-material/Key'
import LockIcon from '@mui/icons-material/Lock'
import PersonIcon from '@mui/icons-material/Person'
import StorageIcon from '@mui/icons-material/Storage'
import { Box, Chip, Paper, Stack, Tooltip, Typography } from '@mui/material'
import type { ReactElement } from 'react'
import { useTranslation } from 'react-i18next'
import type { ResourceType, ResourceView } from '../../api/types'
import { resourceStatusColor } from '../../components/Chips'
import { MONO } from '../../theme'

const icons: Record<ResourceType, ReactElement> = {
  VIRTUAL_MACHINE: <DnsIcon />, IAM_USER: <PersonIcon />, IAM_ROLE: <PersonIcon />, ACCESS_KEY: <KeyIcon />,
  STORAGE_BUCKET: <StorageIcon />, SECURITY_GROUP: <LockIcon />, DATABASE: <StorageIcon />, LOAD_BALANCER: <CloudIcon />,
}

function formatValue(value: unknown): string {
  return Array.isArray(value) ? value.join(', ') : String(value)
}

export function ResourcesPanel({ resources }: { resources: ResourceView[] }) {
  const { t } = useTranslation()
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="overline" color="primary">{t('sim.resources')}</Typography>
      <Stack spacing={1.25} sx={{ mt: 1 }}>
        {resources.map((r) => (
          <Box key={r.key} sx={{ p: 1.25, borderRadius: 2, border: '1px solid', borderColor: 'divider', bgcolor: 'rgba(15,23,42,0.6)' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Box sx={{ color: 'text.secondary', display: 'flex' }}>{icons[r.type]}</Box>
              <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap title={r.name}>{r.name}</Typography>
                <Typography variant="caption" color="text.secondary">{r.type.replace('_', ' ')} · {r.region}</Typography>
              </Box>
              <Chip size="small" label={r.status} color={resourceStatusColor(r.status)} sx={{ flexShrink: 0 }} />
            </Stack>
            {Object.keys(r.properties).length > 0 && (
              <Box sx={{ mt: 0.75, fontFamily: MONO, fontSize: 11, color: 'text.secondary' }}>
                {Object.entries(r.properties).slice(0, 4).map(([k, v]) => (
                  <Tooltip key={k} title={formatValue(v)} placement="left">
                    <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {k}: <span style={{ color: '#cbd5e1' }}>{formatValue(v)}</span>
                    </div>
                  </Tooltip>
                ))}
              </Box>
            )}
          </Box>
        ))}
      </Stack>
    </Paper>
  )
}
