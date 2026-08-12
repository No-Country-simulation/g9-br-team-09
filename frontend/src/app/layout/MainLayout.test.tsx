// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

vi.mock('./Header', () => ({
  Header: () => <header>Header</header>,
}))

vi.mock('./Footer', () => ({
  Footer: () => <footer>Footer</footer>,
}))

import { MainLayout } from './MainLayout'

describe('MainLayout', () => {
  it('mantém o Footer depois da área de conteúdo', () => {
    render(
      <MemoryRouter>
        <Routes>
          <Route element={<MainLayout />}>
            <Route index element={<p>Conteúdo da página</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    const footer = screen.getByText('Footer')
    const layout = footer.parentElement

    expect(layout).toHaveTextContent('Header')
    expect(layout).toHaveTextContent('Conteúdo da página')
    expect(layout?.lastElementChild).toBe(footer)
  })
})
