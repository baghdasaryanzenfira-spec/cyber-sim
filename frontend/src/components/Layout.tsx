import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh'
import DashboardIcon from '@mui/icons-material/Dashboard'
import ListAltIcon from '@mui/icons-material/ListAlt'
import LogoutIcon from '@mui/icons-material/Logout'
import ShieldIcon from '@mui/icons-material/Shield'
import {
  AppBar, Avatar, Box, Chip, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Tooltip,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { LanguageSwitcher } from './LanguageSwitcher'

const DRAWER_WIDTH = 236

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
}

const adminNav = (t: (key: string) => string): NavItem[] => [
  { to: '/admin', label: t('nav.dashboard'), icon: <DashboardIcon />, end: true },
  { to: '/admin/scenarios', label: t('nav.scenarios'), icon: <ListAltIcon />, end: true },
  { to: '/admin/generate', label: t('nav.generate'), icon: <AutoFixHighIcon /> },
]

function NavList({ items }: { items: NavItem[] }) {
  return (
    <>
      {items.map((item) => (
        <ListItemButton key={item.to} component={NavLink} to={item.to} end={item.end}
          sx={{ borderRadius: 2, mx: 1, '&.active': { bgcolor: 'rgba(34,211,238,0.12)', color: 'primary.main' } }}>
          <ListItemIcon sx={{ minWidth: 36, color: 'inherit' }}>{item.icon}</ListItemIcon>
          <ListItemText primary={item.label} />
        </ListItemButton>
      ))}
    </>
  )
}

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" elevation={0} sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <ShieldIcon color="primary" sx={{ mr: 1 }} />
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            CyberSim <Typography component="span" color="text.secondary" sx={{ fontSize: 14, ml: 1 }}>
              {t('common.tagline')}
            </Typography>
          </Typography>
          <LanguageSwitcher sx={{ mr: 2 }} />
          {user && (
            <>
              <Chip size="small" label={user.role} color="secondary"
                variant="outlined" sx={{ mr: 2 }} />
              <Avatar sx={{ width: 30, height: 30, mr: 1, bgcolor: 'primary.dark', fontSize: 14 }}>
                {user.displayName.charAt(0).toUpperCase()}
              </Avatar>
              <Typography sx={{ mr: 1 }}>{user.displayName}</Typography>
              <Tooltip title={t('common.logout')}>
                <IconButton color="inherit" onClick={() => { logout(); navigate('/login') }}>
                  <LogoutIcon />
                </IconButton>
              </Tooltip>
            </>
          )}
        </Toolbar>
      </AppBar>
      <Drawer variant="permanent" sx={{
        width: DRAWER_WIDTH, flexShrink: 0,
        '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', bgcolor: '#0d1424' },
      }}>
        <Toolbar />
        <List>
          <NavList items={adminNav(t)} />
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', mb: 3, gap: 2, flexWrap: 'wrap' }}>
      <Box>
        <Typography variant="h4">{title}</Typography>
        {subtitle && <Typography color="text.secondary" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 1 }}>{actions}</Box>}
    </Box>
  )
}
