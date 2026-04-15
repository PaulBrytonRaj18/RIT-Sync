import { useEffect, useState } from 'react'
import { Smartphone, CheckCircle, XCircle, Send, Trash2, RefreshCw } from 'lucide-react'
import { devicesApi } from '../../api/devices'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import toast from 'react-hot-toast'
import { fmt } from '../../utils/format'

export default function DeviceManagementPage() {
  const [devices, setDevices] = useState([])
  const [loading, setLoading] = useState(true)
  const [sendingId, setSendingId] = useState(null)

  const loadDevices = async () => {
    setLoading(true)
    try {
      const data = await devicesApi.getMyDevices()
      setDevices(data)
    } catch (err) {
      toast.error('Failed to load devices')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDevices()
  }, [])

  const handleSendVerification = async (deviceId) => {
    setSendingId(deviceId)
    try {
      await devicesApi.sendVerificationEmail(deviceId)
      toast.success('Verification email sent! Check your inbox.')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to send verification email')
    } finally {
      setSendingId(null)
    }
  }

  const handleRevoke = async (deviceId) => {
    if (!confirm('Are you sure you want to revoke this device?')) return
    try {
      await devicesApi.revokeDevice(deviceId)
      toast.success('Device revoked')
      loadDevices()
    } catch (err) {
      toast.error('Failed to revoke device')
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <Spinner size="lg" />
      </div>
    )
  }

  return (
    <div className="animate-fade-in max-w-2xl mx-auto">
      <PageHeader
        title="My Devices"
        subtitle="Manage devices registered to your account"
        action={
          <Button variant="secondary" size="sm" onClick={loadDevices}>
            <RefreshCw size={14} /> Refresh
          </Button>
        }
      />

      <div className="card">
        <div className="flex items-center gap-3 mb-4 p-3 bg-blue-50 border border-blue-100 rounded-xl">
          <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
            <Smartphone size={16} className="text-blue-600" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-medium text-blue-900">Device Verification Required</p>
            <p className="text-xs text-blue-600">New devices must be verified via email before use.</p>
          </div>
        </div>

        {devices.length === 0 ? (
          <EmptyState
            icon={Smartphone}
            title="No devices registered"
            description="Devices will appear here when you log in from a new browser or device."
          />
        ) : (
          <div className="divide-y divide-gray-50">
            {devices.map(device => (
              <div key={device.id} className="py-4 first:pt-0 last:pb-0">
                <div className="flex items-start gap-3">
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                    device.isTrusted ? 'bg-green-100' : 'bg-amber-100'
                  }`}>
                    {device.isTrusted ? (
                      <CheckCircle size={20} className="text-green-600" />
                    ) : (
                      <XCircle size={20} className="text-amber-600" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-gray-900">{device.deviceName}</p>
                      {device.isTrusted ? (
                        <span className="badge-green text-xs">Verified</span>
                      ) : (
                        <span className="badge-amber text-xs">Pending</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 font-mono mt-0.5">{device.deviceFingerprint}</p>
                    <p className="text-xs text-gray-400 mt-1">
                      Added {fmt.datetime(device.boundAt)}
                      {device.verifiedAt && ` · Verified ${fmt.datetime(device.verifiedAt)}`}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {!device.isTrusted && (
                      <Button
                        variant="secondary"
                        size="sm"
                        loading={sendingId === device.id}
                        onClick={() => handleSendVerification(device.id)}
                      >
                        <Send size={14} /> Verify
                      </Button>
                    )}
                    {device.isTrusted && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleRevoke(device.id)}
                        className="text-red-600 hover:bg-red-50"
                      >
                        <Trash2 size={14} />
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
