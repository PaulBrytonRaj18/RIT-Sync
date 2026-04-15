export default function Select({ label, error, children, ...props }) {
  return (
    <div>
      {label && <label className="label">{label}</label>}
      <select className="input" {...props}>{children}</select>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}
