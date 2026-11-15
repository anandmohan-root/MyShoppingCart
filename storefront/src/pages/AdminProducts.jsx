import { useContext, useState } from "react";
import { api } from "../api.js";
import Icon from "../icons.jsx";
import { ActionButton, Loading, Notify, Stats, Tile, money, useLoad, useRun } from "../ui.jsx";
import AdminLayout from "./AdminLayout.jsx";

const LOW = 5;

export default function AdminProducts() {
  const notify = useContext(Notify);
  const run = useRun();
  const [newName, setNewName] = useState("");
  const [newPrice, setNewPrice] = useState("");
  const [filter, setFilter] = useState("all");
  const [search, setSearch] = useState("");
  const [data, reload] = useLoad(async () => {
    const [products, stock] = await Promise.all([api("/products?includeOffSale=true"), api("/stock")]);
    return { products, available: new Map(stock.map(item => [item.productId, item.available])) };
  });

  const layout = children => (
    <AdminLayout active="admin" title="Products" subtitle="Add products, set prices, restock and choose what is on sale.">
      {children}
    </AdminLayout>
  );
  if (!data) {
    return layout(<Loading />);
  }

  const inStock = product => data.available.get(product.id) ?? 0;
  const FILTERS = {
    all: () => true,
    onSale: product => product.onSale,
    low: product => product.onSale && inStock(product) > 0 && inStock(product) <= LOW,
    out: product => product.onSale && inStock(product) === 0,
    offSale: product => !product.onSale,
  };
  const count = key => data.products.filter(FILTERS[key]).length;
  const wanted = search.trim().toLowerCase();
  const shown = data.products.filter(product => FILTERS[filter](product) && product.name.toLowerCase().includes(wanted));
  const fullStock = Math.max(20, ...data.products.map(inStock));

  return layout(<>
    <Stats selected={filter} onSelect={setFilter} items={[
      ["package", "All products", count("all"), undefined, "all"],
      ["eye", "On sale", count("onSale"), "good", "onSale"],
      ["alert", `Low stock (${LOW} or fewer)`, count("low"), "warn", "low"],
      ["xCircle", "Out of stock", count("out"), "bad", "out"],
      ["eyeOff", "Off sale", count("offSale"), "muted", "offSale"],
    ]} />

    <section className="panel add-product">
      <h2><Icon name="plus" />Add a product</h2>
      <form className="add-form" onSubmit={event => {
        event.preventDefault();
        run(async () => {
          const product = await api("/products", { method: "POST", body: { name: newName, price: Number(newPrice) } });
          setNewName("");
          setNewPrice("");
          setFilter("all");
          notify(`Added ${product.name}. It starts with 0 in stock: restock it below.`);
          await reload();
        });
      }}>
        <Tile product={{ id: newName || "new", name: newName || "New" }} small />
        <input type="text" maxLength={100} required placeholder="New product name" aria-label="New product name"
          value={newName} onChange={event => setNewName(event.target.value)} />
        <input type="number" min={0.01} max={100000} step={0.01} required placeholder="Price" aria-label="New product price"
          value={newPrice} onChange={event => setNewPrice(event.target.value)} />
        <button type="submit"><Icon name="plus" />Add product</button>
        <span className="hint">Starts on sale with 0 in stock.</span>
      </form>
    </section>

    <section className="panel">
      <h2><Icon name="package" />Catalog <small>{shown.length} of {data.products.length}</small></h2>
      <label className="admin-search">
        <Icon name="search" />
        <input type="search" placeholder="Find a product by name" aria-label="Find a product"
          value={search} onChange={event => setSearch(event.target.value)} />
      </label>
      {shown.length === 0 ? <p className="hint none">No products here.</p> :
        <div className="table-wrap">
          <table className="admin-table">
            <thead><tr><th>Product</th><th>Price</th><th>Stock</th><th>Restock</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {shown.map(product =>
                <ProductRow key={product.id} product={product} inStock={inStock(product)} fullStock={fullStock} reload={reload} />)}
            </tbody>
          </table>
        </div>}
    </section>
  </>);
}

function ProductRow({ product, inStock, fullStock, reload }) {
  const notify = useContext(Notify);
  const [name, setName] = useState(product.name);
  const [price, setPrice] = useState(money(product.price));
  const [restockBy, setRestockBy] = useState(10);
  const changed = name !== product.name || Number(price) !== Number(product.price);
  const level = inStock === 0 ? "out" : inStock <= LOW ? "low" : "";

  async function restock() {
    // A new product's stock entry appears a moment after it is added (inventory learns of it
    // through an event), so a 404 right after adding is retried for a few seconds.
    let item;
    for (let attempt = 1; !item; attempt++) {
      try {
        item = await api(`/stock/${product.id}/restock`, { method: "POST", body: { quantity: Number(restockBy) } });
      } catch (error) {
        if (error.status !== 404) {
          throw error;
        }
        if (attempt === 10) {
          throw new Error(`Stock for ${product.name} is not set up yet. Try again in a moment.`);
        }
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    }
    notify(`${product.name} now has ${item.available} in stock.`);
    await reload();
  }

  return (
    <tr className={product.onSale ? "" : "off-sale"}>
      <td>
        <div className="product-cell">
          <Tile product={product} small />
          <input type="text" maxLength={100} aria-label="Name" value={name} onChange={event => setName(event.target.value)} />
        </div>
      </td>
      <td><span>
        <input type="number" min={0.01} step={0.01} aria-label="Price" value={price} onChange={event => setPrice(event.target.value)} />
      </span></td>
      <td>
        <div className={`stock-cell ${level}`}>
          <strong>{inStock}</strong>
          <span className="meter" aria-hidden="true"><span style={{ width: `${Math.min(100, inStock / fullStock * 100)}%` }} /></span>
          <small>{inStock === 0 ? "Out of stock" : inStock <= LOW ? "Low" : "In stock"}</small>
        </div>
      </td>
      <td><div className="restock">
        <input type="number" min={1} max={10000} aria-label="Restock quantity" value={restockBy} onChange={event => setRestockBy(event.target.value)} />
        <ActionButton className="secondary" icon="refresh" onAction={restock}>Restock</ActionButton>
      </div></td>
      <td>
        <span className={`sale-pill${product.onSale ? " on" : ""}`}>{product.onSale ? "On sale" : "Off sale"}</span>
        <ActionButton className="link" icon={product.onSale ? "eyeOff" : "eye"} onAction={async () => {
          await api(`/products/${product.id}`, { method: "PATCH", body: { onSale: !product.onSale } });
          notify(`${product.name} is ${product.onSale ? "off sale" : "on sale again"}.`);
          await reload();
        }}>{product.onSale ? "Take off sale" : "Put on sale"}</ActionButton>
      </td>
      <td><ActionButton className={changed ? "" : "secondary"} icon="save" disabled={!changed} onAction={async () => {
        await api(`/products/${product.id}`, { method: "PATCH", body: { name, price: Number(price) } });
        notify(`Saved ${name}.`);
        await reload();
      }}>Save</ActionButton></td>
    </tr>
  );
}
