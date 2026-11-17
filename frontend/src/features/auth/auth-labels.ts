import type { AccountStatus, PublicRole, Role } from './auth-api'

export const roleLabels: Record<Role, string> = {
  CUSTOMER: 'Cliente',
  RESTAURANT: 'Restaurante',
  COURIER: 'Repartidor',
  ADMIN: 'Administrador',
}

export const statusLabels: Record<AccountStatus, string> = {
  ENABLED: 'Habilitada',
  DISABLED: 'No habilitada',
}

export const publicRoleOptions: readonly { value: PublicRole; label: string; hint: string }[] = [
  { value: 'CUSTOMER', label: 'Cliente', hint: 'Pide a restaurantes de tu ciudad' },
  { value: 'RESTAURANT', label: 'Restaurante', hint: 'Recibe pedidos tras la aprobación' },
  { value: 'COURIER', label: 'Repartidor', hint: 'Realiza entregas con Mikuna' },
]
