/** 旧书签只允许回到现有业务页，拒绝外站、路径逃逸和认证循环。 */
export function safeReturnPath(value) {
  if (typeof value !== 'string' || !/^\/(?:$|(?:crawler|management)(?:\/|\?|$))/.test(value) ||
    /[\\\s\u0000-\u001f]|%2f|%5c|%0[0-9a-f]|%25/i.test(value)) return '/'
  const url = new URL(value, 'http://system.local')
  if (url.origin !== 'http://system.local' || !/^\/(?:$|crawler(?:\/|$)|management\/(?:users(?:\/overview)?|logs|audits)$)/.test(url.pathname) ||
    /\/(?:login|register|session-expired)(?:\/|$)/.test(url.pathname) || /\/api(?:\/|$)/i.test(url.pathname)) return '/'
  return `${url.pathname}${url.search}${url.hash}`
}
