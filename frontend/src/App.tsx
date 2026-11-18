import { Navigate, Route, Routes } from 'react-router'
import { HomePage } from './features/home/HomePage'
import { AccountPage } from './features/auth/AccountPage'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { ProductsPage } from './features/catalog/ProductsPage'
import { RestaurantCatalogPage } from './features/catalog/RestaurantCatalogPage'
import { RestaurantSearchPage } from './features/catalog/RestaurantSearchPage'
import { AddressesPage } from './features/cart/AddressesPage'
import { CartPage } from './features/cart/CartPage'
import { CheckoutPage } from './features/checkout/CheckoutPage'
import { OrderPage } from './features/checkout/OrderPage'
import { AdminRequestsPage } from './features/restaurant/AdminRequestsPage'
import { RestaurantPage } from './features/restaurant/RestaurantPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/registro" element={<RegisterPage />} />
      <Route path="/iniciar-sesion" element={<LoginPage />} />
      <Route path="/mi-cuenta" element={<AccountPage />} />
      <Route path="/mi-restaurante" element={<RestaurantPage />} />
      <Route path="/restaurantes" element={<RestaurantSearchPage />} />
      <Route path="/restaurantes/:id" element={<RestaurantCatalogPage />} />
      <Route path="/mi-restaurante/productos" element={<ProductsPage />} />
      <Route path="/carrito" element={<CartPage />} />
      <Route path="/mis-direcciones" element={<AddressesPage />} />
      <Route path="/confirmar-pedido" element={<CheckoutPage />} />
      <Route path="/pedido/:id" element={<OrderPage />} />
      <Route path="/admin/solicitudes" element={<AdminRequestsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
