import { AnalysisForm } from '../components/Form'
import { AnalysisHero } from '../components/Hero'

export function AnalysisFormPage() {
  return (
    <main className="mx-auto max-w-xl px-3 py-10 sm:py-20 lg:max-w-fit">
      <AnalysisHero />
      <section className="mx-auto max-w-xl">
        <AnalysisForm />
      </section>
    </main>
  )
}
