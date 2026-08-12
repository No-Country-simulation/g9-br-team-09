import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'

import { Input, type InputProps } from '@/shared/components/Input'

type PasswordInputProps = Omit<InputProps, 'type' | 'suffix' | 'endAdornment'>

export function PasswordInput(props: PasswordInputProps) {
  const [isVisible, setIsVisible] = useState(false)

  return (
    <Input
      type={isVisible ? 'text' : 'password'}
      endAdornment={
        <button
          type="button"
          onClick={() => setIsVisible((prev) => !prev)}
          className="text-muted-foreground ml-3 cursor-pointer rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          aria-label={isVisible ? 'Ocultar senha' : 'Mostrar senha'}
          aria-pressed={isVisible}
        >
          {isVisible ? (
            <EyeOff size={18} aria-hidden="true" />
          ) : (
            <Eye size={18} aria-hidden="true" />
          )}
        </button>
      }
      {...props}
    />
  )
}
