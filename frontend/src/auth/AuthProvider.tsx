import * as React from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/endpoints'
import { getToken, setToken, setUnauthorizedHandler } from '../api/client'
import type { Role, UserView } from '../api/types'

interface AuthState {
  user: UserView | null
  loading: boolean
  login: (email: string, password: string) => Promise<UserView>
  demoLogin: (key: string) => Promise<UserView>
  logout: (expired?: boolean) => void
  hasRole: (...roles: Role[]) => boolean
}

const AuthContext = React.createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const qc = useQueryClient()
  const [user, setUser] = React.useState<UserView | null>(null)
  const [loading, setLoading] = React.useState<boolean>(!!getToken())

  const logout = React.useCallback((expired = false) => {
    setToken(null)
    setUser(null)
    qc.clear()
    if (expired && !window.location.pathname.startsWith('/login')) {
      window.location.assign('/login?expired=1')
    }
  }, [qc])

  React.useEffect(() => {
    setUnauthorizedHandler(() => logout(true))
    if (!getToken()) return
    authApi.me().then(setUser).catch(() => setToken(null)).finally(() => setLoading(false))
  }, [logout])

  const login = React.useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    setToken(res.accessToken)
    setUser(res.user)
    return res.user
  }, [])

  const demoLogin = React.useCallback(async (key: string) => {
    const res = await authApi.demoLogin(key)
    qc.clear()
    setToken(res.accessToken)
    setUser(res.user)
    return res.user
  }, [qc])

  const hasRole = React.useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user])

  return (
    <AuthContext.Provider value={{ user, loading, login, demoLogin, logout, hasRole }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = React.useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}

export function homeFor(role: Role): string {
  return role === 'HR' ? '/hr' : role === 'MANAGER' ? '/manager' : '/me'
}
