// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { AnalysisFormData } from '../data/analysis'
import { useCreateAnalysis } from './useCreateAnalysis'

const createAnalysis = vi.hoisted(() => vi.fn())

vi.mock('../api/services/analysisService', () => ({ createAnalysis }))

describe('useCreateAnalysis', () => {
  it('mantém um único POST enquanto o envio está em andamento', async () => {
    let resolveRequest: (value: unknown) => void = () => undefined
    createAnalysis.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve
        }),
    )
    const { result } = renderHook(() => useCreateAnalysis())
    const data = {
      propertyType: 'CASA',
      applianceCount: '10',
      monthlyConsumption: '420',
      peakUsage: 'true',
      peakConsumptionHours: '8',
    } as AnalysisFormData

    let firstSubmission: Promise<unknown>
    let duplicateSubmission: Promise<unknown>
    await act(async () => {
      firstSubmission = result.current.submit(data)
      duplicateSubmission = result.current.submit(data)
    })

    expect(createAnalysis).toHaveBeenCalledOnce()
    expect(result.current.isSubmitting).toBe(true)
    await expect(duplicateSubmission!).resolves.toBeNull()

    await act(async () => {
      resolveRequest({ id: 1 })
      await firstSubmission!
    })

    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.error).toBeNull()
  })
})
