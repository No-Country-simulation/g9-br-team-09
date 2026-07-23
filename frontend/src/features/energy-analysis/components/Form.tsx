import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { type AnalysisFormData, analysisFormSteps } from '../data/analysis'
import { useCreateAnalysis } from '../hooks/useCreateAnalysis'
import { FormStep } from './FormStep'

export function AnalysisForm() {
  const navigate = useNavigate()
  const { submit, isSubmitting, error } = useCreateAnalysis()
  const [currentStepIndex, setCurrentStepIndex] = useState(0)
  const [formData, setFormData] = useState<AnalysisFormData>(
    {} as AnalysisFormData,
  )
  const totalSteps = analysisFormSteps.length
  const currentStep = analysisFormSteps[currentStepIndex]

  const handleNextStep = async (value: string) => {
    const updatedFormData = { ...formData, [currentStep.id]: value }
    setFormData(updatedFormData)

    if (currentStepIndex + 1 > totalSteps - 1) {
      const result = await submit(updatedFormData)
      if (result) {
        void navigate('/resultado')
      }
      return
    }
    setCurrentStepIndex((prev) => prev + 1)
  }
  const handlePreviousStep = () => {
    if (currentStepIndex === 0) return
    setCurrentStepIndex((prev) => prev - 1)
  }
  return (
    <>
      <FormStep
        key={currentStep.id}
        {...currentStep}
        defaultValue={formData[currentStep.id]}
        currentStep={currentStepIndex + 1}
        totalSteps={totalSteps}
        onBack={handlePreviousStep}
        onNext={handleNextStep}
        hideBackButton={currentStepIndex === 0}
        isSubmitting={isSubmitting}
      />
      {error && (
        <p
          className="text-destructive mt-3 text-center text-sm text-red-500"
          role="alert"
        >
          {error}
        </p>
      )}
    </>
  )
}
