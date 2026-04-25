import { useState, useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import OfflineBanner from '../shared/OfflineBanner'
import { Menu, X } from 'lucide-react'
import { useOnlineStatus } from '../../hooks/useOnlineStatus'

export default function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const location = useLocation()
  useOnlineStatus()

  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])

  return (
    <div className="flex h-screen overflow-hidden bg-surface safe-top">
      <div className="hidden md:flex">
        <Sidebar />
      </div>

      {sidebarOpen && (
        <div className="fixed inset-0 z-50 md:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setSidebarOpen(false)} />
          <div className="absolute left-0 top-0 h-full animate-slide-up">
            <Sidebar onClose={() => setSidebarOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="md:hidden flex items-center gap-3 px-4 py-3 bg-white border-b border-gray-100 safe-top">
          <button onClick={() => setSidebarOpen(true)} className="p-2 -ml-2 hover:bg-gray-100 rounded-xl transition-colors touch-manipulation">
            <Menu size={22} className="text-gray-600" />
          </button>
          <span className="font-semibold text-gray-900">RIT-Sync</span>
        </header>

        <OfflineBanner />

        <main className="flex-1 overflow-y-auto overflow-x-hidden mobile-scroll">
          <div className="max-w-6xl mx-auto px-4 py-4 md:px-6 md:py-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
