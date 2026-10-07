import { createTheme } from '@mui/material/styles'

export const MONO = '"JetBrains Mono", "Consolas", monospace'

/** Inter covers Latin; Noto Sans Armenian is picked up per-glyph for Armenian text. */
const SANS = '"Inter", "Noto Sans Armenian", "Segoe UI", system-ui, sans-serif'

/** Dark "security operations centre" theme. */
export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#22d3ee' },
    secondary: { main: '#a78bfa' },
    success: { main: '#34d399' },
    warning: { main: '#fbbf24' },
    error: { main: '#f87171' },
    info: { main: '#60a5fa' },
    background: { default: '#0a0f1c', paper: '#111827' },
    divider: 'rgba(148, 163, 184, 0.16)',
    text: { primary: '#e5e7eb', secondary: '#94a3b8' },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: SANS,
    h4: { fontWeight: 700, letterSpacing: '-0.02em' },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
    overline: { letterSpacing: '0.12em', fontWeight: 600 },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none', border: '1px solid rgba(148, 163, 184, 0.12)' },
      },
    },
    MuiCard: { defaultProps: { elevation: 0 } },
    MuiAppBar: {
      styleOverrides: { root: { backgroundColor: '#0d1424', borderBottom: '1px solid rgba(148,163,184,0.12)' } },
    },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiTableCell: { styleOverrides: { root: { borderColor: 'rgba(148, 163, 184, 0.12)' } } },
  },
})
