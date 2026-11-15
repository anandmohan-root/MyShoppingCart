import Icon from "../icons.jsx";

const SECTIONS = [
  ["admin", "package", "Products"],
  ["all-orders", "clipboard", "Orders"],
];

/** The admin console: a sidebar of sections beside the page, with a heading and page actions. */
export default function AdminLayout({ active, title, subtitle, children }) {
  return (
    <div className="admin">
      <aside className="admin-side">
        <p className="admin-brand"><span className="admin-logo"><Icon name="sliders" /></span>Admin console</p>
        <ul>
          {SECTIONS.map(([page, icon, label]) => (
            <li key={page}>
              <a href={`#/${page}`} className={active === page ? "active" : ""} aria-current={active === page ? "page" : undefined}>
                <Icon name={icon} />{label}
              </a>
            </li>
          ))}
          <li className="side-split"><a href="#/products"><Icon name="shop" />Storefront<Icon name="arrowRight" className="end" /></a></li>
        </ul>
      </aside>
      <div className="admin-main">
        <div className="admin-head">
          <h1>{title}</h1>
          <p className="hint">{subtitle}</p>
        </div>
        {children}
      </div>
    </div>
  );
}
