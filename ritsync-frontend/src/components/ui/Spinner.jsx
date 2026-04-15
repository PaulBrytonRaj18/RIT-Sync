import clsx from 'clsx'
export default function Spinner({ size = 'md', className }) {
  const s = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-10 w-10' }[size]
  return (
    <div className={clsx('animate-spin rounded-full border-2 border-gray-200 border-t-primary-600', s, className)} />
  )
}
