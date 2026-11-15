// Line icons drawn inline as SVG, so the page needs no icon font, library or extra request.
// Each icon is a list of path strings on a 24×24 grid, stroked in the current text colour.
const ring = "M12 2a10 10 0 1 0 0 20a10 10 0 0 0 0-20z";

const PATHS = {
  shop: ["M3 9l1.5-5h15L21 9", "M3 9h18v2a3 3 0 0 1-6 0a3 3 0 0 1-6 0a3 3 0 0 1-6 0z", "M5 13v8h14v-8", "M10 21v-5h4v5"],
  bag: ["M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z", "M3 6h18", "M16 10a4 4 0 0 1-8 0"],
  cart: ["M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6", "M9 20a1 1 0 1 0 0 2a1 1 0 0 0 0-2z", "M20 20a1 1 0 1 0 0 2a1 1 0 0 0 0-2z"],
  package: ["M21 16V8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.7l7 4a2 2 0 0 0 2 0l7-4a2 2 0 0 0 1-1.7z", "M3.3 7L12 12l8.7-5", "M12 22V12", "M16.5 9.4L7.5 4.2"],
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"],
  clipboard: ["M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M9 2h6v4H9z", "M8 12h8", "M8 16h5"],
  search: ["M11 3a8 8 0 1 0 0 16a8 8 0 0 0 0-16z", "M21 21l-4.35-4.35"],
  logout: ["M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9"],
  login: ["M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4", "M10 17l5-5-5-5", "M15 12H3"],
  trash: ["M3 6h18", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6", "M10 11v6", "M14 11v6", "M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"],
  truck: ["M1 3h15v13H1z", "M16 8h4l3 3v5h-7z", "M5.5 16a2.5 2.5 0 1 0 0 5a2.5 2.5 0 0 0 0-5z", "M18.5 16a2.5 2.5 0 1 0 0 5a2.5 2.5 0 0 0 0-5z"],
  shield: ["M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4"],
  check: ["M20 6L9 17l-5-5"],
  checkCircle: [ring, "M8 12l3 3 5-5"],
  clock: [ring, "M12 6v6l4 2"],
  xCircle: [ring, "M15 9l-6 6", "M9 9l6 6"],
  alert: ["M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4", "M12 17h.01"],
  ban: [ring, "M4.9 4.9l14.2 14.2"],
  plus: ["M12 5v14", "M5 12h14"],
  minus: ["M5 12h14"],
  x: ["M18 6L6 18", "M6 6l12 12"],
  save: ["M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z", "M17 21v-8H7v8", "M7 3v5h8"],
  refresh: ["M23 4v6h-6", "M1 20v-6h6", "M3.5 9a9 9 0 0 1 14.9-3.4L23 10", "M1 14l4.6 4.4A9 9 0 0 0 20.5 15"],
  undo: ["M1 4v6h6", "M3.5 15a9 9 0 1 0 2.1-9.4L1 10"],
  eye: ["M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z", "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6z"],
  eyeOff: ["M17.9 17.9A10 10 0 0 1 12 20c-7 0-11-8-11-8a18.5 18.5 0 0 1 5.1-5.9", "M9.9 4.2A9 9 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.2 3.2", "M14.1 14.1a3 3 0 1 1-4.2-4.2", "M1 1l22 22"],
  filter: ["M22 3H2l8 9.5V19l4 2v-8.5z"],
  sort: ["M21 16l-4 4-4-4", "M17 20V4", "M3 8l4-4 4 4", "M7 4v16"],
  zap: ["M13 2L3 14h9l-1 8 10-12h-9z"],
  mail: ["M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M22 6l-10 7L2 6"],
  arrowRight: ["M5 12h14", "M12 5l7 7-7 7"],
  tag: ["M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8z", "M7 7h.01"],
  card: ["M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M1 10h22"],
  calendar: ["M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M16 2v4", "M8 2v4", "M3 10h18"],
  user: ["M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M12 3a4 4 0 1 0 0 8a4 4 0 0 0 0-8z"],
};

/** An icon; decorative, so screen readers skip it and read the text or aria-label beside it. */
export default function Icon({ name, className = "" }) {
  return (
    <svg className={`icon ${className}`} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {PATHS[name].map(d => <path key={d} d={d} />)}
    </svg>
  );
}
