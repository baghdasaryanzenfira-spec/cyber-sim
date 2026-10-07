import '@fontsource/inter/400.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/jetbrains-mono/400.css'
// Inter has no Armenian glyphs; these cover the Armenian UI without touching Latin text.
import '@fontsource/noto-sans-armenian/armenian-400.css'
import '@fontsource/noto-sans-armenian/armenian-600.css'
import '@fontsource/noto-sans-armenian/armenian-700.css'
import { CssBaseline, ThemeProvider } from '@mui/material'
import './i18n'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { theme } from './theme'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
)
