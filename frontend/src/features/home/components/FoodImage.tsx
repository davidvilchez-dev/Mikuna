import { useState } from 'react'
import type { VisualAsset } from '../home-types'

interface FoodImageProps {
  asset: VisualAsset
  className?: string
  priority?: boolean
}

export function FoodImage({ asset, className = '', priority = false }: FoodImageProps) {
  const [failed, setFailed] = useState(false)

  if (failed) {
    return (
      <span className={'image-fallback ' + className} role="img" aria-label={asset.alternativeText} data-asset={asset.id}>
        <span>{asset.id === 'logo' ? 'Mikuna' : 'Imagen no disponible'}</span>
      </span>
    )
  }

  return (
    <img
      src={asset.src}
      alt={asset.alternativeText}
      width={asset.width}
      height={asset.height}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      className={className}
      data-asset={asset.id}
      onError={() => setFailed(true)}
    />
  )
}
