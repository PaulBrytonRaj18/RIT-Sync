export default function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center px-4">
      {Icon && (
        <div className="p-4 bg-gray-100 rounded-2xl mb-4">
          <Icon size={28} className="text-gray-400" />
        </div>
      )}
      <p className="font-medium text-gray-700 mb-1">{title}</p>
      {description && <p className="text-sm text-gray-500 mb-4">{description}</p>}
      {action}
    </div>
  )
}
