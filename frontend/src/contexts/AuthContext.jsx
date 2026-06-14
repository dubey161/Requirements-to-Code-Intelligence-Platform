import { createContext, useContext, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'

const AuthContext = createContext(null)

const STORAGE_KEY = 'ai_platform_auth'

function decodeJwt(token) {
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return {}
  }
}

function loadAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (data.accessToken && !data.email) {
      data.email = decodeJwt(data.accessToken).sub ?? null
    }
    return data
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(loadAuth)

  const login = useCallback((data) => {
    // data = { accessToken, refreshToken, tokenType, role, userId }
    const enriched = { ...data, email: decodeJwt(data.accessToken).sub ?? null }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(enriched))
    setAuth(enriched)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setAuth(null)
  }, [])

  const value = {
    auth,
    login,
    logout,
    isAuthenticated: !!auth?.accessToken,
    role: auth?.role ?? null,
    userId: auth?.userId ?? null,
    token: auth?.accessToken ?? null,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
