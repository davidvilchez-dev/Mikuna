import { Link } from 'react-router'
import { homeAssets } from '../home/home-assets'
import { AddressSearch } from '../home/components/AddressSearch'
import { FoodImage } from '../home/components/FoodImage'
import { PublicIcon } from './components/PublicIcon'
import { publicPhotos } from './public-content'
import './how-it-works.css'

const faqs = [
  { q: '¿Necesito una cuenta para ver restaurantes?', a: 'No. Puedes explorar restaurantes y sus cartas sin iniciar sesión. La cuenta solo la necesitas para confirmar tu pedido.' },
  { q: '¿Cómo pago mi pedido?', a: 'Con tarjeta, de forma segura, en el último paso antes de confirmar.' },
  { q: '¿Puedo cancelar un pedido?', a: 'Sí, mientras siga pendiente, es decir, antes de que el restaurante lo acepte.' },
  { q: '¿Cómo sé por dónde va mi pedido?', a: 'En el detalle del pedido ves cada cambio de estado y, durante la entrega, la ubicación del repartidor.' },
  { q: '¿Puedo calificar mi pedido?', a: 'Sí. Cuando lo recibes puedes calificarlo de 1 a 5 y ayudar a otros a elegir.' },
] as const

function StepSearch() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <div className="hiw-mock-search"><PublicIcon name="search" /> pollo a la brasa</div>
      {([['chicken', 'Pollería del barrio', 'Pollos · Centro'], ['pizzaSlice', 'Pizzería artesanal', 'Pizzas · San Juan Bautista']] as const).map(([img, name, meta]) => (
        <div key={name} className="hiw-mock-row">
          <span className="hiw-thumb"><FoodImage asset={homeAssets[img]} /></span>
          <span className="hiw-mock-text"><strong>{name}</strong><small>{meta}</small></span>
          <span className="hiw-badge">Abierto</span>
        </div>
      ))}
    </div>
  )
}

function StepCart() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Tu carrito</p>
      {([['categoryBurger', 'Hamburguesa clásica', 'S/ 18.00', 2], ['drink', 'Jugo de maracuyá', 'S/ 7.00', 1]] as const).map(([img, name, price, qty]) => (
        <div key={name} className="hiw-mock-row">
          <span className="hiw-thumb"><FoodImage asset={homeAssets[img]} /></span>
          <span className="hiw-mock-text"><strong>{name}</strong><small>{price}</small></span>
          <span className="hiw-stepper"><span>−</span>{qty}<span>+</span></span>
        </div>
      ))}
      <div className="hiw-mock-total"><span>Subtotal</span><strong>S/ 43.00</strong></div>
    </div>
  )
}

function StepPay() {
  return (
    <div className="hiw-mock" aria-hidden="true">
      <div className="hiw-mock-field"><PublicIcon name="pin" /><span><small>Entregar en</small><strong>Tu dirección guardada</strong></span></div>
      <div className="hiw-mock-field"><span className="hiw-card-chip" /><span><small>Pagar con</small><strong>Tarjeta •••• 4242</strong></span></div>
      <div className="hiw-mock-button">Confirmar pedido</div>
    </div>
  )
}

function StepTrack() {
  const states = [['Aceptado', 'done'], ['En preparación', 'done'], ['En camino', 'now'], ['Entregado', 'next']] as const
  return (
    <div className="hiw-mock" aria-hidden="true">
      <p className="hiw-mock-title">Pedido en camino</p>
      <ol className="hiw-track">
        {states.map(([label, state]) => <li key={label} data-state={state}>{label}</li>)}
      </ol>
      <div className="hiw-rate"><span>Califica tu pedido</span><span className="hiw-stars">★★★★★</span></div>
    </div>
  )
}

const steps = [
  { title: 'Elige tu restaurante', text: 'Busca por nombre, categoría o zona y mira qué restaurantes están abiertos y llegan a tu dirección.', mock: <StepSearch /> },
  { title: 'Arma tu pedido', text: 'Agrega tus platos al carrito, ajusta cantidades y revisa el subtotal antes de seguir.', mock: <StepCart /> },
  { title: 'Confirma y paga', text: 'Elige dónde lo recibes, paga con tarjeta y confirma. El restaurante recibe tu pedido al instante.', mock: <StepPay /> },
  { title: 'Síguelo hasta tu puerta', text: 'Ve cada cambio de estado y la ubicación del repartidor. Al recibirlo, califica tu experiencia.', mock: <StepTrack /> },
] as const

export function HowItWorksPage() {
  return (
    <div className="hiw">
      <header className="public-shell hiw-hero">
        <div className="hiw-hero-copy">
          <p className="hiw-eyebrow">Cómo funciona</p>
          <h2 id="public-title-customer">De tu antojo <span>a tu puerta.</span></h2>
          <p>Pedir en Mikuna toma cuatro pasos. Sin llamadas, sin esperar en línea y sabiendo siempre por dónde va tu comida.</p>
          <div className="hiw-hero-actions">
            <Link className="public-button" to="/restaurantes">Ver restaurantes</Link>
            <Link className="public-text-button" to="/registro">Crear cuenta</Link>
          </div>
        </div>
        <figure className="hiw-hero-photo">
          <img src={publicPhotos.customer.src} alt={publicPhotos.customer.alt} width="1672" height="941" fetchPriority="high" decoding="async" />
          <figcaption className="hiw-float" aria-hidden="true">
            <span className="hiw-float-icon"><PublicIcon name="scooter" /></span>
            <span><strong>Tu pedido va en camino</strong><small>El repartidor ya salió del restaurante</small></span>
          </figcaption>
        </figure>
      </header>

      <section className="hiw-steps" aria-labelledby="hiw-steps-title">
        <div className="public-shell">
          <h3 id="hiw-steps-title" className="hiw-section-title">Así de fácil</h3>
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

      <section className="public-shell hiw-faq" aria-labelledby="hiw-faq-title">
        <div>
          <h3 id="hiw-faq-title" className="hiw-section-title">Lo que conviene saber</h3>
          <p className="hiw-faq-lead">Si te queda alguna duda antes de tu primer pedido, empieza por aquí.</p>
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

      <section className="public-shell hiw-close" aria-labelledby="hiw-close-title">
        <div className="hiw-close-copy">
          <h3 id="hiw-close-title">¿Ya se te antojó algo?</h3>
          <p>Escribe tu dirección y mira qué restaurantes llegan hasta ti.</p>
          <AddressSearch />
        </div>
        <div className="hiw-close-food" aria-hidden="true">
          <FoodImage asset={homeAssets.pizzaSlice} className="hiw-close-pizza" />
          <FoodImage asset={homeAssets.categoryBurger} className="hiw-close-burger" />
          <FoodImage asset={homeAssets.drink} className="hiw-close-drink" />
        </div>
      </section>
    </div>
  )
}
