import { useCallback, useEffect, useState } from "react";
import { api } from "./api.js";
import { currentUser, signIn, signOut } from "./auth.js";
import Icon from "./icons.jsx";
import { Notify, go } from "./ui.jsx";
import Shop from "./pages/Shop.jsx";
import Cart from "./pages/Cart.jsx";
import Orders from "./pages/Orders.jsx";
import AdminProducts from "./pages/AdminProducts.jsx";
import AdminOrders from "./pages/AdminOrders.jsx";

const PAGES = { products: Shop, cart: Cart, orders: Orders, admin: AdminProducts, "all-orders": AdminOrders };
const ADMIN_PAGES = ["admin", "all-orders"];
const TITLES = { products: "Shop", cart: "Cart", orders: "My orders", admin: "Admin · Products", "all-orders": "Admin · Orders" };
const pageFromHash = () => location.hash.slice(2) || "products";

export default function App({ signInError }) {
  const user = currentUser();
  const signedIn = Boolean(user);
  const [page, setPage] = useState(pageFromHash);
  const [notice, setNotice] = useState(signInError ? { message: signInError, kind: "error" } : null);
  const [cartCount, setCartCount] = useState(0);
  const [query, setQuery] = useState("");

  const notify = useCallback((message, kind = "ok") => setNotice({ message, kind }), []);
  const refreshCart = useCallback(() => api("/cart")
    .then(cart => setCartCount(cart.items.reduce((sum, item) => sum + item.quantity, 0)))
    .catch(() => {}), []);

  useEffect(() => {
    const onHashChange = () => {
      setPage(pageFromHash());
      window.scrollTo(0, 0);
      // Keyboard and screen-reader users start at the new page, not back in the header.
      document.querySelector("main").focus();
    };
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);
  useEffect(() => {
    if (signedIn) {
      refreshCart();
    }
  }, [signedIn, refreshCart]);
  useEffect(() => {
    // An error stays until it is dismissed; a confirmation goes away by itself.
    if (notice?.kind === "ok") {
      const timer = setTimeout(() => setNotice(null), 6000);
      return () => clearTimeout(timer);
    }
  }, [notice]);

  const name = PAGES[page] && (!ADMIN_PAGES.includes(page) || user?.isAdmin) ? page : "products";
  const Page = PAGES[name];
  useEffect(() => { document.title = signedIn ? `${TITLES[name]} · MyShoppingCart` : "MyShoppingCart"; }, [name, signedIn]);
  const link = (to, icon, label) => (
    <a href={`#/${to}`} className={name === to ? "active" : ""} aria-current={name === to ? "page" : undefined}>
      <Icon name={icon} />{label}
    </a>
  );

  return (
    <Notify.Provider value={notify}>
      <header>
        <a className="brand" href="#/products"><span className="mark"><Icon name="bag" /></span>MyShoppingCart</a>
        {signedIn && <>
          <nav aria-label="Main">
            {link("products", "shop", "Shop")}
            {link("orders", "package", "My orders")}
            {user.isAdmin && link("admin", "sliders", "Admin")}
            {user.isAdmin && link("all-orders", "clipboard", "All orders")}
          </nav>
          <label className="search">
            <Icon name="search" />
            <input type="search" placeholder="Search for products" aria-label="Search products"
              value={query} onChange={event => { setQuery(event.target.value); go("products"); }} />
          </label>
          <div className="account">
            <span className="user" title={user.name}>
              <span className="avatar" aria-hidden="true">{user.name[0].toUpperCase()}</span>
              <span className="user-name">{user.name}{user.isAdmin && <small>admin</small>}</span>
            </span>
            <a href="#/cart" className={`icon-link${name === "cart" ? " active" : ""}`}
              aria-label={cartCount > 0 ? `Cart (${cartCount} items)` : "Cart"}>
              <span className="icon-wrap"><Icon name="cart" />{cartCount > 0 && <span className="badge">{cartCount}</span>}</span>
              Cart
            </a>
            <button className="icon-link" onClick={signOut} aria-label="Sign out" title="Sign out">
              <Icon name="logout" /><span className="label">Sign out</span>
            </button>
          </div>
        </>}
      </header>
      {notice && (
        <div id="notice" role={notice.kind === "error" ? "alert" : "status"} className={notice.kind}>
          <Icon name={notice.kind === "error" ? "alert" : "checkCircle"} />
          <span>{notice.message}</span>
          <button className="dismiss" aria-label="Dismiss message" onClick={() => setNotice(null)}><Icon name="x" /></button>
        </div>
      )}
      <main tabIndex={-1}>
        {signedIn
          ? <Page query={query} refreshCart={refreshCart} />
          : <section className="welcome">
              <p className="eyebrow"><Icon name="zap" />New season, new favourites</p>
              <h1>Everything you need, in one cart</h1>
              <p>Sign in to browse products, fill your cart and follow your orders as they happen.</p>
              <button onClick={() => signIn()}><Icon name="login" />Sign in</button>
              <ul className="perks">
                <li><Icon name="zap" /><strong>Live order status</strong>Watch payment and stock updates arrive by themselves.</li>
                <li><Icon name="undo" /><strong>Easy cancellation</strong>Cancel a paid order and the refund starts at once.</li>
                <li><Icon name="mail" /><strong>Email updates</strong>Every step of your order lands in your mailbox.</li>
              </ul>
            </section>}
      </main>
      <footer>
        <ul className="trust">
          <li><Icon name="truck" />Free delivery on every order</li>
          <li><Icon name="shield" />Secure sign-in with Keycloak</li>
          <li><Icon name="undo" />Cancel a paid order for a full refund</li>
        </ul>
        <p>MyShoppingCart · a reference shop built on microservices</p>
      </footer>
    </Notify.Provider>
  );
}
