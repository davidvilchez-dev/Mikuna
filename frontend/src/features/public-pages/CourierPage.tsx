import { Link } from 'react-router'
import { PublicIcon } from './components/PublicIcon'
import { publicPhotos } from './public-content'
import './how-it-works.css'
import './partners.css'

const faqs = [
  { q: '¿Qué necesito para empezar?', a: 'Crear tu cuenta de repartidor en Mikuna. Desde tu panel gestionas tu disponibilidad y tus entregas.' },
  { q: '¿Puedo elegir cuándo trabajar?', a: 'Sí. Activas tu disponibilidad cuando quieras recibir entregas y la apagas cuando terminas.' },
  { q: '¿Puedo rechazar una entrega?', a: 'Sí. Si no puedes tomar una entrega asignada, la rechazas e indicas el motivo si lo deseas.' },
  { q: '¿Qué ve el cliente durante la entrega?', a: 'El estado del pedido y tu ubicación mientras llevas la entrega, para que sepa cuándo llegas.' },
] as const

function AvailabilityMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Mis entregas</p>
      <div className="pt-available"><span><strong>Estoy disponible</strong><small>Recibirás nuevas entregas</small></span><span className="pt-switch" data-on="true"><span /></span></div>
    </div>
  )
}

function AssignedMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <div className="pt-order-head"><p className="hiw-mock-title">Entrega asignada</p><span className="pt-chip">Nueva</span></div>
      <div className="hiw-mock-field"><PublicIcon name="store" /><span><small>Recoger en</small><strong>Pollería del barrio</strong></span></div>
      <div className="hiw-mock-field"><PublicIcon name="pin" /><span><small>Entregar en</small><strong>Dirección del cliente</strong></span></div>
      <div className="pt-actions"><span className="pt-btn pt-btn-ghost">Rechazar</span><span className="pt-btn">Recogí el pedido</span></div>
    </div>
  )
}

function DeliverMock() {
  const states = [['Asignada', 'done'], ['Recogida', 'done'], ['Entregada', 'now']] as const
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">En camino al cliente</p>
      <ol className="hiw-track">{states.map(([label, state]) => <li key={label} data-state={state}>{label}</li>)}</ol>
      <div className="pt-live"><span className="pt-live-dot" />Compartiendo tu ubicación</div>
    </div>
  )
}

function HistoryMock() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Historial</p>
      {([['#2210', 'Entregada'], ['#2207', 'Entregada'], ['#2201', 'Entregada']] as const).map(([id, state]) => (
        <div key={id} className="pt-history-row"><strong>{id}</strong><span data-state="Entregado">{state}</span><span className="pt-history-check">✓</span></div>
      ))}
    </div>
  )
}

const steps = [
  { title: 'Activa tu disponibilidad', text: 'Enciéndela cuando quieras trabajar. Mientras esté activa, puedes recibir entregas.', mock: <AvailabilityMock /> },
  { title: 'Recibe tu entrega', text: 'Ves dónde recoger y dónde entregar. Si no puedes tomarla, la rechazas.', mock: <AssignedMock /> },
  { title: 'Recoge y entrega', text: 'Marca cada avance. Tu ubicación se comparte con el cliente solo mientras llevas su pedido.', mock: <DeliverMock /> },
  { title: 'Revisa tu historial', text: 'Todas tus entregas realizadas quedan registradas en tu panel.', mock: <HistoryMock /> },
] as const

export function CourierPage() {
  return (
    <div className="hiw">
      <header className="public-shell hiw-hero">
        <div className="hiw-hero-copy">
          <p className="hiw-eyebrow">Para repartidores</p>
          <h2 id="public-title-courier">Tu próxima entrega <span>empieza aquí.</span></h2>
          <p>Conecta con los pedidos de tu ciudad, trabaja cuando tú decidas y gestiona cada entrega desde tu celular.</p>
          <div className="hiw-hero-actions">
            <Link className="public-button" to="/registro?tipo=repartidor">Quiero ser repartidor</Link>
            <Link className="public-text-button" to="/iniciar-sesion">Ya tengo cuenta</Link>
          </div>
        </div>
        <figure className="hiw-hero-photo">
          <img src={publicPhotos.courier.src} alt={publicPhotos.courier.alt} width="1672" height="941" fetchPriority="high" decoding="async" />
          <figcaption className="hiw-float" aria-hidden="true">
            <span className="hiw-float-icon"><PublicIcon name="scooter" /></span>
            <span><strong>Entrega asignada</strong><small>Recoge en el restaurante y lleva el pedido</small></span>
          </figcaption>
        </figure>
      </header>

      <section className="hiw-steps" aria-labelledby="pt-steps-title">
        <div className="public-shell">
          <h3 id="pt-steps-title" className="hiw-section-title">Así es una entrega</h3>
          <ol className="hiw-route">
            {steps.map((step, index) => (
              <li key={step.title} className="hiw-step">
                <span className="hiw-marker" aria-hidden="true">{index + 1}</span>
                <div className="hiw-step-copy">
                  <span className="hiw-step-label">Paso {index + 1}</span>
                  <h4>{step.title}</h4>
                  <p>{step.text}</p>
                </div>
                <div className="hiw-step-visual">{step.mock}</div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="public-shell hiw-faq" aria-labelledby="pt-courier-faq">
        <div>
          <h3 id="pt-courier-faq" className="hiw-section-title">Preguntas de repartidores</h3>
          <p className="hiw-faq-lead">Lo básico antes de tu primera entrega.</p>
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

      <section className="public-shell pt-close" aria-labelledby="pt-courier-close">
        <div className="pt-close-copy">
          <h3 id="pt-courier-close">Tu ciudad. Tu recorrido.</h3>
          <p>Acerca las cocinas de Ayacucho a quienes las esperan.</p>
          <Link className="public-button" to="/registro?tipo=repartidor">Quiero sumarme</Link>
        </div>
        <div className="pt-route" aria-hidden="true">
          <span className="pt-route-point"><PublicIcon name="store" /><small>Restaurante</small></span>
          <span className="pt-route-line" />
          <span className="pt-route-scooter"><PublicIcon name="scooter" /></span>
          <span className="pt-route-line" />
          <span className="pt-route-point"><PublicIcon name="pin" /><small>Cliente</small></span>
        </div>
      </section>
    </div>
  )
}
