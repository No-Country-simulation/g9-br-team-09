import { useNavigate } from 'react-router-dom'

import { Button } from '@/shared/components/Button'

export function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <main className="mx-auto max-w-xl px-5 py-10 sm:py-20 lg:max-w-3xl">
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
        <p className="text-primary text-6xl font-semibold">404</p>
        <h1 className="text-foreground text-2xl font-semibold">
          Página não encontrada
        </h1>
        <p className="text-muted-foreground">
          O endereço acessado não existe ou foi movido.
        </p>
        <Button variant="primary" onClick={() => void navigate('/')}>
          Voltar ao início
        </Button>
      </div>
    </main>
  )
}
