import { onScopeDispose } from 'vue'

import { registerSessionCleanup } from '@/services/session-scope'

/**
 * 把"轮询、动画帧、监听器、图实例"等与当前登录账号绑定的资源
 * 登记到会话作用域：组件卸载时自动注销，登出或会话失效时立即清理。
 *
 * 只在组件或 composable 的作用域内调用。
 */
export function useSessionCleanup(cleanup: () => void): void {
  const unregister = registerSessionCleanup(cleanup)
  onScopeDispose(unregister)
}
