import { ExternalLink } from 'lucide-react'

export function Footer() {
  return (
    <footer className="border-border mt-12 border-t px-5 py-8 sm:px-10">
      <div className="text-muted-foreground mx-auto flex max-w-7xl flex-col gap-4 text-sm sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-foreground font-semibold">EnergiAI</p>
          <p className="mt-1">
            Análise energética desenvolvida no Hackathon Oracle Next Education
            G9.
          </p>
        </div>
        <a
          href="https://github.com/No-Country-simulation/g9-br-team-09"
          target="_blank"
          rel="noreferrer"
          className="text-foreground inline-flex w-fit items-center gap-2 rounded-lg font-medium transition-opacity hover:opacity-80 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
          aria-label="Abrir repositório oficial do EnergiAI no GitHub"
        >
          <ExternalLink size={18} aria-hidden="true" />
          Repositório oficial
        </a>
      </div>
    </footer>
  )
}
