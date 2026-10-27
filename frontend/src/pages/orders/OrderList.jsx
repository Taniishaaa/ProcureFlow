import { useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { OrderTable } from '../../components/tables.jsx';
import { ORDER_STATUSES, statusLabel } from '../../utils.js';

export default function OrderList({ variant }) {
  const { user } = useAuth();
  const [status, setStatus] = useState('');
  const [supplierId, setSupplierId] = useState('');
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const register = variant === 'register';
  const suppliers = useFetch(() => (register ? api.get('/suppliers', { size: 100 }) : Promise.resolve(null)), [register]);
  const { data, loading, error } = useFetch(
    () => api.get('/purchase-orders', { status, supplierId, search: debounced, from, to, page, size: 10 }),
    [status, supplierId, debounced, from, to, page],
  );
  const reset = (fn) => (e) => { fn(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader
        title={register ? 'Purchase orders' : 'My orders'}
        subtitle={register ? undefined : 'Issued purchase orders raised from your requests.'}
      />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by PO or request number…" value={search} onChange={reset(setSearch)} />
          <select value={status} onChange={reset(setStatus)}>
            <option value="">All statuses</option>
            {ORDER_STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
          {register && (
            <select value={supplierId} onChange={reset(setSupplierId)}>
              <option value="">All suppliers</option>
              {(suppliers.data?.content || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          )}
          <label className="row">From <input type="date" value={from} onChange={reset(setFrom)} /></label>
          <label className="row">To <input type="date" value={to} onChange={reset(setTo)} /></label>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : <OrderTable rows={data?.content} showRequester={user.role !== 'EMPLOYEE'} />}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
