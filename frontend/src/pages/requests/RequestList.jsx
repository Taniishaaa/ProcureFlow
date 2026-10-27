import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { RequestTable } from '../../components/tables.jsx';
import { REQUEST_STATUSES, statusLabel } from '../../utils.js';

export default function RequestList({ mode }) {
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const categories = useFetch(() => (mode === 'all' ? api.get('/categories', { includeInactive: true }) : Promise.resolve([])), [mode]);
  const { data, loading, error } = useFetch(
    () =>
      api.get(mode === 'all' ? '/requests' : '/requests/mine', {
        status,
        search: debounced,
        categoryId: mode === 'all' ? categoryId : undefined,
        page,
        size: 10,
      }),
    [mode, status, debounced, categoryId, page],
  );

  return (
    <>
      <PageHeader
        title={mode === 'all' ? 'All purchase requests' : 'My purchase requests'}
        actions={mode === 'mine' && <Link className="btn btn-primary" to="/requests/new">New request</Link>}
      />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by number or title…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {REQUEST_STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
          {mode === 'all' && (
            <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
              <option value="">All categories</option>
              {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          )}
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : <RequestTable rows={data?.content} showRequester={mode === 'all'} />}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
