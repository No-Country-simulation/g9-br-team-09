import {
  Clock,
  House,
  LogIn,
  LogOut,
  type LucideIcon,
  LucideLayoutDashboard,
  Moon,
  Sun,
  TrendingUp,
  UserPlus,
} from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { useTheme } from '@/app/providers/theme'

import { Button } from '../../shared/components/Button'
import { Divider } from '../../shared/components/Divider'
import { Logo } from '../../shared/components/Logo'

interface HeaderActionProps {
  icon: LucideIcon
  label: string
  ariaLabel: string
  onClick: () => void
  path: string
  activePaths?: string[]
  destructive?: boolean
}

function HeaderAction({
  icon,
  label,
  ariaLabel,
  onClick,
  path,
  activePaths = [],
  destructive = false,
}: HeaderActionProps) {
  const { pathname } = useLocation()
  const isActive = [path, ...activePaths].some((activePath) =>
    activePath.endsWith('/*')
      ? pathname.startsWith(activePath.slice(0, -1))
      : pathname === activePath,
  )

  return (
    <Button
      variant={destructive ? 'destructive' : 'navigation'}
      icon={icon}
      aria-label={ariaLabel}
      aria-current={isActive ? 'page' : undefined}
      onClick={onClick}
      className={`group shrink-0 overflow-hidden border px-1 sm:px-3 ${
        destructive
          ? 'border-transparent hover:opacity-100 hover:drop-shadow-[0_0_8px_var(--inefficient-badge-border)]'
          : isActive
            ? 'border-primary bg-muted-primary sm:bg-muted-primary'
            : 'border-transparent sm:hover:bg-muted-primary/50'
      }`}
    >
      <span
        className={`max-w-0 overflow-hidden whitespace-nowrap opacity-0 transition-[max-width,opacity] duration-200 motion-reduce:transition-none sm:group-hover:max-w-40 sm:group-hover:opacity-100 sm:group-focus-visible:max-w-40 sm:group-focus-visible:opacity-100 ${
          isActive ? 'sm:max-w-40 sm:opacity-100' : ''
        }`}
      >
        {label}
      </span>
    </Button>
  )
}

export function Header() {
  const navigate = useNavigate()
  const { theme, toggleTheme } = useTheme()
  const { status, logout } = useAuth()

  const handleLogout = async () => {
    try {
      await logout()
    } catch {
      // The provider clears the local session even if the request fails.
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <header className="border-(--border) border-b px-3 py-4 sm:px-10 sm:py-6">
      <nav className="mx-auto flex max-w-7xl items-center justify-between gap-1 sm:gap-2">
        <Link
          to="/"
          aria-label="Ir para a página inicial do EnergiAI"
          className="shrink-0 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
        >
          <Logo />
        </Link>
        <div className="flex min-w-0 items-center justify-end gap-0.5 sm:gap-1">
          {status === 'authenticated' ? (
            <>
              <HeaderAction
                icon={House}
                label="Início"
                ariaLabel="Ir para o início"
                path="/"
                onClick={() => void navigate('/')}
              />
              <HeaderAction
                icon={LucideLayoutDashboard}
                label="Painel"
                ariaLabel="Abrir painel"
                path="/painel"
                onClick={() => void navigate('/painel')}
              />
              <HeaderAction
                icon={Clock}
                label="Histórico"
                ariaLabel="Abrir histórico de análises"
                path="/historico"
                activePaths={['/detalhes/*']}
                onClick={() => void navigate('/historico')}
              />
              <HeaderAction
                icon={TrendingUp}
                label="Nova análise"
                ariaLabel="Iniciar nova análise energética"
                path="/analise-energetica"
                activePaths={['/resultado']}
                onClick={() => void navigate('/analise-energetica')}
              />
            </>
          ) : (
            <>
              <HeaderAction
                icon={House}
                label="Início"
                ariaLabel="Ir para o início"
                path="/"
                onClick={() => void navigate('/')}
              />
              <HeaderAction
                icon={LogIn}
                label="Entrar"
                ariaLabel="Entrar"
                path="/login"
                onClick={() => void navigate('/login')}
              />
              <HeaderAction
                icon={UserPlus}
                label="Cadastre-se"
                ariaLabel="Cadastre-se"
                path="/cadastro"
                onClick={() => void navigate('/cadastro')}
              />
            </>
          )}
          <Divider
            orientation="vertical"
            spacing={8}
            className="hidden sm:block"
          />
          <Button
            variant="ghost"
            icon={theme === 'light' ? Moon : Sun}
            aria-label={
              theme === 'light' ? 'Ativar tema escuro' : 'Ativar tema claro'
            }
            aria-pressed={theme === 'dark'}
            onClick={toggleTheme}
            className="hover:text-primary hover:opacity-100 hover:drop-shadow-[0_0_8px_var(--primary)]"
          />
          {status === 'authenticated' && (
            <HeaderAction
              icon={LogOut}
              label="Sair"
              ariaLabel="Encerrar sessão"
              path="/sair"
              destructive
              onClick={() => void handleLogout()}
            />
          )}
        </div>
      </nav>
    </header>
  )
}
