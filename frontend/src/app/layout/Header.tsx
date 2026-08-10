import {
  Clock,
  LogIn,
  LogOut,
  LucideLayoutDashboard,
  Moon,
  Sun,
  TrendingUp,
  UserPlus,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { useTheme } from '@/app/providers/theme'

import { Button } from '../../shared/components/Button'
import { Divider } from '../../shared/components/Divider'
import { Logo } from '../../shared/components/Logo'

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
    <header className="border-(--border) border-b px-5 py-6 sm:px-10">
      <nav className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Logo />
        </div>
        <div className="flex items-center sm:gap-1">
          {status === 'authenticated' ? (
            <>
              <Button
                variant="ghost"
                icon={LucideLayoutDashboard}
                aria-label="Abrir painel"
                onClick={() => void navigate('/painel')}
              >
                <span className="hidden sm:inline">Painel</span>
              </Button>
              <Button
                variant="ghost"
                icon={Clock}
                aria-label="Abrir histórico de análises"
                onClick={() => void navigate('/historico')}
              >
                <span className="hidden sm:inline">Histórico</span>
              </Button>
              <Button
                variant="secondary"
                icon={TrendingUp}
                aria-label="Iniciar nova análise energética"
                onClick={() => void navigate('/analise-energetica')}
              >
                <span className="hidden sm:inline">Nova análise</span>
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                icon={LogIn}
                aria-label="Entrar"
                onClick={() => void navigate('/login')}
              >
                <span className="hidden sm:inline">Entrar</span>
              </Button>
              <Button
                variant="ghost"
                icon={UserPlus}
                aria-label="Cadastre-se"
                onClick={() => void navigate('/cadastro')}
              >
                <span className="hidden sm:inline">Cadastre-se</span>
              </Button>
              <Button
                variant="secondary"
                icon={TrendingUp}
                aria-label="Começar análise"
                onClick={() => void navigate('/analise-energetica')}
              >
                <span className="hidden sm:inline">Começar análise</span>
              </Button>
            </>
          )}
          <Divider orientation="vertical" />
          <Button
            variant="ghost"
            icon={theme === 'light' ? Moon : Sun}
            aria-label={
              theme === 'light' ? 'Ativar tema escuro' : 'Ativar tema claro'
            }
            aria-pressed={theme === 'dark'}
            onClick={toggleTheme}
          />
          {status === 'authenticated' && (
            <Button
              variant="ghost"
              icon={LogOut}
              aria-label="Encerrar sessão"
              onClick={() => void handleLogout()}
            >
              <span className="hidden sm:inline">Sair</span>
            </Button>
          )}
        </div>
      </nav>
    </header>
  )
}
