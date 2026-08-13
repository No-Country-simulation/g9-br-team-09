import {
  ArrowRight,
  BrainCircuit,
  ChartNoAxesCombined,
  Clock3,
  House,
  Lightbulb,
  ListChecks,
  MonitorSmartphone,
  Zap,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { Button } from '@/shared/components/Button'

const analysisInputs = [
  { icon: Zap, label: 'Consumo mensal em kWh' },
  { icon: Clock3, label: 'Uso em horário de pico' },
  { icon: MonitorSmartphone, label: 'Quantidade de equipamentos' },
  { icon: House, label: 'Tipo de imóvel' },
  { icon: Clock3, label: 'Horas de alto consumo' },
]

const analysisSteps = [
  {
    icon: ListChecks,
    title: '1. Informe os dados',
    description: 'Conte como a energia é usada no seu imóvel.',
  },
  {
    icon: BrainCircuit,
    title: '2. Perfil classificado',
    description: 'O sistema processa as informações para a classificação.',
  },
  {
    icon: Lightbulb,
    title: '3. Consulte o resultado',
    description: 'Veja custo estimado e recomendações para refletir sobre o uso.',
  },
]

export function EnergyAnalysisHomePage() {
  const navigate = useNavigate()

  return (
    <main className="mx-auto max-w-5xl px-5 py-10 sm:py-16">
      <section className="mx-auto max-w-3xl text-center">
        <p className="text-primary text-sm font-semibold uppercase tracking-widest">
          O que é o EnergiAI
        </p>
        <h1 className="text-foreground mt-3 text-3xl font-bold tracking-tight sm:text-5xl">
          Entenda o perfil de consumo do seu imóvel
        </h1>
        <p className="text-muted-foreground mx-auto mt-5 max-w-2xl text-base leading-relaxed sm:text-lg">
          O EnergiAI analisa informações de consumo energético para classificar
          o perfil do imóvel e apresentar recomendações de forma simples e
          prática.
        </p>
        <div className="mt-8 flex justify-center">
          <Button
            variant="primary"
            onClick={() => void navigate('/analise-energetica')}
          >
            Começar análise
          </Button>
        </div>
      </section>

      <section className="mt-12" aria-labelledby="inputs-title">
        <div className="max-w-2xl">
          <p className="text-primary text-sm font-semibold uppercase tracking-widest">
            Dados utilizados
          </p>
          <h2 id="inputs-title" className="mt-2 text-2xl font-bold">
            O que é considerado na análise
          </h2>
        </div>
        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {analysisInputs.map(({ icon: Icon, label }) => (
            <article
              key={label}
              className="bg-card border-border rounded-2xl border p-4 shadow-card"
            >
              <Icon className="text-primary" size={20} aria-hidden="true" />
              <h3 className="mt-3 text-sm font-semibold">{label}</h3>
            </article>
          ))}
        </div>
      </section>

      <section className="mt-12" aria-labelledby="how-it-works-title">
        <div className="max-w-2xl">
          <p className="text-primary text-sm font-semibold uppercase tracking-widest">
            Como funciona
          </p>
          <h2 id="how-it-works-title" className="mt-2 text-2xl font-bold">
            Da informação ao resultado
          </h2>
        </div>
        <div className="mt-5 grid gap-4 sm:grid-cols-3">
          {analysisSteps.map(({ icon: Icon, title, description }) => (
            <article
              key={title}
              className="bg-card border-border rounded-2xl border p-5"
            >
              <Icon className="text-primary" size={22} aria-hidden="true" />
              <h3 className="mt-4 font-semibold">{title}</h3>
              <p className="text-muted-foreground mt-2 text-sm leading-relaxed">
                {description}
              </p>
            </article>
          ))}
        </div>
      </section>

      <section
        className="bg-card border-border mt-12 grid gap-6 rounded-2xl border p-6 sm:grid-cols-[1fr_auto] sm:items-center sm:p-8"
        aria-labelledby="result-title"
      >
        <div>
          <p className="text-primary text-sm font-semibold uppercase tracking-widest">
            Resultado da análise
          </p>
          <h2 id="result-title" className="mt-2 text-2xl font-bold">
            Informações para entender o perfil energético
          </h2>
          <p className="text-muted-foreground mt-3 leading-relaxed">
            O resultado reúne categoria energética, probabilidade, custo mensal
            estimado e recomendações. O índice de ineficiência vai de 0 a 100:
            0 representa menor ineficiência e 100, maior ineficiência.
          </p>
        </div>
        <ChartNoAxesCombined
          className="text-primary"
          size={36}
          aria-hidden="true"
        />
      </section>

      <section className="mt-12 grid gap-4 lg:grid-cols-2">
        <article className="border-border rounded-2xl border p-6">
          <p className="text-primary text-sm font-semibold uppercase tracking-widest">
            Modelo preditivo
          </p>
          <h2 className="mt-2 text-xl font-bold">
            Classificação com Machine Learning
          </h2>
          <p className="text-muted-foreground mt-3 text-sm leading-relaxed">
            O EnergiAI utiliza um modelo de Machine Learning para apoiar a
            classificação do perfil energético a partir dos dados informados.
          </p>
        </article>
        <article className="border-border rounded-2xl border p-6">
          <p className="text-primary text-sm font-semibold uppercase tracking-widest">
            Arquitetura
          </p>
          <h2 className="mt-2 text-xl font-bold">Fluxo da aplicação</h2>
          <div className="text-muted-foreground mt-4 flex flex-wrap items-center gap-2 text-sm font-medium">
            {[
              'Frontend',
              'Backend Spring Boot',
              'FastAPI',
              'Modelo preditivo',
              'Backend, persistência e histórico',
            ].map((item, index, items) => (
              <span key={item} className="contents">
                <span className="bg-muted-primary text-foreground rounded-full px-3 py-1.5">
                  {item}
                </span>
                {index < items.length - 1 && (
                  <ArrowRight className="text-primary" size={16} aria-hidden="true" />
                )}
              </span>
            ))}
          </div>
        </article>
      </section>

      <section className="border-border mt-12 border-t pt-8 text-center">
        <h2 className="text-lg font-semibold">Projeto acadêmico</h2>
        <p className="text-muted-foreground mx-auto mt-2 max-w-3xl text-sm leading-relaxed">
          O EnergiAI foi desenvolvido no Hackathon Oracle Next Education G9 e
          não substitui uma avaliação energética profissional certificada.
        </p>
      </section>
    </main>
  )
}
