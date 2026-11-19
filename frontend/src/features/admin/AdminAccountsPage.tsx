import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { accountDetail, listAccounts, toggleAccount, type AccountDetail, type AccountSummary, type Page } from './admin-api'
import {
  accessLabel,
  actionLabel,
  emptyAccountFilters,
  formatDateTime,
  operationLabel,
  pageSummary,
  registrationLabels,
  roleLabels,
  tabLabels,
  toggleInfo,
  type AccountFilters,
  type AccountTab,
} from './admin-logic'
import '../restaurant/restaurant.css'
import './admin.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: Page<AccountSummary> }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const TABS: AccountTab[] = ['users', 'restaurants', 'couriers']

/** Gestión de cuentas (RF-32): lista, busca, consulta el detalle y activa o desactiva usuarios, restaurantes y repartidores. */
export function AdminAccountsPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [tab, setTab] = useState<AccountTab>('users')
  const [draft, setDraft] = useState<AccountFilters>(emptyAccountFilters)
  const [filters, setFilters] = useState<AccountFilters>(emptyAccountFilters)
  const [page, setPage] = useState(0)
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<{ tab: AccountTab; id: string } | null>(null)
  const [detail, setDetail] = useState<AccountDetail | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const token = session?.token ?? ''

  const fail = useCallback((error: unknown): string => {
    if (error instanceof ApiError && error.status === 401) {
      clearSession()
      navigate('/iniciar-sesion', { replace: true })
      return 'Tu sesión venció'
    }
    return error instanceof ApiError ? error.message : GENERIC_ERROR
  }, [navigate])

  useEffect(() => {
    if (!token) return
    let active = true
    listAccounts(token, tab, filters, page)
      .then((data) => { if (active) setView({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: fail(error) })
      })
    return () => { active = false }
  }, [token, tab, filters, page, attempt, fail])

  useEffect(() => {
    if (!token || !selected) return
    let active = true
    accountDetail(token, selected.tab, selected.id)
      .then((value) => { if (active) setDetail(value) })
      .catch((error: unknown) => { if (active) setNotice(fail(error)) })
    return () => { active = false }
  }, [token, selected, attempt, fail])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  function changeTab(next: AccountTab) {
    setTab(next)
    setDraft(emptyAccountFilters)
    setFilters(emptyAccountFilters)
    setPage(0)
    setSelected(null)
    setNotice(null)
    setView({ kind: 'loading' })
  }

  function search(event: FormEvent) {
    event.preventDefault()
    setFilters(draft)
    setPage(0)
    setSelected(null)
    setView({ kind: 'loading' })
  }

  async function toggle(target: AccountTab, id: string, enabledNow: boolean) {
    if (busy) return
    if (!window.confirm(toggleInfo(target, enabledNow).confirm)) return
    setBusy(true)
    setNotice(null)
    try {
      const result = await toggleAccount(token, target, id, !enabledNow)
      setDetail(result)
      setNotice('Cambio aplicado y registrado.')
    } catch (error) {
      setNotice(fail(error))
    } finally {
      setBusy(false)
      setAttempt((n) => n + 1)
    }
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Cuentas de la plataforma">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Cuentas de la plataforma" description="Consulta y gestiona usuarios, restaurantes y repartidores. Las cuentas no se eliminan: se desactivan.">
      <div className="admin-tabs" role="tablist" aria-label="Tipo de cuenta">
        {TABS.map((id) => (
          <button key={id} type="button" role="tab" className="admin-tab" aria-selected={tab === id} onClick={() => changeTab(id)}>
            {tabLabels[id]}
          </button>
        ))}
      </div>

      <form className="admin-filters" onSubmit={search} role="search">
        <label>Buscar por nombre o correo
          <input type="search" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} maxLength={100} />
        </label>
        {tab === 'users' && (
          <>
            <label>Tipo
              <select value={draft.role} onChange={(e) => setDraft({ ...draft, role: e.target.value as AccountFilters['role'] })}>
                <option value="">Todos</option>
                {Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <label>Acceso
              <select value={draft.access} onChange={(e) => setDraft({ ...draft, access: e.target.value as AccountFilters['access'] })}>
                <option value="">Todos</option>
                <option value="ENABLED">Habilitado</option>
                <option value="DISABLED">Bloqueado</option>
              </select>
            </label>
          </>
        )}
        {tab !== 'users' && (
          <label>Operación
            <select value={draft.operation} onChange={(e) => setDraft({ ...draft, operation: e.target.value as AccountFilters['operation'] })}>
              <option value="">Todas</option>
              <option value="ACTIVE">Activos</option>
              <option value="INACTIVE">Desactivados</option>
            </select>
          </label>
        )}
        {tab === 'restaurants' && (
          <label>Registro
            <select value={draft.registration} onChange={(e) => setDraft({ ...draft, registration: e.target.value as AccountFilters['registration'] })}>
              <option value="">Todos</option>
              {Object.entries(registrationLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
        )}
        <button type="submit" className="auth-submit">Buscar</button>
      </form>

      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{view.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setView({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
        </>
      )}
      {view.kind === 'ready' && view.data.items.length === 0 && (
        <p className="auth-description" role="status">No hay cuentas que coincidan con la búsqueda.</p>
      )}
      {view.kind === 'ready' && view.data.items.length > 0 && (
        <ul className="restaurant-list">
          {view.data.items.map((item) => {
            const open = selected?.tab === tab && selected.id === item.id
            const operation = operationLabel(item.operationEnabled, tab)
            return (
              <li key={item.id} className="restaurant-card">
                <h2>{item.name}</h2>
                <dl>
                  {item.email && <div><dt>Correo</dt><dd>{item.email}</dd></div>}
                  {tab === 'users' && <div><dt>Tipo</dt><dd>{roleLabels[item.role]}</dd></div>}
                  <div>
                    <dt>Acceso</dt>
                    <dd><span className={item.accessEnabled ? 'admin-badge' : 'admin-badge admin-badge-off'}>{accessLabel(item.accessEnabled)}</span></dd>
                  </div>
                  {operation && (
                    <div>
                      <dt>Operación</dt>
                      <dd><span className={item.operationEnabled ? 'admin-badge' : 'admin-badge admin-badge-off'}>{operation}</span></dd>
                    </div>
                  )}
                  {item.registrationStatus && <div><dt>Registro</dt><dd>{registrationLabels[item.registrationStatus]}</dd></div>}
                  <div><dt>Registrado</dt><dd>{formatDateTime(item.createdAt)}</dd></div>
                </dl>
                <div className="restaurant-actions">
                  <button type="button" className="auth-submit auth-submit-secondary" aria-expanded={open}
                    onClick={() => setSelected(open ? null : { tab, id: item.id })}>
                    {open ? 'Ocultar detalle' : 'Ver detalle'}
                  </button>
                </div>
                {open && detail && detail.account.id === item.id && (
                  <DetailPanel tab={tab} detail={detail} busy={busy}
                    onToggle={(target, id, enabledNow) => void toggle(target, id, enabledNow)}
                    onOpenOwner={() => setSelected({ tab: 'users', id: detail.account.accountId })} />
                )}
              </li>
            )
          })}
        </ul>
      )}

      {view.kind === 'ready' && (
        <div className="admin-pager">
          <span role="status">{pageSummary(view.data.page, view.data.totalPages, view.data.totalItems)}</span>
          <div className="restaurant-actions">
            <button type="button" className="auth-submit auth-submit-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
            <button type="button" className="auth-submit auth-submit-secondary" disabled={page + 1 >= view.data.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
          </div>
        </div>
      )}
      {selected?.tab === 'users' && tab !== 'users' && detail && detail.account.id === selected.id && (
        <section className="restaurant-card" aria-label="Cuenta del titular">
          <h2>Cuenta del titular: {detail.account.name}</h2>
          <DetailPanel tab="users" detail={detail} busy={busy}
            onToggle={(target, id, enabledNow) => void toggle(target, id, enabledNow)} />
        </section>
      )}
      <Link to="/admin/pedidos" className="auth-switch">Ver pedidos y entregas</Link>
      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}

interface PanelProps {
  tab: AccountTab
  detail: AccountDetail
  busy: boolean
  onToggle: (tab: AccountTab, id: string, enabledNow: boolean) => void
  onOpenOwner?: () => void
}

function DetailPanel({ tab, detail, busy, onToggle, onOpenOwner }: PanelProps) {
  const { account } = detail
  // En usuarios cambia el acceso; en restaurantes y repartidores, la operación.
  const enabledNow = tab === 'users' ? account.accessEnabled : account.operationEnabled !== false
  const info = toggleInfo(tab, enabledNow)
  return (
    <div className="admin-detail">
      {detail.restaurant && (
        <p>{detail.restaurant.category} · {detail.restaurant.address}, {detail.restaurant.zone} · {detail.restaurant.phone}
          {' '}· {detail.restaurant.available ? 'Disponible' : 'En pausa'}</p>
      )}
      {detail.courier && <p>{detail.courier.available ? 'Disponible para entregas' : 'No disponible para entregas'}</p>}
      <div className="restaurant-actions">
        <button type="button" className="auth-submit" disabled={busy} onClick={() => onToggle(tab, account.id, enabledNow)}>{info.button}</button>
        {tab !== 'users' && onOpenOwner && (
          <button type="button" className="auth-submit auth-submit-secondary" onClick={onOpenOwner}>Ver cuenta del titular</button>
        )}
      </div>
      <h3>Acciones del administrador</h3>
      {detail.actions.length === 0 ? <p className="auth-description">Sin acciones registradas.</p> : (
        <ul className="admin-log">
          {detail.actions.map((action, index) => (
            <li key={index}>
              {actionLabel(action.action, action.scope)} · {formatDateTime(action.createdAt)} · {action.adminName ?? 'Administrador'}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
