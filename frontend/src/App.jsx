import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import Sidebar from './components/Sidebar.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import DeveloperDashboard from './pages/DeveloperDashboard.jsx'
import ManagerDashboard from './pages/ManagerDashboard.jsx'
import AdminDashboard from './pages/AdminDashboard.jsx'
import RequirementDetail from './pages/RequirementDetail.jsx'
import Search from './pages/Search.jsx'
import ExternalReview from './pages/ExternalReview.jsx'

function HomeRedirect() {
  const { role } = useAuth()
  if (role === 'ADMIN')                return <Navigate to="/dashboard/admin"   replace />
  if (role === 'ENGINEERING_MANAGER')  return <Navigate to="/dashboard/manager" replace />
  return <Navigate to="/dashboard/developer" replace />
}

function AppShell() {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated) return null
  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg)' }}>
      <Sidebar />
      <main style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', minWidth: 0 }}>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/dashboard/developer" element={
            <ProtectedRoute roles={['DEVELOPER', 'ADMIN', 'ENGINEERING_MANAGER']}>
              <DeveloperDashboard />
            </ProtectedRoute>
          } />
          <Route path="/dashboard/manager" element={
            <ProtectedRoute roles={['ENGINEERING_MANAGER', 'ADMIN']}>
              <ManagerDashboard />
            </ProtectedRoute>
          } />
          <Route path="/dashboard/admin" element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="/pipeline" element={
            <ProtectedRoute><Dashboard /></ProtectedRoute>
          } />
          <Route path="/requirements/:id" element={
            <ProtectedRoute><RequirementDetail /></ProtectedRoute>
          } />
          <Route path="/search" element={
            <ProtectedRoute><Search /></ProtectedRoute>
          } />
          <Route path="/review" element={
            <ProtectedRoute><ExternalReview /></ProtectedRoute>
          } />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/*" element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        } />
      </Routes>
    </AuthProvider>
  )
}
