import { useContext, useEffect } from "react";
import { api, followOrders } from "../api.js";
import Icon from "../icons.jsx";
import { ConfirmButton, Empty, Loading, Notify, StatusPill, money, useLoad } from "../ui.jsx";

const EXPLAIN = {
  PENDING: "Reserving stock and taking payment. This page updates by itself.",
  PAID: "Payment received. Cancel any time for a full refund.",
  PAYMENT_DECLINED: "Your payment was declined, so nothing was charged.",
  OUT_OF_STOCK: "Not enough stock to fill this order, so nothing was charged.",
  CANCELLED: "Cancelled. Any payment taken is refunded.",
};

export default function Orders() {
  const notify = useContext(Notify);
  const [data, reload] = useLoad(async () => {
    const [orders, products] = await Promise.all([api("/orders"), api("/products")]);
    return { orders, names: new Map(products.map(product => [product.id, product.name])) };
  });
  // The server pushes status changes, so the page redraws by itself.
  useEffect(() => {
    const stop = new AbortController();
    followOrders(stop.signal, reload);
    return () => stop.abort();
  }, [reload]);

  if (!data) {
    return <Loading />;
  }
  if (!data.orders.length) {
    return (
      <Empty icon="package" title="No orders yet"
        action={<a className="button" href="#/products"><Icon name="shop" />Start shopping</a>}>
        When you check out, your order and its live status appear here.
      </Empty>
    );
  }
  return <>
    <h1>My orders <small className="live"><Icon name="zap" />Live updates on</small></h1>
    <ul className="orders">
      {data.orders.map(order => (
        <li className={`order ${order.status.toLowerCase()}`} key={order.id}>
          <div>
            <p className="order-title">
              <StatusPill status={order.status} />
              <strong>{order.lines.map(line => `${line.productName ?? data.names.get(line.productId) ?? "Product no longer sold"} × ${line.quantity}`).join(", ")}</strong>
            </p>
            <p className="hint meta">
              <span>{EXPLAIN[order.status]}</span>
              <span><Icon name="calendar" />{new Date(order.createdAt).toLocaleString()}</span>
              <span><Icon name="tag" />Order {order.id.slice(0, 8)}</span>
            </p>
          </div>
          <p className="price">{money(order.total)}</p>
          {(order.status === "PENDING" || order.status === "PAID") &&
            <ConfirmButton className="secondary" confirmLabel="Yes, cancel"
              question={order.status === "PAID" ? "Cancel and refund?" : "Cancel this order?"}
              onAction={async () => {
                await api(`/orders/${order.id}/cancel`, { method: "POST" });
                notify(order.status === "PAID" ? "Order cancelled. Your refund has started." : "Order cancelled.");
                await reload();
              }}><Icon name="xCircle" />Cancel</ConfirmButton>}
        </li>
      ))}
    </ul>
  </>;
}
