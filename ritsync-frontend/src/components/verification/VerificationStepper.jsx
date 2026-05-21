import { Check, Loader } from 'lucide-react'
import clsx from 'clsx'

const STEPS = [
  { id: 1, label: 'Face ID',  sub: 'Camera + liveness'    },
  { id: 2, label: 'Location', sub: 'GPS geofence check'   },
  { id: 3, label: 'Beacon',   sub: 'BLE proximity check'  },
  { id: 4, label: 'Device',   sub: 'Registered device'    },
]

export default function VerificationStepper({ currentStep, stepStatuses }) {
  return (
    <div className="flex items-start justify-between relative mb-8">
      <div className="absolute top-5 left-0 right-0 h-0.5 bg-gray-100 z-0" />

      {STEPS.map((step, i) => {
        const status = stepStatuses?.[step.id] ?? 'idle'
        const isActive = status === 'active'
        const isDone   = status === 'done'
        const isFailed = status === 'failed'

        return (
          <div key={step.id} className="flex flex-col items-center gap-2 z-10 flex-1">
            <div className={clsx(
              'w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold border-2 transition-all duration-300',
              isDone   && 'bg-green-500 border-green-500 text-white',
              isFailed && 'bg-red-500 border-red-500 text-white',
              isActive && 'bg-primary-600 border-primary-600 text-white animate-pulse',
              status === 'idle' && 'bg-white border-gray-200 text-gray-400',
            )}>
              {isDone   && <Check size={16} strokeWidth={3} />}
              {isFailed && <span>✕</span>}
              {isActive && <Loader size={16} className="animate-spin" />}
              {status === 'idle' && <span>{step.id}</span>}
            </div>

            <div className="text-center">
              <p className={clsx('text-xs font-semibold',
                isDone   && 'text-green-600',
                isFailed && 'text-red-500',
                isActive && 'text-primary-600',
                status === 'idle' && 'text-gray-400',
              )}>{step.label}</p>
              <p className="text-xs text-gray-400 hidden sm:block">{step.sub}</p>
            </div>
          </div>
        )
      })}
    </div>
  )
}
