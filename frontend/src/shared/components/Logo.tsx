import iconeLogo from '@/shared/assets/images/energiai-icone.png'

interface LogoProps {
  className?: string
  textClassName?: string
  imgWidth?: number
  imgHeight?: number
  textSizeClassName?: string
}

const DEFAULT_IMG_WIDTH = 32
const DEFAULT_IMG_HEIGHT = 40
const DEFAULT_TEXT_SIZE_CLASSNAME = 'text-[16px] sm:text-[20px]'

export function Logo({
  className,
  textClassName = 'text-foreground',
  imgWidth = DEFAULT_IMG_WIDTH,
  imgHeight = DEFAULT_IMG_HEIGHT,
  textSizeClassName = DEFAULT_TEXT_SIZE_CLASSNAME,
}: LogoProps) {
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <img
        src={iconeLogo}
        alt="EnergiAI logo"
        width={imgWidth}
        height={imgHeight}
        style={{ width: imgWidth, height: imgHeight }}
      />
      <span className={`${textSizeClassName} ${textClassName}`}>
        Energi<span className="font-bold">AI</span>
      </span>
    </div>
  )
}
