import { Navigate, Route, Routes } from 'react-router'
import { HomePage } from './features/home/HomePage'
import { AccountPage } from './features/auth/AccountPage'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { ProductsPage } from './features/catalog/ProductsPage'
import { OrdersPage } from './features/restaurant/OrdersPage'
import { RestaurantCatalogPage } from './features/catalog/RestaurantCatalogPage'
import { RestaurantSearchPage } from './features/catalog/RestaurantSearchPage'
import { AddressesPage } from './features/cart/AddressesPage'
import { CartPage } from './features/cart/CartPage'
import { CheckoutPage } from './features/checkout/CheckoutPage'
import { OrderPage } from './features/checkout/OrderPage'
import { AdminRequestsPage } from './features/restaurant/AdminRequestsPage'
import { RestaurantDashboard } from './features/restaurant/RestaurantDashboard'
import { RestaurantPage } from './features/restaurant/RestaurantPage'
import { SalesHistoryPage } from './features/restaurant/SalesHistoryPage'
import { AdminDeliveriesPage } from './features/delivery/AdminDeliveriesPage'
import { CourierPage } from './features/delivery/CourierPage'
import { NotificationsPage } from './features/notifications/NotificationsPage'
import { HistoryPage } from './features/history/HistoryPage'
import { AdminAccountsPage } from './features/admin/AdminAccountsPage'
import { AdminOrdersPage } from './features/admin/AdminOrdersPage'
import { PublicInfoPage } from './features/public-pages/PublicInfoPage'
import { AdminDashboardPage } from './features/admin/AdminDashboardPage'

export default function App() {
  return (
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
      <Route path="/admin/solicitudes"element={<AdminRequestsPage />} />
      <Route path="/repartidor/entregas" element={<CourierPage />} />
      <Route path="/avisos" element={<NotificationsPage />} />
      <Route path="/historial" element={<HistoryPage />} />
      <Route path="/admin/entregas" element={<AdminDeliveriesPage />} />
      <Route path="/admin/cuentas" element={<AdminAccountsPage />} />
      <Route path="/admin/pedidos" element={<AdminOrdersPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
