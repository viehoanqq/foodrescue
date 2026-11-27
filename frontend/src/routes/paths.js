export const STORE_ROLES = ['STORE_OWNER', 'STORE_STAFF'];

export function loginForRole(role) {
  if (role === 'ADMIN') return '/admin/login';
  if (STORE_ROLES.includes(role)) return '/store/login';
  return '/login';
}

export function homeForRole(role) {
  if (role === 'ADMIN') return '/admin/users';
  if (STORE_ROLES.includes(role)) return '/store/orders';
  return '/';
}
