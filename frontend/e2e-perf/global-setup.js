import { request } from '@playwright/test'
import { PASSWORD, PERF_USER } from './measure.js'

// 測り始める前に、性能テスト用のデータでログインできるかを確かめる。
// できなければ、バックエンドが raisetime_perf で動いていないか、データを入れていない（開発用のデータで測ってしまわないように止める）
export default async function globalSetup() {
  const api = await request.newContext({ baseURL: 'http://localhost:8081' })
  try {
    const health = await api.get('/actuator/health').catch(() => null)
    if (!health || !health.ok()) {
      throw new Error('バックエンドに接続できません。perf/run.sh backend で、性能テスト用の DB で起動してください')
    }
    await api.get('/api/auth/csrf')
    const cookies = await api.storageState()
    const csrf = cookies.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
    const login = await api.post('/api/auth/login', {
      data: { email: `${PERF_USER}@example.com`, password: PASSWORD },
      headers: { 'X-XSRF-TOKEN': csrf },
    })
    if (!login.ok()) {
      throw new Error(
        `${PERF_USER} でログインできません。バックエンドを perf/run.sh backend で起動し、perf/run.sh seed でデータを入れてください`,
      )
    }
  } finally {
    await api.dispose()
  }
}
