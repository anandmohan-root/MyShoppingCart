import { useContext, useEffect, useState } from "react";
import { api } from "../api.js";
import Icon from "../icons.jsx";
import { ConfirmButton, Loading, Notify, STATUS, STATUS_ICON, Stats, StatusPill, money, useRun } from "../ui.jsx";
import AdminLayout from "./AdminLayout.jsx";

/** Every customer's orders, newest first, for admins; filter by status, cancel any open one. */
export default function AdminOrders() {
  const notify = useContext(Notify);
  const run = useRun();
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [data, setData] = useState(null);

  const load = wanted => run(async () => {
    const [orders, products] = await Promise.all([
      api(`/admin/orders${wanted ? `?status=${wanted}` : ""}`), api("/products?includeOffSale=true")]);
    setData({ orders, names: new Map(products.map(product => [product.id, product.name])) });
  });
  // Reloads only when the filter changes.
  useEffect(() => { load(status); }, [status]);

  const tabs = (
    <div className="tabs" role="group" aria-label="Status">
      {[["", "All", "clipboard"], ...Object.entries(STATUS).map(([value, label]) => [value, label, STATUS_ICON[value]])]
        .map(([value, label, icon]) => (
          <button key={value} className={status === value ? "active" : ""} aria-pressed={status === value} onClick={() => setStatus(value)}>
            <Icon name={icon} />{label}
          </button>
        ))}
    </div>
  );

  let body = <Loading />;
  if (data) {
    const count = wanted => data.orders.filter(order => order.status === wanted).length;
    const value = data.orders.filter(order => order.status === "PAID").reduce((sum, order) => sum + Number(order.total), 0);
    const wanted = search.trim().toLowerCase();
    const shown = data.orders.filter(order => order.id.startsWith(wanted) || order.customerId.startsWith(wanted));
    body = <>
      <Stats items={[
        ["package", "Orders in this list", data.orders.length],
        ["card", "Paid revenue", money(value), "good"],
        ["clock", "Processing", count("PENDING"), "warn"],
        ["xCircle", "Declined or out of stock", count("PAYMENT_DECLINED") + count("OUT_OF_STOCK"), "bad"],
      ]} />
      <section className="panel">
        <h2><Icon name="clipboard" />Orders <small>newest first, at most 50</small></h2>
        <div className="admin-tools">
          <label className="admin-search">
            <Icon name="search" />
            <input type="search" placeholder="Find by order or customer id" aria-label="Find an order"
              value={search} onChange={event => setSearch(event.target.value)} />
          </label>
          <button className="secondary" onClick={() => load(status)}><Icon name="refresh" />Refresh</button>
        </div>
        {shown.length === 0 ? <p className="hint none">No orders here.</p> :
          <div className="table-wrap">
            <table className="admin-table">
              <thead><tr><th>Order</th><th>Customer</th><th>Items</th><th className="num">Total</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {shown.map(order => {
                  const placed = new Date(order.createdAt);
                  return (
                    <tr key={order.id}>
                      <td>
                        <strong className="mono">#{order.id.slice(0, 8)}</strong>
                        <small className="sub">{placed.toLocaleDateString()} · {placed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</small>
                      </td>
                      <td title={order.customerId}>
                        <span className="customer">
                          <span className="avatar small" aria-hidden="true">{order.customerId[0].toUpperCase()}</span>
                          <span className="mono">{order.customerId.slice(0, 8)}</span>
                        </span>
                      </td>
                      <td>
                        <ul className="lines">
                          {order.lines.map(line => (
                            <li key={line.productId}>{`${line.productName ?? data.names.get(line.productId) ?? "Unknown product"} × ${line.quantity}`}</li>
                          ))}
                        </ul>
                      </td>
                      <td className="num"><strong>{money(order.total)}</strong></td>
                      <td><StatusPill status={order.status} /></td>
                      <td>{(order.status === "PENDING" || order.status === "PAID") &&
                        <ConfirmButton className="danger" confirmLabel="Yes, cancel"
                          question={order.status === "PAID" ? "Cancel and refund?" : "Cancel this order?"}
                          onAction={async () => {
                            await api(`/admin/orders/${order.id}/cancel`, { method: "POST" });
                            notify("Order cancelled; the customer is emailed.");
                            await load(status);
                          }}><Icon name="xCircle" />Cancel</ConfirmButton>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>}
      </section>
    </>;
  }

  return (
    <AdminLayout active="all-orders" title="Orders" subtitle="Every customer's orders. Cancelling a paid order refunds it and emails the customer.">
      {tabs}
      {body}
    </AdminLayout>
  );
}
