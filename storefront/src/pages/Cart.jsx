import { useContext } from "react";
import { api } from "../api.js";
import Icon from "../icons.jsx";
import { ActionButton, ConfirmButton, Empty, Loading, Notify, Stepper, Tile, go, money, useLoad, useRun } from "../ui.jsx";

export default function Cart({ refreshCart }) {
  const notify = useContext(Notify);
  const run = useRun();
  const [data, reload] = useLoad(async () => {
    const [cart, products, stock] = await Promise.all([api("/cart"), api("/products"), api("/stock")]);
    return {
      items: cart.items,
      byId: new Map(products.map(product => [product.id, product])),
      available: new Map(stock.map(item => [item.productId, item.available])),
    };
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
  // What would make the order fail after checkout; shown on the item, and checkout waits for it.
  const problem = item => {
    const left = data.available.get(item.productId) ?? 0;
    if (!data.byId.has(item.productId)) {
      return "No longer for sale. Remove it to check out.";
    }
    if (left === 0) {
      return "Out of stock. Remove it to check out.";
    }
    return item.quantity > left ? `Only ${left} in stock. Lower the quantity to check out.` : null;
  };
  const blocked = data.items.some(problem);
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
                {problem(item) && <p className="problem"><Icon name="alert" />{problem(item)}</p>}
                {product && <Stepper label={`Quantity of ${product.name}`} quantity={item.quantity}
                  max={Math.min(99, data.available.get(item.productId) ?? 0)} onChange={next => setQuantity(item, next)} />}
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
        <ActionButton icon="shield" disabled={blocked} onAction={async () => {
          const order = await api("/cart/checkout", { method: "POST" });
          refreshCart();
          notify(`Order placed for ${money(order.total)}. Its status updates below.`);
          go("orders");
        }}>Check out</ActionButton>
        {blocked && <p className="problem"><Icon name="alert" />Fix the marked items to check out.</p>}
        <ConfirmButton className="secondary" question="Remove everything?" confirmLabel="Yes, empty"
          onAction={() => change("/cart", { method: "DELETE" })}><Icon name="trash" />Empty cart</ConfirmButton>
        <p className="hint"><Icon name="clock" />Prices are confirmed when you check out. Stock is reserved and payment taken after that.</p>
      </aside>
    </div>
  </>;
}
