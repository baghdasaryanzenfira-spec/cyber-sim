import ReplayIcon from '@mui/icons-material/Replay'
import { Button } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { simulationApi } from '../api/endpoints'
import { ErrorAlert, Loading } from '../components/Feedback'
import { PageHeader } from '../components/Layout'
import { ResultView } from '../components/ResultView'
import { useLoad } from '../hooks/useLoad'

export function ResultPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const result = useLoad(() => simulationApi.result(Number(id)), [id])

  if (result.loading) return <Loading />
  if (!result.data) return <ErrorAlert message={result.error} />
  const r = result.data

  return (
    <>
      <PageHeader title={t('result.title')} subtitle={r.scenarioTitle} actions={
        <>
          <Button onClick={() => navigate('/history')}>{t('history.title')}</Button>
          <Button variant="contained" startIcon={<ReplayIcon />} onClick={() => navigate(`/scenarios/${r.scenarioId}`)}>
            {t('result.tryAgain')}
          </Button>
        </>
      } />
      <ResultView result={r} />
    </>
  )
}
