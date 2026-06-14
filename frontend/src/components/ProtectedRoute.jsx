import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext.jsx'

/**
 * Wraps a route so only authenticated users can access it.
 * Unauthenticated users are redirected to /login with the original path saved.
 *
 * Usage: <Route path="/..." element={<ProtectedRoute><MyPage /></ProtectedRoute>} />
 *
 * Optional `roles` prop restricts access to specific roles:
 * <ProtectedRoute roles={['ADMIN']}> ... </ProtectedRoute>
 */
export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, role } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (roles && !roles.includes(role)) {
    return <Navigate to="/" replace />
  }

  return children
}
