import iconeLogo from '@/shared/assets/images/energiai-icone.png'

interface LogoProps {
  orientation?: 'horizontal' | 'vertical'
  className?: string
}

export function Logo({ orientation = 'horizontal', className }: LogoProps) {
  const isVertical = orientation === 'vertical'
  const imgWidth = isVertical ? 94 : 32
  const imgHeight = isVertical ? 119 : 40
  const imageClassName = isVertical
    ? 'w-[94px] h-[119px] sm:w-[150px] sm:h-[190px]'
    : 'w-[32px] h-[40px]'
  const textClassName = isVertical
    ? 'text-[32px] sm:text-[48px]'
    : 'text-[16px] sm:text-[20px]'

  return (
    <div
      className={`flex ${isVertical ? 'flex-col items-center gap-4' : 'items-center gap-2'} ${className}`}
    >
      <img
        src={iconeLogo}
        alt="EnergiAI logo"
        width={imgWidth}
        height={imgHeight}
        className={imageClassName}
      />
      <span className={`${textClassName} text-foreground`}>
        Energi<span className="font-bold">AI</span>
      </span>
    </div>
  )
}
