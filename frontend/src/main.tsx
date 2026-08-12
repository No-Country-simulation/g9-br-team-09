import './shared/styles/index.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from './app/App.tsx'
import { AuthProvider } from './app/providers/auth/AuthProvider.tsx'
import { ThemeProvider } from './app/providers/theme/ThemeProvider.tsx'
import { installAuthInterceptors } from './features/auth/session/auth-interceptors.ts'

installAuthInterceptors()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
)
