import { useContext, useState } from "react";
import { api } from "../api.js";
import Icon from "../icons.jsx";
import { ActionButton, Empty, Loading, Notify, Stepper, Tile, money, useLoad, useRun } from "../ui.jsx";

export default function Shop({ query, refreshCart }) {
  const notify = useContext(Notify);
  const run = useRun();
  const [sort, setSort] = useState("");
  const [inStockOnly, setInStockOnly] = useState(false);
  const [data, reload] = useLoad(async () => {
    const [products, stock, cart] = await Promise.all([api("/products"), api("/stock"), api("/cart")]);
    return {
      products,
      available: new Map(stock.map(item => [item.productId, item.available])),
      inCart: new Map(cart.items.map(item => [item.productId, item.quantity])),
    };
  });
  if (!data) {
    return <Loading />;
  }

  const inStock = product => data.available.get(product.id) ?? 0;
  const wanted = query.trim().toLowerCase();
  const shown = data.products.filter(product =>
    product.name.toLowerCase().includes(wanted) && (!inStockOnly || inStock(product) > 0));
  if (sort) {
    shown.sort((a, b) => (a.price - b.price) * (sort === "low" ? 1 : -1));
  } else {
    // Recommended: what can be bought comes first.
    shown.sort((a, b) => (inStock(a) === 0) - (inStock(b) === 0));
  }

  async function setQuantity(product, quantity) {
    await (quantity === 0
      ? api(`/cart/items/${product.id}`, { method: "DELETE" })
      : api(`/cart/items/${product.id}`, { method: "PUT", body: { quantity } }));
    await reload();
    refreshCart();
  }

  return <>
    {!wanted && <ul className="promo">
      <li><Icon name="truck" />Free delivery</li>
      <li><Icon name="zap" />Live order tracking</li>
      <li><Icon name="undo" />Easy refunds</li>
    </ul>}
    <div className="toolbar">
      <h2>{wanted ? `Results for “${query.trim()}”` : "All products"} <small>{shown.length} items</small></h2>
      <label className="chip">
        <input type="checkbox" checked={inStockOnly} onChange={event => setInStockOnly(event.target.checked)} />
        <Icon name="filter" />In stock only
      </label>
      <label className="select-wrap">
        <Icon name="sort" />
        <select aria-label="Sort by" value={sort} onChange={event => setSort(event.target.value)}>
          <option value="">Recommended</option>
          <option value="low">Price: low to high</option>
          <option value="high">Price: high to low</option>
        </select>
      </label>
    </div>
    {shown.length === 0 && <Empty icon="search" title="No products match">Try another search or clear the filter.</Empty>}
    <div className="grid">
      {shown.map(product => {
        const left = inStock(product);
        const quantity = data.inCart.get(product.id) ?? 0;
        return (
          <article className={`card${left === 0 ? " sold-out" : ""}`} key={product.id}>
            <div className="card-media">
              <Tile product={product} />
              {left > 0 && left <= 5 && <span className="flag">Only {left} left</span>}
              {left === 0 && <span className="flag out">Sold out</span>}
            </div>
            <div className="card-body">
              <h3>{product.name}</h3>
              <p className="price-row">
                <span className="price">{money(product.price)}</span>
                <span className={`stock${left === 0 ? " out" : left <= 5 ? " low" : ""}`}>
                  {left === 0 ? "Out of stock" : left <= 5 ? "Few left" : `${left} in stock`}
                </span>
              </p>
              {quantity > 0
                ? <div className="in-cart">
                    <Stepper label={`Quantity of ${product.name} in your cart`} quantity={quantity} min={0} max={Math.min(99, left)}
                      onChange={next => run(() => setQuantity(product, next))} />
                    <a href="#/cart">In cart<Icon name="arrowRight" /></a>
                  </div>
                : <ActionButton icon="bag" disabled={left === 0} onAction={async () => {
                    await setQuantity(product, 1);
                    notify(`${product.name} is in your cart.`);
                  }}>Add to cart</ActionButton>}
            </div>
          </article>
        );
      })}
    </div>
  </>;
}
