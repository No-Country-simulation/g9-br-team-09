import type { ReactNode } from 'react'

import { Logo } from '@/shared/components/Logo'

interface AuthLayoutProps {
  children: ReactNode
}

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="bg-linear-to-b flex min-h-screen flex-col from-slate-950 to-emerald-700 md:flex-row">
      <aside className="flex-col justify-center gap-6  px-16 py-12 text-slate-50 md:flex md:w-1/2 xl:px-48">
        <Logo
          className="justify-center lg:justify-start"
          textClassName="text-white"
          imgWidth={48}
          imgHeight={60}
          textSizeClassName="text-[22px] sm:text-[28px]"
        />
        <h1 className="mt-6 text-wrap text-center text-3xl font-bold md:mt-0 md:text-4xl lg:text-left">
          Descubra a eficiência energética do seu imóvel
        </h1>
        <p className="mt-5 text-wrap text-center text-sm text-slate-200 sm:text-base md:mt-0 lg:text-left">
          O EnergiAI analisa o perfil energético do seu imóvel e traz
          recomendações práticas para reduzir custos.
        </p>
      </aside>

      <main className="bg-background shadow-card flex flex-1 items-center justify-center rounded-t-[35px] p-6 md:rounded-l-[60px] md:rounded-tr-none">
        <div className="w-full max-w-md">{children}</div>
      </main>
    </div>
  )
}
