import clsx from 'clsx'
export default function Badge({ children, variant = 'gray' }) {
  const variants = {
    green: 'badge-green', red: 'badge-red',
    yellow: 'badge-yellow', blue: 'badge-blue', gray: 'badge-gray',
    purple: 'badge bg-purple-100 text-purple-700',
  }
  return <span className={clsx(variants[variant] || 'badge-gray')}>{children}</span>
}
