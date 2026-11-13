// The MyShoppingCart storefront: products, cart, orders and, for admins, product management.
// Every page is built with DOM calls and text nodes, never innerHTML, so product names and
// other data cannot inject markup or script.

import { signIn, completeSignIn, accessToken, currentUser, signOut } from "./auth.js";

const main = document.querySelector("main");
const nav = document.querySelector("nav");
const notice = document.querySelector("#notice");
let following = null; // aborts the "My orders" update stream when the page changes
let flash = null;

const STATUS = {
  PENDING: "Processing",
  PAID: "Paid",
  PAYMENT_DECLINED: "Payment declined",
  OUT_OF_STOCK: "Out of stock",
  CANCELLED: "Cancelled",
};

// ---------- helpers

/** Creates an element. Strings among the children become text nodes, never markup. */
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) {
      continue;
    }
    if (key === "class") {
      node.className = value;
    } else if (key.startsWith("on")) {
      node.addEventListener(key.slice(2), value);
    } else if (key in node) {
      node[key] = value;
    } else {
      node.setAttribute(key, value);
    }
  }
  node.append(...children.flat().filter(child => child !== null && child !== undefined && child !== false));
  return node;
}

const money = amount => Number(amount).toFixed(2);

function show(message, kind) {
  notice.textContent = message;
  notice.className = kind;
  notice.hidden = false;
}

/** Wraps an event handler so a failure shows its message instead of being lost. */
function action(handler) {
  return async event => {
    const button = event?.currentTarget instanceof HTMLButtonElement ? event.currentTarget : null;
    if (button) {
      button.disabled = true;
    }
    try {
      await handler(event);
    } catch (error) {
      show(error.message, "error");
    } finally {
      if (button) {
        button.disabled = false;
      }
    }
  };
}

/** Calls the API through Kong with the user's token. A 401 sends the user to sign in again. */
async function api(path, { method = "GET", body } = {}) {
  const token = await accessToken();
  if (!token) {
    await signIn();
    return new Promise(() => {});
  }
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (response.status === 401) {
    await signIn();
    return new Promise(() => {});
  }
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      detail = (await response.json()).detail || detail;
    } catch {
      // not a JSON problem body
    }
    const error = new Error(response.status === 403 ? "You are not allowed to do that." : detail);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

// ---------- layout and routing

function renderNav(user) {
  const page = location.hash.slice(2) || "products";
  const link = (name, label) => el("a", { href: `#/${name}`, class: page === name ? "active" : "" }, label);
  if (!user) {
    nav.replaceChildren();
    return;
  }
  const cartLink = link("cart", "Cart");
  const items = [
    link("products", "Products"),
    cartLink,
    link("orders", "My orders"),
    user.isAdmin ? link("admin", "Admin") : null,
    user.isAdmin ? link("all-orders", "All orders") : null,
    el("span", { class: "user" }, user.name + (user.isAdmin ? " (admin)" : "")),
    el("button", { class: "secondary", onclick: signOut }, "Sign out"),
  ];
  nav.replaceChildren(...items.filter(Boolean));
  api("/cart").then(cart => {
    const count = cart.items.reduce((sum, item) => sum + item.quantity, 0);
    cartLink.textContent = count ? `Cart (${count})` : "Cart";
  }).catch(() => {});
}

const pages = { products: productsPage, cart: cartPage, orders: ordersPage, admin: adminPage, "all-orders": allOrdersPage };
const ADMIN_PAGES = ["admin", "all-orders"];

async function render() {
  following?.abort();
  following = null;
  notice.hidden = true;
  if (flash) {
    show(flash.message, flash.kind);
    flash = null;
  }
  const user = currentUser();
  renderNav(user);
  if (!user) {
    main.replaceChildren(el("section", { class: "welcome" },
      el("h1", {}, "Welcome to MyShoppingCart"),
      el("p", {}, "Sign in to browse products, fill your cart and follow your orders."),
      el("button", { onclick: action(signIn) }, "Sign in")));
    return;
  }
  const name = location.hash.slice(2) || "products";
  const page = pages[name] && (!ADMIN_PAGES.includes(name) || user.isAdmin) ? pages[name] : productsPage;
  try {
    await page();
    if (page === ordersPage) {
      following = new AbortController();
      followOrders(following.signal);
    }
  } catch (error) {
    show(error.message, "error");
  }
}

