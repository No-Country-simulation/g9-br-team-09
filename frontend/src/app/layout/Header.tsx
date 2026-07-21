import {
  Clock,
  LucideLayoutDashboard,
  Moon,
  Sun,
  TrendingUp,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useTheme } from '@/app/providers/theme'

import { Button } from '../../shared/components/Button'
import { Divider } from '../../shared/components/Divider'
import { Logo } from '../../shared/components/Logo'

export function Header() {
  const navigate = useNavigate()
  const { theme, toggleTheme } = useTheme()
  return (
    <header className="border-(--border) border-b px-5 py-6 sm:px-10">
      <nav className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Logo />
        </div>
        <div className="flex items-center sm:gap-1">
          <Button
            variant="ghost"
            icon={LucideLayoutDashboard}
            onClick={() => void navigate('/painel')}
          >
            <span className="hidden sm:inline">Painel</span>
          </Button>
          <Button
            variant="ghost"
            icon={Clock}
            onClick={() => void navigate('/historico')}
          >
            <span className="hidden sm:inline">Histórico</span>
          </Button>
          <Button
            variant="secondary"
            icon={TrendingUp}
            onClick={() => void navigate('/analise-energetica')}
          >
            <span className="hidden sm:inline">Nova análise</span>
          </Button>
          <Divider orientation="vertical" />
          <Button
            variant="ghost"
            icon={theme === 'light' ? Moon : Sun}
            onClick={toggleTheme}
          />
        </div>
      </nav>
    </header>
  )
}
