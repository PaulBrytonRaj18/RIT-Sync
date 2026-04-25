import clsx from 'clsx'
import Spinner from './Spinner'

export default function Button({ variant = 'primary', size = 'md', loading, children, className, ...props }) {
  const base  = 'btn touch-manipulation active:scale-.98 transition-transform'
  const vars  = { primary: 'btn-primary', secondary: 'btn-secondary', danger: 'btn-danger',
                  ghost: 'inline-flex items-center justify-center gap-2 px-4 py-3 text-base text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-xl transition-colors' }
  const sizes = { sm: 'text-sm px-4 py-2.5', md: 'text-base px-5 py-3', lg: 'text-lg px-6 py-4' }
  return (
    <button className={clsx(base, vars[variant], sizes[size], className)} disabled={loading || props.disabled} {...props}>
      {loading && <Spinner size="sm" />}
      {children}
    </button>
  )
}
