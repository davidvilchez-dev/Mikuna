# Contract: Rutas y puntos de entrada de la interfaz

## Rutas (todas públicas en el navegador)

| Ruta | Pantalla | Comportamiento |
| --- | --- | --- |
| `/` | Inicio existente | Sin cambios visuales, salvo los puntos de entrada de abajo. |
| `/registro?tipo=cliente\|restaurante\|repartidor` | RegisterPage | Selector de tipo visible (sin administrador), preseleccionado según `tipo` (por defecto `cliente`). Campos: nombre, correo, contraseña y confirmación. Si el registro es correcto, muestra una confirmación con un enlace a `/iniciar-sesion`; para restaurante añade el aviso «habilitación comercial pendiente». Enlace para volver al inicio. |
| `/iniciar-sesion` | LoginPage | Correo y contraseña. Si es correcto, guarda la sesión y navega a `/mi-cuenta`. Si falla, muestra el mensaje genérico del backend. |
| `/mi-cuenta` | AccountPage | Sin sesión válida, redirige a `/iniciar-sesion`. Con sesión, muestra nombre, correo, rol y estado (y la aprobación pendiente si es restaurante) y un botón «Cerrar sesión», que borra la sesión y vuelve a `/`. |

El envío de un formulario deshabilita el botón mientras la petición está en curso, para evitar
envíos repetidos. Si el backend no responde o falla, se muestra «No se pudo completar la
operación, inténtalo de nuevo» y no se confirma nada.

## Puntos de entrada en el inicio

| Componente | Control | Pasa de «próximamente» a |
| --- | --- | --- |
| HeroSection | «Crear cuenta» | `/registro` |
| SiteFooter | «Crear cuenta» | `/registro` |
| ForRestaurantsPage | «Registrar mi restaurante» / «Ya tengo cuenta» | `/registro?tipo=restaurante` / `/iniciar-sesion` |
| CourierPage | «Quiero ser repartidor», «Quiero sumarme» / «Ya tengo cuenta» | `/registro?tipo=repartidor` / `/iniciar-sesion` |
| SiteHeader | nuevo enlace «Iniciar sesión» (si hay sesión: «Mi cuenta») | `/iniciar-sesion` o `/mi-cuenta` |

Siguen como «próximamente»: «Pedir ahora», «Empezar a pedir» y las categorías (fuera del
incremento, SC-005).
