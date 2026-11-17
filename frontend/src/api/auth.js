import request from '@/utils/request'

export function registerApi(data) {
  return request.post('/auth/register', data, {
    skipAuth: true,
  })
}

export function loginApi(data) {
  return request.post('/auth/login', data, {
    skipAuth: true,
  })
}

export function currentUserApi() {
  return request.get('/auth/me')
}