function go(page, message) {
  flash = message ? { message, kind: "ok" } : null;
  if (location.hash === `#/${page}`) {
    render();
  } else {
    location.hash = `#/${page}`;
  }
}

// ---------- pages

async function productsPage() {
  const [products, stock] = await Promise.all([api("/products"), api("/stock")]);
  const available = new Map(stock.map(item => [item.productId, item.available]));
  main.replaceChildren(
    el("h1", {}, "Products"),
    el("div", { class: "grid" }, products.map(product => productCard(product, available.get(product.id) ?? 0))));
}

function productCard(product, inStock) {
  const quantity = el("input", { type: "number", min: 1, max: 99, value: 1, "aria-label": `Quantity of ${product.name}` });
  return el("article", { class: "card" },
    el("h2", {}, product.name),
    el("p", { class: "price" }, money(product.price)),
    el("p", { class: inStock > 0 ? "stock" : "stock out" }, inStock > 0 ? `${inStock} in stock` : "Out of stock"),
    el("div", { class: "row" },
      quantity,
      el("button", {
        disabled: inStock === 0,
        onclick: action(async () => {
          const cart = await api("/cart");
          const current = cart.items.find(item => item.productId === product.id)?.quantity ?? 0;
          const wanted = Math.min(99, current + Math.max(1, Number(quantity.value) || 1));
          await api(`/cart/items/${product.id}`, { method: "PUT", body: { quantity: wanted } });
          go("products", `${product.name}: ${wanted} in your cart.`);
        }),
      }, "Add to cart")));
}

async function cartPage() {
  const [cart, products] = await Promise.all([api("/cart"), api("/products")]);
  const byId = new Map(products.map(product => [product.id, product]));
  const title = el("h1", {}, "Your cart");
  if (!cart.items.length) {
    main.replaceChildren(title, el("p", {}, "Your cart is empty. ", el("a", { href: "#/products" }, "Browse products"), "."));
    return;
  }
  let total = 0;
  const rows = cart.items.map(item => {
    const product = byId.get(item.productId);
    const price = product ? Number(product.price) : null;
    if (price !== null) {
      total += price * item.quantity;
    }
    const quantity = el("input", {
      type: "number", min: 1, max: 99, value: item.quantity, "aria-label": "Quantity",
      onchange: action(async () => {
        await api(`/cart/items/${item.productId}`, { method: "PUT", body: { quantity: Number(quantity.value) } });
        go("cart");
      }),
    });
    return el("tr", {},
      el("td", {}, product ? product.name : "No longer for sale"),
      el("td", { class: "num" }, price === null ? "–" : money(price)),
      el("td", {}, quantity),
      el("td", { class: "num" }, price === null ? "–" : money(price * item.quantity)),
      el("td", {}, el("button", {
        class: "link",
        onclick: action(async () => {
          await api(`/cart/items/${item.productId}`, { method: "DELETE" });
          go("cart");
        }),
      }, "Remove")));
  });
  main.replaceChildren(
    title,
    el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Product"), el("th", { class: "num" }, "Price"), el("th", {}, "Quantity"),
        el("th", { class: "num" }, "Subtotal"), el("th", {}, ""))),
      el("tbody", {}, rows)),
    el("p", { class: "total" }, `Estimated total: ${money(total)}`),
    el("p", { class: "hint" }, "Prices are confirmed when you check out. Stock is reserved and payment taken after that."),
    el("div", { class: "row" },
      el("button", {
        onclick: action(async () => {
          const order = await api("/cart/checkout", { method: "POST" });
          go("orders", `Order placed for ${money(order.total)}. Its status updates below.`);
        }),
      }, "Check out"),
      el("button", {
        class: "secondary",
        onclick: action(async () => {
          await api("/cart", { method: "DELETE" });
          go("cart");
        }),
      }, "Empty cart")));
}

