# Visual Assets Contract: Inicio de Mikuna

**Actualización del usuario:** usar las imágenes sin fondo ya aportadas para hero y categorías.
**Referencia visual:** `docs/diseno/inicio-referencia.png` (1536 × 1024).
**Carpeta real de categorías:** `docs/seis_categorías/`, con tilde; no existe una carpeta raíz
`/seis_categorias` en este espacio. Las rutas siguientes son relativas al proyecto.

## Fuentes y destinos de implementación

Se usan tres fotografías del hero y seis fotografías específicas de categorías: **nueve archivos
de comida distintos**, más el logo. Todos existen. Los PNG de comida seleccionados son RGBA;
se comprobaron píxeles transparentes en las esquinas. Esto no sustituye la revisión de halos.

| Uso | Fuente existente | Destino previsto bajo frontend/src/assets/ | Dimensiones |
| --- | --- | --- | --- |
| Logo | `docs/diseno/logo-mikuna.png` | `branding/logo-mikuna.png` | 2146 × 732 |
| Alitas del hero | `docs/diseno/Plato de alitas BBQ y dip-1.png` | `home/wings.png` | 1536 × 1024 |
| Pizza del hero | `docs/diseno/Pizza redonda de pepperoni dorada-3.png` | `home/pizza-whole.png` | 1254 × 1254 |
| Hamburguesa del hero | `docs/diseno/Hamburguesa gourmet con queso fundido.png` | `home/burger.png` | 1254 × 1254 |
| Hamburguesas | `docs/seis_categorías/Hamburguesa clásica con sésamo-1.png` | `home/category-burger.png` | 1254 × 1254 |
| Pollo | `docs/seis_categorías/Pila de pollo frito crujiente-2.png` | `home/chicken.png` | 1254 × 1254 |
| Pizza | `docs/seis_categorías/Porción de pizza dorada con pepperoni-2.png` | `home/pizza-slice.png` | 1254 × 1254 |
| Comida peruana | `docs/seis_categorías/Plato de ceviche peruano-3.png` | `home/peruvian-food.png` | 1254 × 1254 |
| Postres | `docs/seis_categorías/Rebanada de pastel de chocolate.png` | `home/dessert.png` | 1254 × 1254 |
| Bebidas | `docs/seis_categorías/Vaso de maracuyá con fruta-3.png` | `home/drink.png` | 1254 × 1254 |

El archivo adicional `docs/seis_categorías/Plato de alitas con salsa-1.png` es una alternativa
existente; no añade una categoría ni sustituye por defecto la imagen principal indicada arriba.
No hay tareas de generación de imágenes nuevas. Si un recurso necesita corrección visual, se
registra el problema antes de alterar la fuente; los originales siempre se conservan.

## Copia e integridad

Durante la implementación se copian los diez recursos a los destinos indicados, sin mover,
renombrar ni modificar los originales. Se usan rutas literales para nombres con espacios y tildes.
El módulo `frontend/src/features/home/home-assets.ts` registra por recurso identificador, import,
dimensiones naturales, descripción accesible, ruta de origen y huella SHA-256 de 64 caracteres
hexadecimales. Cada copia debe coincidir con la huella de su original.

La hamburguesa del hero y la de categoría se mantienen distintas. El manifiesto contiene nueve
recursos de comida y un logo; no incorpora la referencia completa como recurso de presentación.
El logo conserva transparencia, colores y proporciones originales.

## Composición y consumo

Los componentes importan archivos locales para que Vite los incluya en el build. La publicación
no depende de servir `docs/`, de URLs de demostración ni de una generación durante la visita.
Texto promocional, botones y etiquetas son contenido real separado de los bitmaps.

El fondo melocotón, formas redondeadas, bordes blancos y sombras se dibujan en la interfaz con la
paleta exacta. La vajilla blanca pertenece a la fotografía; no se recolorea para simular fondos.
La composición del hero contiene alitas dominantes, pizza circular arriba y hamburguesa abajo;
las categorías usan sus seis archivos en el orden de la especificación.

Cada imagen reserva su proporción y tiene alternativa accesible. Cuando falle una carga, permanecen
los títulos, etiquetas, orden de lectura y controles. Las fotografías no pueden tapar texto o
acciones. Se revisa nitidez y contorno sobre blanco, marfil y melocotón, sin declarar aceptable
un rectángulo opaco solo porque el archivo contiene canal alfa.

## Condiciones de aceptación

| Condición | Evidencia |
| --- | --- |
| Se utilizan los nueve alimentos asignados y el logo. | Manifiesto y revisión en las seis ventanas. |
| No se alteran los originales ni sus copias previstas. | Huellas SHA-256 coincidentes. |
| No hay halos o fondos rectangulares que alteren la paleta. | Inspección sobre tres fondos y revisión del diseño. |
| Logo completo, sin recolorear ni deformar. | Huella original y revisión de proporción. |
| Se mantienen sujetos y composición de la referencia. | V-01 a V-10, imágenes independientes y textos reales. |
| El build incluye todos los recursos. | Carga del artefacto sin solicitudes fallidas. |

Las tareas deben verificar y copiar estos recursos existentes. La presencia de archivos no equivale
a una revisión visual aprobada ni autoriza añadir secciones o categorías fuera del alcance.
