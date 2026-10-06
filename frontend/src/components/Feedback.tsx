import { Alert, Box, CircularProgress } from '@mui/material'

export function Loading() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
      <CircularProgress />
    </Box>
  )
}

export function ErrorAlert({ message }: { message: string | null }) {
  if (!message) return null
  return <Alert severity="error" sx={{ mb: 2 }}>{message}</Alert>
}
