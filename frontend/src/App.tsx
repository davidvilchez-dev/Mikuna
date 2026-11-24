import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { HomePage } from './features/home/HomePage'
import { getSession } from './features/auth/session-storage'

// Cada página se carga bajo demanda: en desarrollo Vite solo transforma lo que se visita.
const AccountPage = lazy(() => import('./features/auth/AccountPage').then((m) => ({ default: m.AccountPage })))
const LoginPage = lazy(() => import('./features/auth/LoginPage').then((m) => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('./features/auth/RegisterPage').then((m) => ({ default: m.RegisterPage })))
const ProductsPage = lazy(() => import('./features/catalog/ProductsPage').then((m) => ({ default: m.ProductsPage })))
const OrdersPage = lazy(() => import('./features/restaurant/OrdersPage').then((m) => ({ default: m.OrdersPage })))
const RestaurantCatalogPage = lazy(() => import('./features/catalog/RestaurantCatalogPage').then((m) => ({ default: m.RestaurantCatalogPage })))
const RestaurantSearchPage = lazy(() => import('./features/catalog/RestaurantSearchPage').then((m) => ({ default: m.RestaurantSearchPage })))
const AddressesPage = lazy(() => import('./features/cart/AddressesPage').then((m) => ({ default: m.AddressesPage })))
const CartPage = lazy(() => import('./features/cart/CartPage').then((m) => ({ default: m.CartPage })))
const CheckoutPage = lazy(() => import('./features/checkout/CheckoutPage').then((m) => ({ default: m.CheckoutPage })))
const OrderPage = lazy(() => import('./features/checkout/OrderPage').then((m) => ({ default: m.OrderPage })))
const AdminRequestsPage = lazy(() => import('./features/restaurant/AdminRequestsPage').then((m) => ({ default: m.AdminRequestsPage })))
const RestaurantDashboard = lazy(() => import('./features/restaurant/RestaurantDashboard').then((m) => ({ default: m.RestaurantDashboard })))
const NewBranchPage = lazy(() => import('./features/restaurant/RestaurantPage').then((m) => ({ default: m.NewBranchPage })))
const RestaurantPage = lazy(() => import('./features/restaurant/RestaurantPage').then((m) => ({ default: m.RestaurantPage })))
const SalesHistoryPage = lazy(() => import('./features/restaurant/SalesHistoryPage').then((m) => ({ default: m.SalesHistoryPage })))
const AdminDeliveriesPage = lazy(() => import('./features/delivery/AdminDeliveriesPage').then((m) => ({ default: m.AdminDeliveriesPage })))
const CourierPage = lazy(() => import('./features/delivery/CourierPage').then((m) => ({ default: m.CourierPage })))
const NotificationsPage = lazy(() => import('./features/notifications/NotificationsPage').then((m) => ({ default: m.NotificationsPage })))
const HistoryPage = lazy(() => import('./features/history/HistoryPage').then((m) => ({ default: m.HistoryPage })))
const AdminAccountsPage = lazy(() => import('./features/admin/AdminAccountsPage').then((m) => ({ default: m.AdminAccountsPage })))
const AdminOrdersPage = lazy(() => import('./features/admin/AdminOrdersPage').then((m) => ({ default: m.AdminOrdersPage })))
const PublicInfoPage = lazy(() => import('./features/public-pages/PublicInfoPage').then((m) => ({ default: m.PublicInfoPage })))
const AdminDashboardPage = lazy(() => import('./features/admin/AdminDashboardPage').then((m) => ({ default: m.AdminDashboardPage })))
const AssistantChat = lazy(() => import('./features/assistant/AssistantChat').then((m) => ({ default: m.AssistantChat })))

/** El asistente de IA (spec 013) solo existe para clientes con sesión; se relee la sesión en cada cambio de ruta. */
function AssistantGate() {
  useLocation()
  const session = getSession()
  if (!session || session.role !== 'CUSTOMER') return null
  return <AssistantChat key={session.token} token={session.token} />
}

export default function App() {
  return (
    <Suspense fallback={null}>
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/como-funciona" element={<PublicInfoPage page="como-funciona" />} />
      <Route path="/para-restaurantes" element={<PublicInfoPage page="para-restaurantes" />} />
      <Route path="/se-repartidor" element={<PublicInfoPage page="se-repartidor" />} />
      <Route path="/sobre-nosotros" element={<PublicInfoPage page="sobre-nosotros" />} />
      <Route path="/registro" element={<RegisterPage />} />
      <Route path="/iniciar-sesion" element={<LoginPage />} />
      <Route path="/mi-cuenta" element={<AccountPage />} />
      <Route path="/mi-restaurante" element={<RestaurantDashboard />} />
      <Route path="/mi-restaurante/negocio" element={<RestaurantPage />} />
      <Route path="/mi-restaurante/sucursales/nueva" element={<NewBranchPage />} />
      <Route path="/mi-restaurante/historial" element={<SalesHistoryPage />} />
      <Route path="/restaurantes" element={<RestaurantSearchPage />} />
      <Route path="/restaurantes/:id" element={<RestaurantCatalogPage />} />
      <Route path="/mi-restaurante/productos" element={<ProductsPage />} />
      <Route path="/mi-restaurante/pedidos" element={<OrdersPage />} />
      <Route path="/carrito" element={<CartPage />} />
      <Route path="/mis-direcciones" element={<AddressesPage />} />
      <Route path="/confirmar-pedido" element={<CheckoutPage />} />
      <Route path="/pedido/:id" element={<OrderPage />} />
      <Route path="/admin" element={<AdminDashboardPage />} />
      <Route path="/admin/solicitudes" element={<AdminRequestsPage />} />
      <Route path="/repartidor/entregas" element={<CourierPage />} />
      <Route path="/avisos" element={<NotificationsPage />} />
      <Route path="/historial" element={<HistoryPage />} />
      <Route path="/admin/entregas" element={<AdminDeliveriesPage />} />
      <Route path="/admin/cuentas" element={<AdminAccountsPage />} />
      <Route path="/admin/pedidos" element={<AdminOrdersPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    <AssistantGate />
    </Suspense>
  )
}
