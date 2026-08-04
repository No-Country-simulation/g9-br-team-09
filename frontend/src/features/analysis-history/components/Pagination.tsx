import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/shared/components/Button'

interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <nav
      aria-label="Paginação"
      className="mt-8 flex items-center justify-center gap-3"
    >
      <Button
        variant="secondary"
        className="disabled:opacity-40"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
        aria-label="Página anterior"
      >
        <ChevronLeft size={12} />
      </Button>

      <span className="text-muted-foreground text-sm" aria-live="polite">
        Página {currentPage + 1} de {totalPages}
      </span>

      <Button
        variant="secondary"
        className="disabled:opacity-40"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages - 1}
        aria-label="Próxima página"
      >
        <ChevronRight size={12} />
      </Button>
    </nav>
  )
}
