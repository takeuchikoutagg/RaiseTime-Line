import { cleanup, configure } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'

// findBy〜 / waitFor の待ち時間。全部のテストを並べて動かすと重くなり、初期値の1秒では足りないことがあるため長めにする
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
})
