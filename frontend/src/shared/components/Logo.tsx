import iconeLogo from '@/shared/assets/images/energiai-icone.png'

interface LogoProps {
  orientation?: 'horizontal' | 'vertical'
  className?: string
  textClassName?: string
  imgWidth?: number
  imgHeight?: number
  textSizeClassName?: string
}

export function Logo({
  orientation = 'horizontal',
  className,
  textClassName = 'text-foreground',
  imgWidth,
  imgHeight,
  textSizeClassName,
}: LogoProps) {
  const isVertical = orientation === 'vertical'
  const resolvedImgWidth = imgWidth ?? (isVertical ? 94 : 32)
  const resolvedImgHeight = imgHeight ?? (isVertical ? 119 : 40)
  const imageClassName =
    isVertical && imgWidth === undefined && imgHeight === undefined
      ? 'h-[119px] w-[94px] sm:h-[190px] sm:w-[150px]'
      : undefined
  const resolvedTextSizeClassName =
    textSizeClassName ??
    (isVertical ? 'text-[32px] sm:text-[48px]' : 'text-[16px] sm:text-[20px]')

  return (
    <div
      className={`flex ${isVertical ? 'flex-col items-center gap-4' : 'items-center gap-2'} ${className}`}
    >
      <img
        src={iconeLogo}
        alt="EnergiAI logo"
        width={resolvedImgWidth}
        height={resolvedImgHeight}
        className={imageClassName}
        style={
          imageClassName
            ? undefined
            : { width: resolvedImgWidth, height: resolvedImgHeight }
        }
      />
      <span className={`${resolvedTextSizeClassName} ${textClassName}`}>
        Energi<span className="font-bold">AI</span>
      </span>
    </div>
  )
}
