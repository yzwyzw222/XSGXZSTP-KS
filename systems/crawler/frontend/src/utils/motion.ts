export function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function easeOutCubic(progress: number): number {
  const bounded = Math.min(1, Math.max(0, progress))
  return 1 - (1 - bounded) ** 3
}
