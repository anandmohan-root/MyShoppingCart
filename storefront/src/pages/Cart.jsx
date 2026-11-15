import { useContext } from "react";
import { api } from "../api.js";
import Icon from "../icons.jsx";
import { ActionButton, Empty, Loading, Notify, Tile, go, money, useLoad, useRun } from "../ui.jsx";

export default function Cart({ refreshCart }) {
  const notify = useContext(Notify);
  const run = useRun();
  const [data, reload] = useLoad(async () => {
    const [cart, products] = await Promise.all([api("/cart"), api("/products")]);
    return { items: cart.items, byId: new Map(products.map(product => [product.id, product])) };
  });
  if (!data) {
    return <Loading />;
  }
  if (!data.items.length) {
    return (
      <Empty icon="cart" title="Your cart is empty"
        action={<a className="button" href="#/products"><Icon name="shop" />Browse products</a>}>
        Add something you like and it will show up here.
      </Empty>
    );
  }

  const change = (path, options) => run(async () => {
    await api(path, options);
    await reload();
    refreshCart();
  });
  const setQuantity = (item, quantity) =>
    change(`/cart/items/${item.productId}`, { method: "PUT", body: { quantity } });
  const count = data.items.reduce((sum, item) => sum + item.quantity, 0);
  const total = data.items.reduce((sum, item) => sum + (Number(data.byId.get(item.productId)?.price) || 0) * item.quantity, 0);

  return <>
    <ol className="steps" aria-label="Checkout steps">
      <li className="current"><Icon name="cart" />Cart</li>
      <li><Icon name="card" />Check out</li>
      <li><Icon name="zap" />Track order</li>
    </ol>
    <h1>Your cart <small>{count} {count === 1 ? "item" : "items"}</small></h1>
    <div className="checkout">
      <ul className="cart-items">
        {data.items.map(item => {
          const product = data.byId.get(item.productId);
          return (
            <li className="cart-item" key={item.productId}>
              {product && <Tile product={product} small />}
              <div>
                <h3>{product ? product.name : "No longer for sale"}</h3>
                <p className="price">{product ? money(product.price * item.quantity) : "–"}</p>
                {product && item.quantity > 1 && <p className="hint">{money(product.price)} each</p>}
                <div className="stepper" role="group" aria-label={`Quantity of ${product?.name ?? "item"}`}>
                  <button className="secondary" aria-label="Decrease quantity" disabled={item.quantity <= 1}
                    onClick={() => setQuantity(item, item.quantity - 1)}><Icon name="minus" /></button>
                  <span aria-live="polite">{item.quantity}</span>
                  <button className="secondary" aria-label="Increase quantity" disabled={item.quantity >= 99}
                    onClick={() => setQuantity(item, item.quantity + 1)}><Icon name="plus" /></button>
                </div>
              </div>
              <button className="link" onClick={() => change(`/cart/items/${item.productId}`, { method: "DELETE" })}>
                <Icon name="trash" />Remove
              </button>
            </li>
          );
        })}
      </ul>
      <aside className="summary">
        <h2>Price details</h2>
        <p><span>Items ({count})</span><span>{money(total)}</span></p>
        <p><span><Icon name="truck" />Delivery</span><span className="free">Free</span></p>
        <p className="total"><span>Estimated total</span><span>{money(total)}</span></p>
        <ActionButton icon="shield" onAction={async () => {
          const order = await api("/cart/checkout", { method: "POST" });
          refreshCart();
          notify(`Order placed for ${money(order.total)}. Its status updates below.`);
          go("orders");
        }}>Check out</ActionButton>
        <button className="secondary" onClick={() => change("/cart", { method: "DELETE" })}><Icon name="trash" />Empty cart</button>
        <p className="hint"><Icon name="clock" />Prices are confirmed when you check out. Stock is reserved and payment taken after that.</p>
      </aside>
    </div>
  </>;
}
