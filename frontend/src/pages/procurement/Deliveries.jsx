import { useState } from 'react';
import { api } from '../../api.js';
import { useFetch } from '../../hooks.js';
import { Alert, Card, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { OrderTable } from '../../components/tables.jsx';
import OrderDetails from '../orders/OrderDetails.jsx';

const TABS = [
  { status: 'ISSUED', label: 'Awaiting delivery' },
  { status: 'PARTIALLY_RECEIVED', label: 'Partially received' },
  { status: 'COMPLETED', label: 'Completed' },
];

/** Orders grouped by delivery state. Receiving goods is done on the order page. */
export function DeliveryList() {
  const [status, setStatus] = useState('ISSUED');
  const [page, setPage] = useState(0);
  const { data, loading, error } = useFetch(() => api.get('/purchase-orders', { status, page, size: 10 }), [status, page]);

  return (
    <>
      <PageHeader title="Deliveries" subtitle="Open an order to record goods received." />
      <Card>
        <div className="filters">
          {TABS.map((t) => (
            <button key={t.status} className={`btn ${status === t.status ? 'btn-primary' : ''}`} onClick={() => { setStatus(t.status); setPage(0); }}>{t.label}</button>
          ))}
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : <OrderTable rows={data?.content} />}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}

export function DeliveryDetail() {
  return <OrderDetails />;
}