async function ordersPage() {
  const [orders, products] = await Promise.all([api("/orders"), api("/products")]);
  const names = new Map(products.map(product => [product.id, product.name]));
  const title = el("h1", {}, "My orders");
  if (!orders.length) {
    main.replaceChildren(title, el("p", {}, "No orders yet."));
    return;
  }
  const rows = orders.map(order => el("tr", {},
    el("td", {}, new Date(order.createdAt).toLocaleString()),
    el("td", {}, order.lines.map(line => `${names.get(line.productId) ?? "Product no longer sold"} × ${line.quantity}`).join(", ")),
    el("td", { class: "num" }, money(order.total)),
    el("td", {}, el("span", { class: `status ${order.status.toLowerCase()}` }, STATUS[order.status] ?? order.status)),
    el("td", {}, order.status === "PENDING" || order.status === "PAID"
      ? el("button", {
        class: "link",
        onclick: action(async () => {
          if (!confirm(order.status === "PAID" ? "Cancel this order and refund the payment?" : "Cancel this order?")) {
            return;
          }
          await api(`/orders/${order.id}/cancel`, { method: "POST" });
          go("orders", "Order cancelled.");
        }),
      }, "Cancel")
      : "")));
  main.replaceChildren(
    title,
    el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Placed"), el("th", {}, "Items"), el("th", { class: "num" }, "Total"),
        el("th", {}, "Status"), el("th", {}, ""))),
      el("tbody", {}, rows)));
}

/**
 * Redraws "My orders" whenever order-service pushes a status change (GET /orders/events, a
 * server-sent event stream). fetch is used instead of EventSource because EventSource cannot send
 * the Authorization header. The page redraws once each time the stream opens, so no change is
 * missed while connecting; the server ends each stream after about 50 seconds and the page reconnects.
 */
async function followOrders(stop) {
  while (!stop.aborted) {
    try {
      const token = await accessToken();
      if (!token) {
        return;
      }
      const response = await fetch("/orders/events", { headers: { Authorization: `Bearer ${token}` }, signal: stop });
      if (!response.ok) {
        throw new Error(`order updates unavailable (${response.status})`);
      }
      // The stream is registered before the server answers, so this redraw catches any change
      // made between drawing the page and opening the stream.
      await ordersPage();
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      for (let chunk = await reader.read(); !chunk.done; chunk = await reader.read()) {
        if (chunk.value.includes("event:order-status")) {
          await ordersPage();
        }
      }
    } catch (error) {
      if (stop.aborted) {
        return;
      }
      // Network trouble or a restart: wait a little before reconnecting.
      await new Promise(resolve => setTimeout(resolve, 3000));
    }
  }
}

let allOrdersStatus = "";

/** Every customer's orders, newest first, for admins; filter by status, cancel any open one. */
async function allOrdersPage() {
  const query = allOrdersStatus ? `?status=${allOrdersStatus}` : "";
  const [orders, products] = await Promise.all([api(`/admin/orders${query}`), api("/products?includeOffSale=true")]);
  const names = new Map(products.map(product => [product.id, product.name]));
  const filter = el("select", {
    "aria-label": "Status",
    onchange: () => {
      allOrdersStatus = filter.value;
      go("all-orders");
    },
  }, el("option", { value: "" }, "All statuses"),
    Object.entries(STATUS).map(([value, label]) => el("option", { value, selected: value === allOrdersStatus }, label)));
  const rows = orders.map(order => el("tr", {},
    el("td", {}, new Date(order.createdAt).toLocaleString()),
    el("td", { title: order.customerId }, order.customerId.slice(0, 8)),
    el("td", {}, order.lines.map(line => `${names.get(line.productId) ?? "Unknown product"} × ${line.quantity}`).join(", ")),
    el("td", { class: "num" }, money(order.total)),
    el("td", {}, el("span", { class: `status ${order.status.toLowerCase()}` }, STATUS[order.status] ?? order.status)),
    el("td", {}, order.status === "PENDING" || order.status === "PAID"
      ? el("button", {
        class: "link",
        onclick: action(async () => {
          if (!confirm(order.status === "PAID" ? "Cancel this customer's order and refund the payment?" : "Cancel this customer's order?")) {
            return;
          }
          await api(`/admin/orders/${order.id}/cancel`, { method: "POST" });
          go("all-orders", "Order cancelled; the customer is emailed.");
        }),
      }, "Cancel")
      : "")));
  main.replaceChildren(
    el("h1", {}, "Admin: all orders"),
    el("div", { class: "row" }, filter, el("span", { class: "hint" }, `${orders.length} shown, newest first (at most 50)`)),
    orders.length
      ? el("table", {},
        el("thead", {}, el("tr", {}, el("th", {}, "Placed"), el("th", {}, "Customer"), el("th", {}, "Items"),
          el("th", { class: "num" }, "Total"), el("th", {}, "Status"), el("th", {}, ""))),
        el("tbody", {}, rows))
      : el("p", {}, "No orders with this status."));
}

