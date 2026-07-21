import { useNavigate } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { Logo } from '@/shared/components/Logo'

export function EnergyAnalysisHomePage() {
  const navigate = useNavigate()
  return (
    <main className="mx-auto max-w-xl px-5 py-10 sm:py-20 lg:max-w-3xl">
      <div className="flex-col text-center sm:flex sm:flex-row sm:gap-12 sm:text-left">
        <Logo orientation="vertical" className="mb-8 sm:mb-0" />
        <div className="flex flex-col justify-around gap-4">
          <div className="flex flex-col gap-3">
            <h1 className="text-foreground text-2xl font-bold sm:text-4xl">
              Descubra a eficiência energética do seu imóvel
            </h1>
            <p className="text-muted-foreground text-sm sm:text-base">
              O EnergiAI analisa o perfil energético do seu imóvel e traz
              recomendações práticas para reduzir custos.
            </p>
          </div>
          <div className="flex w-full justify-center sm:block">
            <Button
              variant="primary"
              onClick={() => void navigate('/analise-energetica')}
            >
              Começar análise
            </Button>
          </div>
        </div>
      </div>
    </main>
  )
}
