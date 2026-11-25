import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { accountDetail, listAccounts, toggleAccount, type AccountDetail, type AccountSummary, type Page } from './admin-api'
import { AdminLayout } from './AdminLayout'
import { useConfirm } from '../ui/useConfirm'
import { Badge, EmptyState, ErrorState, LoadingBlock, Pager } from './admin-ui'
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

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: Page<AccountSummary> }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const TABS: AccountTab[] = ['users', 'restaurants', 'couriers']

/** Gestión de cuentas (RF-32): lista, busca, consulta el detalle y activa o desactiva usuarios, restaurantes y repartidores. */
export function AdminAccountsPage() {
  const session = useSession()
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
  const [confirmDialog, confirm] = useConfirm()

  const token = session.token

  useEffect(() => {
    let active = true
    listAccounts(token, tab, filters, page)
      .then((data) => { if (active) setView({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [token, tab, filters, page, attempt])

  useEffect(() => {
    if (!token || !selected) return
    let active = true
    accountDetail(token, selected.tab, selected.id)
      .then((value) => { if (active) setDetail(value) })
      .catch((error: unknown) => { if (active) setNotice(errorMessage(error)) })
    return () => { active = false }
  }, [token, selected, attempt])


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
    const info = toggleInfo(target, enabledNow)
    const ok = await confirm({ title: `¿${info.button}?`, message: info.confirm, confirmLabel: info.button, danger: enabledNow })
    if (!ok) return
    setBusy(true)
    setNotice(null)
    try {
      const result = await toggleAccount(token, target, id, !enabledNow)
      setDetail(result)
      setNotice('Cambio aplicado y registrado.')
    } catch (error) {
      setNotice(errorMessage(error))
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

  const reload = () => { setView({ kind: 'loading' }); setAttempt((n) => n + 1) }
  const columns = tab === 'users' ? 5 : tab === 'restaurants' ? 6 : 5

  return (
    <AdminLayout title="Cuentas" description="Consulta y gestiona usuarios, restaurantes y repartidores. Las cuentas no se eliminan: se desactivan.">
      <div className="adm-seg" role="tablist" aria-label="Tipo de cuenta">
        {TABS.map((id) => (
          <button key={id} type="button" role="tab" aria-selected={tab === id} onClick={() => changeTab(id)}>
            {tabLabels[id]}
          </button>
        ))}
      </div>

      <form className="adm-toolbar" onSubmit={search} role="search">
        <label className="adm-grow">Buscar por nombre o correo
          <input type="search" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} maxLength={100} placeholder="Escribe para buscar" />
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
        <button type="submit" className="adm-btn">Buscar</button>
      </form>

      {notice && <p className="adm-note" role="status">{notice}</p>}
      {view.kind === 'loading' && <LoadingBlock />}
      {view.kind === 'error' && <ErrorState message={view.message} onRetry={reload} />}
      {view.kind === 'ready' && view.data.items.length === 0 && (
        <EmptyState title="Sin resultados" text="No hay cuentas que coincidan con la búsqueda." />
      )}
      {view.kind === 'ready' && view.data.items.length > 0 && (
        <div className="adm-table-wrap">
          <table className="adm-table">
            <thead>
              <tr>
                <th scope="col">{tab === 'users' ? 'Persona' : tab === 'restaurants' ? 'Restaurante' : 'Repartidor'}</th>
                {tab === 'users' && <th scope="col">Tipo</th>}
                <th scope="col">Acceso</th>
                {tab !== 'users' && <th scope="col">Operación</th>}
                {tab === 'restaurants' && <th scope="col">Registro</th>}
                <th scope="col">Registrado</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {view.data.items.map((item) => {
                const open = selected?.tab === tab && selected.id === item.id
                const operation = operationLabel(item.operationEnabled, tab)
                return (
                  <Fragment key={item.id}>
                    <tr className={open ? 'adm-row-open' : undefined}>
                      <td>
                        <div className="adm-cell-main"><strong>{item.name}</strong>{item.email && <span>{item.email}</span>}</div>
                      </td>
                      {tab === 'users' && <td>{roleLabels[item.role]}</td>}
                      <td><Badge tone={item.accessEnabled ? 'ok' : 'bad'}>{accessLabel(item.accessEnabled)}</Badge></td>
                      {tab !== 'users' && (
                        <td>{operation ? <Badge tone={item.operationEnabled ? 'ok' : 'bad'}>{operation}</Badge> : '—'}</td>
                      )}
                      {tab === 'restaurants' && (
                        <td>
                          {item.registrationStatus
                            ? <Badge tone={item.registrationStatus === 'APPROVED' ? 'ok' : item.registrationStatus === 'REJECTED' ? 'bad' : 'warn'}>{registrationLabels[item.registrationStatus]}</Badge>
                            : '—'}
                        </td>
                      )}
                      <td>{formatDateTime(item.createdAt)}</td>
                      <td className="adm-actions-cell">
                        <button type="button" className="adm-btn adm-btn-ghost adm-btn-sm" aria-expanded={open}
                          onClick={() => setSelected(open ? null : { tab, id: item.id })}>
                          {open ? 'Ocultar' : 'Ver detalle'}
                        </button>
                      </td>
                    </tr>
                    {open && (
                      <tr>
                        <td colSpan={columns} className="adm-detail-cell">
                          {detail && detail.account.id === item.id
                            ? (
                              <DetailPanel tab={tab} detail={detail} busy={busy}
                                onToggle={(target, id, enabledNow) => void toggle(target, id, enabledNow)}
                                onOpenOwner={() => setSelected({ tab: 'users', id: detail.account.accountId })} />
                            )
                            : <div className="adm-detail"><LoadingBlock /></div>}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {view.kind === 'ready' && (
        <Pager summary={pageSummary(view.data.page, view.data.totalPages, view.data.totalItems)} page={page} totalPages={view.data.totalPages} onChange={setPage} />
      )}
      {selected?.tab === 'users' && tab !== 'users' && detail && detail.account.id === selected.id && (
        <section className="adm-card" aria-label="Cuenta del titular">
          <h2>Cuenta del titular: {detail.account.name}</h2>
          <DetailPanel tab="users" detail={detail} busy={busy}
            onToggle={(target, id, enabledNow) => void toggle(target, id, enabledNow)} />
        </section>
      )}
      {confirmDialog}
    </AdminLayout>
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
    <div className="adm-detail">
      {detail.restaurant && (
        <dl className="adm-facts">
          <div><dt>Categoría</dt><dd>{detail.restaurant.category}</dd></div>
          <div><dt>Dirección</dt><dd>{detail.restaurant.address}, {detail.restaurant.zone}</dd></div>
          <div><dt>Teléfono</dt><dd>{detail.restaurant.phone}</dd></div>
          <div><dt>Disponibilidad</dt><dd>{detail.restaurant.available ? 'Disponible' : 'En pausa'}</dd></div>
        </dl>
      )}
      {detail.courier && (
        <dl className="adm-facts">
          <div><dt>Disponibilidad</dt><dd>{detail.courier.available ? 'Disponible para entregas' : 'No disponible para entregas'}</dd></div>
        </dl>
      )}
      <div className="adm-actions">
        <button type="button" className={enabledNow ? 'adm-btn adm-btn-danger' : 'adm-btn'} disabled={busy} onClick={() => onToggle(tab, account.id, enabledNow)}>{info.button}</button>
        {tab !== 'users' && onOpenOwner && (
          <button type="button" className="adm-btn adm-btn-ghost" onClick={onOpenOwner}>Ver cuenta del titular</button>
        )}
      </div>
      <h3>Acciones del administrador</h3>
      {detail.actions.length === 0 ? <p>Sin acciones registradas.</p> : (
        <ol className="adm-timeline">
          {detail.actions.map((action, index) => (
            <li key={index}>
              <strong>{actionLabel(action.action, action.scope)}</strong>
              <small>{formatDateTime(action.createdAt)} · {action.adminName ?? 'Administrador'}</small>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
