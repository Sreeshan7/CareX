import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SearchX } from 'lucide-react'
import { Button, Card, EmptyState } from '../../components/ui/primitives'

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <Card>
      <EmptyState title={t('errors.NOT_FOUND')} icon={<SearchX className="h-6 w-6" />}>
        <Link to="/"><Button variant="outline" size="sm" className="mt-2">{t('nav.dashboard')}</Button></Link>
      </EmptyState>
    </Card>
  )
}
