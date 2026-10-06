import ReplayIcon from '@mui/icons-material/Replay'
import { Button } from '@mui/material'
import { useNavigate, useParams } from 'react-router'
import { simulationApi } from '../api/endpoints'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ResultView } from '../components/ResultView'
import { useLoad } from '../hooks/useLoad'

export function ResultPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const result = useLoad(() => simulationApi.result(Number(id)), [id])

  if (result.loading) return <Loading />
  if (!result.data) return <ErrorAlert message={result.error} />
  const r = result.data

  return (
    <>
      <PageHeader title="Simulation result" subtitle={r.scenarioTitle} actions={
        <>
          <Button onClick={() => navigate('/history')}>Progress &amp; history</Button>
          <Button variant="contained" startIcon={<ReplayIcon />} onClick={() => navigate(`/scenarios/${r.scenarioId}`)}>
            Try again
          </Button>
        </>
      } />
      <ResultView result={r} />
    </>
  )
}
