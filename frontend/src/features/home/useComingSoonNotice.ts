import { useCallback, useEffect, useRef, useState } from 'react'
import { NOTICE_DURATION_MS } from './home-types'
import type { MenuToggleRef, PauseReason } from './home-types'

export function useComingSoonNotice(fallbackFocus: MenuToggleRef) {
  const [visible, setVisible] = useState(false)
  const [revision, setRevision] = useState(0)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const remaining = useRef(NOTICE_DURATION_MS)
  const startedAt = useRef(0)
  const pauseReasons = useRef(new Set<PauseReason>())
  const triggerRef = useRef<HTMLButtonElement | null>(null)

  const clearTimer = useCallback(() => {
    if (timer.current !== null) {
      clearTimeout(timer.current)
      timer.current = null
    }
  }, [])

  const expire = useCallback(() => {
    clearTimer()
    remaining.current = 0
    pauseReasons.current.clear()
    setVisible(false)
  }, [clearTimer])

  const startTimer = useCallback(() => {
    clearTimer()
    if (pauseReasons.current.size > 0) return
    startedAt.current = Date.now()
    timer.current = setTimeout(expire, Math.max(0, remaining.current))
  }, [clearTimer, expire])

  const pause = useCallback((reason: PauseReason) => {
    if (pauseReasons.current.has(reason)) return
    if (timer.current !== null) {
      remaining.current = Math.max(0, remaining.current - (Date.now() - startedAt.current))
      clearTimer()
    }
    pauseReasons.current.add(reason)
  }, [clearTimer])

  const resume = useCallback((reason: PauseReason) => {
    if (!pauseReasons.current.delete(reason)) return
    if (remaining.current > 0 && pauseReasons.current.size === 0) startTimer()
  }, [startTimer])

  const show = useCallback((trigger: HTMLButtonElement) => {
    triggerRef.current = trigger
    remaining.current = NOTICE_DURATION_MS
    if (document.hidden) pauseReasons.current.add('document-hidden')
    else pauseReasons.current.delete('document-hidden')
    setRevision((value) => value + 1)
    setVisible(true)
    startTimer()
  }, [startTimer])

  const dismiss = useCallback((restoreFocus = false) => {
    expire()
    if (restoreFocus) {
      const trigger = triggerRef.current
      const target = trigger?.isConnected && trigger.getClientRects().length > 0
        ? trigger : fallbackFocus.current
      target?.focus({ preventScroll: true })
    }
  }, [expire, fallbackFocus])

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.hidden) pause('document-hidden')
      else resume('document-hidden')
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      clearTimer()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [clearTimer, pause, resume])

  return { visible, revision, triggerRef, show, pause, resume, dismiss }
}
