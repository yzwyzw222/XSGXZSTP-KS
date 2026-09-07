/**
 * 会话作用域清理登记簿。
 *
 * 独立于 Pinia store 的叶子模块：`stores/session.ts` 与
 * `composables/useSessionCleanup.ts` 都依赖它，但两者之间不存在依赖，
 * 避免 router / store / composable 之间的循环引用。
 *
 * 只保存回调函数，不保存图实例、DOM、AbortController 或定时器句柄本身，
 * 因此不存在把非序列化对象写入持久化状态的风险。
 */
const cleanups = new Set<() => void>()

/** 登记一个在登出或会话失效时执行的清理回调，返回注销函数。 */
export function registerSessionCleanup(cleanup: () => void): () => void {
  cleanups.add(cleanup)
  return () => {
    cleanups.delete(cleanup)
  }
}

/**
 * 执行并清空全部登记的清理回调。
 * 单个回调抛错不得阻断其余清理，因此逐个隔离捕获。
 */
export function runSessionCleanups(): void {
  const pending = [...cleanups]
  cleanups.clear()
  pending.forEach((cleanup) => {
    try {
      cleanup()
    } catch {
      // 仅记录固定说明，既允许其余资源清理，也不暴露回调中的业务数据。
      console.error('会话资源清理失败，其他清理仍继续执行')
    }
  })
}

/** 仅用于测试：清空登记簿。 */
export function resetSessionCleanupsForTest(): void {
  cleanups.clear()
}
