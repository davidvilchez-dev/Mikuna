import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { HomePage } from './features/home/HomePage'
import { getSession } from './features/auth/session-storage'
import { RequireSession } from './features/auth/RequireSession'

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
      <Route path="/mi-cuenta" element={<RequireSession><AccountPage /></RequireSession>} />
      <Route path="/mi-restaurante" element={<RequireSession><RestaurantDashboard /></RequireSession>} />
      <Route path="/mi-restaurante/negocio" element={<RequireSession><RestaurantPage /></RequireSession>} />
      <Route path="/mi-restaurante/sucursales/nueva" element={<RequireSession><NewBranchPage /></RequireSession>} />
      <Route path="/mi-restaurante/historial" element={<RequireSession><SalesHistoryPage /></RequireSession>} />
      <Route path="/restaurantes" element={<RestaurantSearchPage />} />
      <Route path="/restaurantes/:id" element={<RestaurantCatalogPage />} />
      <Route path="/mi-restaurante/productos" element={<RequireSession><ProductsPage /></RequireSession>} />
      <Route path="/mi-restaurante/pedidos" element={<RequireSession><OrdersPage /></RequireSession>} />
      <Route path="/carrito" element={<RequireSession><CartPage /></RequireSession>} />
      <Route path="/mis-direcciones" element={<RequireSession><AddressesPage /></RequireSession>} />
      <Route path="/confirmar-pedido" element={<RequireSession><CheckoutPage /></RequireSession>} />
      <Route path="/pedido/:id" element={<RequireSession><OrderPage /></RequireSession>} />
      <Route path="/admin" element={<RequireSession><AdminDashboardPage /></RequireSession>} />
      <Route path="/admin/solicitudes" element={<RequireSession><AdminRequestsPage /></RequireSession>} />
      <Route path="/repartidor/entregas" element={<RequireSession><CourierPage /></RequireSession>} />
      <Route path="/avisos" element={<RequireSession><NotificationsPage /></RequireSession>} />
      <Route path="/historial" element={<RequireSession><HistoryPage /></RequireSession>} />
      <Route path="/admin/entregas" element={<RequireSession><AdminDeliveriesPage /></RequireSession>} />
      <Route path="/admin/cuentas" element={<RequireSession><AdminAccountsPage /></RequireSession>} />
      <Route path="/admin/pedidos" element={<RequireSession><AdminOrdersPage /></RequireSession>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    <AssistantGate />
    </Suspense>
  )
}