async function adminPage() {
  const [products, stock] = await Promise.all([api("/products?includeOffSale=true"), api("/stock")]);
  const available = new Map(stock.map(item => [item.productId, item.available]));

  const newName = el("input", { type: "text", maxLength: 100, required: true, placeholder: "Name", "aria-label": "New product name" });
  const newPrice = el("input", { type: "number", min: 0.01, step: 0.01, required: true, placeholder: "Price", "aria-label": "New product price" });
  const addForm = el("form", {
    class: "row",
    onsubmit: action(async event => {
      event.preventDefault();
      const product = await api("/products", { method: "POST", body: { name: newName.value, price: Number(newPrice.value) } });
      go("admin", `Added ${product.name}. It starts with 0 in stock: restock it below.`);
    }),
  }, newName, newPrice, el("button", { type: "submit" }, "Add product"));

  const rows = products.map(product => {
    const name = el("input", { type: "text", maxLength: 100, value: product.name, "aria-label": "Name" });
    const price = el("input", { type: "number", min: 0.01, step: 0.01, value: money(product.price), "aria-label": "Price" });
    const restockBy = el("input", { type: "number", min: 1, max: 10000, value: 10, "aria-label": "Restock quantity" });
    return el("tr", { class: product.onSale ? "" : "off-sale" },
      el("td", {}, name),
      el("td", {}, price),
      el("td", {}, el("button", {
        class: "secondary",
        onclick: action(async () => {
          await api(`/products/${product.id}`, { method: "PATCH", body: { name: name.value, price: Number(price.value) } });
          go("admin", `Saved ${name.value}.`);
        }),
      }, "Save")),
      el("td", { class: "num" }, String(available.get(product.id) ?? 0)),
      el("td", {}, el("div", { class: "row" }, restockBy, el("button", {
        class: "secondary",
        onclick: action(async () => {
          // A new product's stock entry appears a moment after it is added (inventory learns of it
          // through an event), so a 404 right after adding is retried for a few seconds.
          let item;
          for (let attempt = 1; !item; attempt++) {
            try {
              item = await api(`/stock/${product.id}/restock`, { method: "POST", body: { quantity: Number(restockBy.value) } });
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
          go("admin", `${product.name} now has ${item.available} in stock.`);
        }),
      }, "Restock"))),
      el("td", {}, el("button", {
        class: "link",
        onclick: action(async () => {
          await api(`/products/${product.id}`, { method: "PATCH", body: { onSale: !product.onSale } });
          go("admin", `${product.name} is ${product.onSale ? "off sale" : "on sale again"}.`);
        }),
      }, product.onSale ? "Take off sale" : "Put on sale")));
  });

  main.replaceChildren(
    el("h1", {}, "Admin: products"),
    el("h2", {}, "Add a product"),
    addForm,
    el("h2", {}, "All products"),
    el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Name"), el("th", {}, "Price"), el("th", {}, ""), el("th", { class: "num" }, "In stock"),
        el("th", {}, "Restock"), el("th", {}, "Sale"))),
      el("tbody", {}, rows)));
}

// ---------- start

window.addEventListener("hashchange", render);

try {
  const returnTo = await completeSignIn();
  if (returnTo) {
    location.hash = returnTo;
  }
} catch (error) {
  flash = { message: error.message, kind: "error" };
}
render();
