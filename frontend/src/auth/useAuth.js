import { useContext } from 'react'
import { AuthContext } from './authContext.js'

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth は AuthProvider の中で使ってください')
  }
  return context
}
