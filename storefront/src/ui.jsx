// Small pieces every page uses.
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import Icon from "./icons.jsx";

export const STATUS = {
  PENDING: "Processing",
  PAID: "Paid",
  PAYMENT_DECLINED: "Payment declined",
  OUT_OF_STOCK: "Out of stock",
  CANCELLED: "Cancelled",
};

export const STATUS_ICON = {
  PENDING: "clock",
  PAID: "checkCircle",
  PAYMENT_DECLINED: "xCircle",
  OUT_OF_STOCK: "alert",
  CANCELLED: "ban",
};

export const money = amount => Number(amount).toFixed(2);

export const go = page => { location.hash = `#/${page}`; };

/** notify(message, kind) shows the message bar; kind is "ok" (default) or "error". */
export const Notify = createContext(() => {});

/** Returns run(fn): calls fn and shows its failure in the message bar instead of losing it. */
export function useRun() {
  const notify = useContext(Notify);
  return useCallback(async fn => {
    try {
      await fn();
    } catch (error) {
      notify(error.message, "error");
    }
  }, [notify]);
}

/** Loads a page's data. Returns [data, reload]; data is null until the first load finishes. */
export function useLoad(load) {
  const run = useRun();
  const [data, setData] = useState(null);
  // load is left out of the dependencies: it only reads values that never change.
  const reload = useCallback(() => run(async () => setData(await load())), [run]);
  useEffect(() => { reload(); }, [reload]);
  return [data, reload];
}

/** A button that is disabled while its action runs and reports the action's failure. */
export function ActionButton({ onAction, disabled, icon, children, ...props }) {
  const run = useRun();
  const [busy, setBusy] = useState(false);
  return (
    <button {...props} disabled={busy || disabled} onClick={async () => {
      setBusy(true);
      await run(onAction);
      setBusy(false);
    }}>{icon && <Icon name={icon} />}{children}</button>
  );
}

/** Asks "are you sure?" in place, instead of a browser pop-up, before running a destructive action. */
export function ConfirmButton({ question, confirmLabel, onAction, children, ...props }) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return <button {...props} onClick={() => setAsking(true)}>{children}</button>;
  }
  return (
    <span className="confirm" role="group" aria-label={question}>
      <span>{question}</span>
      <ActionButton className="danger" onAction={async () => {
        await onAction();
        setAsking(false);
      }}>{confirmLabel}</ActionButton>
      {/* Focus lands on the safe choice. */}
      <button className="secondary" autoFocus onClick={() => setAsking(false)}>Keep</button>
    </span>
  );
}

/** Minus, quantity, plus. onChange gets the new quantity, between min and max. */
export function Stepper({ label, quantity, min = 1, max = 99, onChange }) {
  return (
    <div className="stepper" role="group" aria-label={label}>
      <button className="secondary" aria-label="Decrease quantity" disabled={quantity <= min}
        onClick={() => onChange(quantity - 1)}><Icon name="minus" /></button>
      <span aria-live="polite">{quantity}</span>
      <button className="secondary" aria-label="Increase quantity" disabled={quantity >= max}
        onClick={() => onChange(quantity + 1)}><Icon name="plus" /></button>
    </div>
  );
}

/** The catalog has no pictures, so each product gets a coloured tile with its initials. */
export function Tile({ product, small }) {
  const hue = [...product.id].reduce((sum, c) => sum + c.charCodeAt(0), 0) % 6;
  const initials = product.name.split(/\s+/).slice(0, 2).map(word => word[0]).join("").toUpperCase();
  return <div className={`tile tile-${hue}${small ? " small" : ""}`} aria-hidden="true">{initials}</div>;
}

export function StatusPill({ status }) {
  return (
    <span className={`status ${status.toLowerCase()}`}>
      <Icon name={STATUS_ICON[status] ?? "clock"} />{STATUS[status] ?? status}
    </span>
  );
}

/** A centred message with a large icon, for empty lists. */
export function Empty({ icon, title, children, action }) {
  return (
    <section className="empty">
      <span className="empty-icon"><Icon name={icon} /></span>
      <h1>{title}</h1>
      <p>{children}</p>
      {action}
    </section>
  );
}

/**
 * A row of figures with icons, for the admin pages. With onSelect, each figure is also a filter
 * button: items are [icon, label, value, tone, key] and the key of the chosen one is `selected`.
 */
export function Stats({ items, selected, onSelect }) {
  return (
    <ul className={`stats${onSelect ? " selectable" : ""}`}>
      {items.map(([icon, label, value, tone, key]) => {
        const body = <>
          <span className="stat-icon"><Icon name={icon} /></span>
          <span><strong>{value}</strong>{label}</span>
        </>;
        return (
          <li key={label} className={`${tone ?? ""}${onSelect && selected === key ? " selected" : ""}`}>
            {onSelect
              ? <button aria-pressed={selected === key} onClick={() => onSelect(key)}>{body}</button>
              : body}
          </li>
        );
      })}
    </ul>
  );
}

export const Loading = () => <p className="loading"><span className="spinner" aria-hidden="true" />Loading…</p>;
