import { useEffect, useLayoutEffect, useRef } from 'react'
import type { RefObject } from 'react'
import { COMING_SOON_MESSAGE } from '../home-types'
import type { PauseReason } from '../home-types'

interface ComingSoonNoticeProps {
  visible: boolean
  revision: number
  triggerRef: RefObject<HTMLButtonElement | null>
  onPause: (reason: PauseReason) => void
  onResume: (reason: PauseReason) => void
  onDismiss: (restoreFocus?: boolean) => void
}

export function ComingSoonNotice({
  visible, revision, triggerRef, onPause, onResume, onDismiss,
}: ComingSoonNoticeProps) {
  const boxRef = useRef<HTMLDivElement>(null)
  const statusRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    const region = statusRef.current
    if (!region) return
    region.textContent = ''
    if (!visible) return
    const announcement = setTimeout(() => { region.textContent = COMING_SOON_MESSAGE }, 0)
    return () => clearTimeout(announcement)
  }, [visible, revision])

  useLayoutEffect(() => {
    if (!visible) return
    const position = () => {
      const box = boxRef.current
      const trigger = triggerRef.current
      if (!box || !trigger) return
      let anchor = trigger.getBoundingClientRect()
      if (!anchor.width || !anchor.height) {
        const menuToggle = document.querySelector('[data-mobile-menu-toggle]')
        if (menuToggle) anchor = menuToggle.getBoundingClientRect()
      }
      const margin = 12
      const width = box.offsetWidth
      const height = box.offsetHeight
      const maxX = Math.max(margin, window.innerWidth - width - margin)
      const maxY = Math.max(margin, window.innerHeight - height - margin)
      const clamp = (value: number, maximum: number) => Math.min(maximum, Math.max(margin, value))
      const centeredX = clamp(anchor.left + anchor.width / 2 - width / 2, maxX)
      const candidates = [
        { left: centeredX, top: anchor.bottom + 12 },
        { left: centeredX, top: anchor.top - height - 12 },
        { left: maxX, top: maxY },
        { left: margin, top: margin },
        { left: maxX, top: margin },
        { left: margin, top: maxY },
      ].map((point) => ({ left: clamp(point.left, maxX), top: clamp(point.top, maxY) }))
      const protectedRects = Array.from(document.querySelectorAll<HTMLElement>('[data-keep-clear]'))
        .map((element) => element.getBoundingClientRect())
        .filter((rect) => rect.width && rect.height && rect.bottom > 0 && rect.top < window.innerHeight)
      const score = (point: { left: number; top: number }) => protectedRects.reduce((sum, rect) => {
        const horizontal = Math.max(0, Math.min(point.left + width, rect.right) - Math.max(point.left, rect.left))
        const vertical = Math.max(0, Math.min(point.top + height, rect.bottom) - Math.max(point.top, rect.top))
        return sum + horizontal * vertical
      }, 0)
      const best = candidates.reduce((choice, candidate) => score(candidate) < score(choice) ? candidate : choice)
      box.style.left = best.left + 'px'
      box.style.top = best.top + 'px'
    }
    position()
    const observer = new ResizeObserver(position)
    if (boxRef.current) observer.observe(boxRef.current)
    window.addEventListener('resize', position)
    window.addEventListener('scroll', position, { passive: true })
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', position)
      window.removeEventListener('scroll', position)
    }
  }, [visible, revision, triggerRef])

  return (
    <div
      ref={boxRef}
      className="coming-soon-notice"
      data-visible={visible}
      data-testid="coming-soon-notice"
      onMouseEnter={() => onPause('pointer')}
      onMouseLeave={() => onResume('pointer')}
      onFocusCapture={() => onPause('focus')}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) onResume('focus')
      }}
    >
      <p ref={statusRef} role="status" aria-live="polite" aria-atomic="true" />
      <button
        hidden={!visible}
        type="button"
        className="notice-close"
        aria-label="Cerrar aviso"
        onClick={() => onDismiss(true)}
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  )
}
