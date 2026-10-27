import { useState } from 'react';
import { api } from '../../api.js';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { formatDateTime } from '../../utils.js';

const ENTITY_TYPES = ['PURCHASE_REQUEST', 'PURCHASE_ORDER', 'QUOTATION', 'SUPPLIER', 'USER', 'CATEGORY', 'SETTING'];

export default function AuditLog() {
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const [actor, setActor] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const debouncedAction = useDebounced(action);
  const debouncedActor = useDebounced(actor);
  const { data, loading, error } = useFetch(
    () => api.get('/audit-logs', { entityType, action: debouncedAction, actor: debouncedActor, from, to, page, size: 20 }),
    [entityType, debouncedAction, debouncedActor, from, to, page],
  );
  const reset = (fn) => (e) => { fn(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title="Audit log" subtitle="Append-only record of business actions." />
      <Card>
        <div className="filters">
          <select value={entityType} onChange={reset(setEntityType)}>
            <option value="">All entities</option>
            {ENTITY_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <input placeholder="Action (e.g. APPROVE)" value={action} onChange={reset(setAction)} />
          <input placeholder="Actor email" value={actor} onChange={reset(setActor)} />
          <label className="row">From <input type="date" value={from} onChange={reset(setFrom)} /></label>
          <label className="row">To <input type="date" value={to} onChange={reset(setTo)} /></label>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.content.length === 0 ? <EmptyState>No audit entries match.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
              <tbody>
                {data?.content.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                    <td>{a.actorEmail || 'system'}</td>
                    <td><span className="tag">{a.action}</span></td>
                    <td className="nowrap">{a.entityType} #{a.entityId}</td>
                    <td>{a.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
