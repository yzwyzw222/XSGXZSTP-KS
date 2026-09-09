import { get, post, initCsrf } from './request'

export async function login(username, password) {
  await initCsrf()
  return post('/auth/login', { body: { username, password } })
}

export async function logout() {
  return post('/auth/logout')
}

export async function getCurrentUser() {
  return get('/auth/me')
}