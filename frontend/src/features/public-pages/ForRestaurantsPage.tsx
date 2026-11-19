import { Link } from 'react-router'
import { homeAssets } from '../home/home-assets'
import { FoodImage } from '../home/components/FoodImage'
import { PublicIcon } from './components/PublicIcon'
import { publicPhotos } from './public-content'
import cevicheImage from '../../assets/public-pages/restaurant-ceviche.png'
import chickenImage from '../../assets/public-pages/restaurant-chicken.png'
import './how-it-works.css'
import './partners.css'

const faqs = [
  { q: '¿Qué necesito para registrar mi restaurante?', a: 'Crear tu cuenta y enviar la solicitud con los datos de tu local. Mikuna la revisa antes de habilitarte.' },
  { q: '¿Qué pasa si rechazan mi solicitud?', a: 'Verás el motivo en tu panel. Puedes corregir tus datos y volver a enviarla.' },
  { q: '¿Puedo pausar un plato que se terminó?', a: 'Sí. Cada producto tiene su disponibilidad y puedes apagarlo o encenderlo cuando quieras.' },
  { q: '¿Puedo rechazar un pedido?', a: 'Sí. Puedes aceptar o rechazar cada pedido nuevo e indicar el motivo si lo deseas.' },
  { q: '¿Quién entrega los pedidos?', a: 'Los repartidores de Mikuna. Cuando el pedido está listo, se asigna a un repartidor disponible.' },
] as const

function MenuMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Tu carta</p>
      {([['peruvianFood', 'Ceviche clásico', 'S/ 28.00', true], ['dessert', 'Torta de chocolate', 'S/ 12.00', false]] as const).map(([img, name, price, on]) => (
        <div key={name} className="hiw-mock-row">
          <span className="hiw-thumb"><FoodImage asset={homeAssets[img]} /></span>
          <span className="hiw-mock-text"><strong>{name}</strong><small>{price}</small></span>
          <span className="pt-switch" data-on={on}><span /></span>
        </div>
      ))}
    </div>
  )
}

function OrdersMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <div className="pt-order-head"><p className="hiw-mock-title">Pedido nuevo</p><span className="pt-chip">Pendiente</span></div>
      <p className="pt-order-items">2 × Pollo a la brasa · 1 × Chicha morada</p>
      <div className="pt-actions"><span className="pt-btn pt-btn-ghost">Rechazar</span><span className="pt-btn">Aceptar</span></div>
    </div>
  )
}

function HistoryMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Historial</p>
      {([['#1042', 'Entregado', '★★★★★'], ['#1041', 'Entregado', '★★★★☆'], ['#1040', 'Cancelado', '']] as const).map(([id, state, stars]) => (
        <div key={id} className="pt-history-row">
          <strong>{id}</strong><span data-state={state}>{state}</span><span className="hiw-stars">{stars}</span>
        </div>
      ))}
    </div>
  )
}

const tools = [
  { title: 'Tu carta, siempre al día', text: 'Agrega platos, cambia precios y apaga lo que se terminó sin llamar a nadie.', mock: <MenuMock /> },
  { title: 'Pedidos en tiempo real', text: 'Recibe cada pedido, acéptalo o recházalo y avisa cuando entra en preparación.', mock: <OrdersMock /> },
  { title: 'Historial y calificaciones', text: 'Revisa los pedidos de tu restaurante y lo que opinan tus clientes.', mock: <HistoryMock /> },
] as const

export function ForRestaurantsPage() {
  return (
    <div className="hiw">
      <header className="public-shell hiw-hero">
        <div className="hiw-hero-copy">
          <p className="hiw-eyebrow">Para restaurantes</p>
          <h2 id="public-title-restaurant">Tu cocina, <span>a más mesas.</span></h2>
          <p>Lleva el sabor de tu restaurante a más personas de Ayacucho. Tú cocinas; Mikuna te trae los pedidos y se encarga del reparto.</p>
          <div className="hiw-hero-actions">
            <Link className="public-button" to="/registro?tipo=restaurante">Registrar mi restaurante</Link>
            <Link className="public-text-button" to="/iniciar-sesion">Ya tengo cuenta</Link>
          </div>
        </div>
        <figure className="hiw-hero-photo">
          <img src={publicPhotos.chef.src} alt={publicPhotos.chef.alt} width="1672" height="941" fetchPriority="high" decoding="async" />
          <figcaption className="hiw-float" aria-hidden="true">
            <span className="hiw-float-icon"><PublicIcon name="bag" /></span>
            <span><strong>Nuevo pedido recibido</strong><small>3 productos · listo para aceptar</small></span>
          </figcaption>
        </figure>
      </header>

      <section className="pt-tools" aria-labelledby="pt-tools-title">
        <div className="public-shell">
          <h3 id="pt-tools-title" className="hiw-section-title">Todo desde tu panel</h3>
          <p className="pt-lead">Un solo lugar para tu carta, tus pedidos y tu historial.</p>
          <ul className="pt-tool-grid">
            {tools.map((tool) => (
              <li key={tool.title} className="pt-tool">
                <div className="pt-tool-visual">{tool.mock}</div>
                <h4>{tool.title}</h4>
                <p>{tool.text}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="public-shell pt-onboard" aria-labelledby="pt-onboard-title">
        <h3 id="pt-onboard-title" className="hiw-section-title">Empieza en tres pasos</h3>
        <ol className="pt-onboard-steps">
          <li><span className="hiw-marker" aria-hidden="true">1</span><h4>Envía tu solicitud</h4><p>Crea tu cuenta y completa los datos de tu restaurante.</p></li>
          <li><span className="hiw-marker" aria-hidden="true">2</span><h4>Mikuna la revisa</h4><p>Validamos tus datos. Si falta algo, verás el motivo y podrás corregirlo.</p></li>
          <li><span className="hiw-marker" aria-hidden="true">3</span><h4>Publica tu carta</h4><p>Una vez aprobado, sube tus platos y empieza a recibir pedidos.</p></li>
        </ol>
      </section>

      <section className="public-shell hiw-faq" aria-labelledby="pt-faq-title">
        <div>
          <h3 id="pt-faq-title" className="hiw-section-title">Preguntas de restaurantes</h3>
          <p className="hiw-faq-lead">Lo que más nos preguntan antes de sumarse a Mikuna.</p>
        </div>
        <div className="hiw-faq-list">
          {faqs.map((item) => (
            <details key={item.q}>
              <summary>{item.q}<span aria-hidden="true" className="hiw-faq-sign" /></summary>
              <p>{item.a}</p>
            </details>
          ))}
        </div>
      </section>

      <section className="public-shell pt-close" aria-labelledby="pt-close-title">
        <div className="pt-close-copy">
          <h3 id="pt-close-title">Tu próximo cliente ya tiene hambre.</h3>
          <p>Registra tu restaurante hoy y empieza a recibir pedidos cuando te aprobemos.</p>
          <Link className="public-button" to="/registro?tipo=restaurante">Registrar mi restaurante</Link>
        </div>
        <div className="pt-close-photos" aria-hidden="true">
          <img src={cevicheImage} alt="" width="1672" height="941" loading="lazy" />
          <img src={chickenImage} alt="" width="1672" height="941" loading="lazy" />
        </div>
      </section>
    </div>
  )
}
