import clsx from 'clsx'
export default function Input({ label, error, className, ...props }) {
  return (
    <div>
      {label && <label className="label">{label}</label>}
      <input className={clsx('input', error && 'border-red-400 focus:border-red-400 focus:ring-red-200', className)} {...props} />
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}
