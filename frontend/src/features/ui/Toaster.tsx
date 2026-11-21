import { ToastContainer } from 'react-toastify'
import 'react-toastify/dist/ReactToastify.css'
import './toast.css'

/** Contenedor único de avisos emergentes; va una sola vez dentro del router. */
export function Toaster() {
  return <ToastContainer position="top-right" limit={3} newestOnTop closeOnClick pauseOnFocusLoss={false} theme="light" />
}
