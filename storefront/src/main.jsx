// The MyShoppingCart storefront, a React single-page app. React writes data as text, never as
// markup, so product names and other data cannot inject script.
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { completeSignIn } from "./auth.js";
import "./style.css";

let signInError = null;
try {
  const returnTo = await completeSignIn();
  if (returnTo) {
    location.hash = returnTo;
  }
} catch (error) {
  signInError = error.message;
}
createRoot(document.getElementById("root")).render(<App signInError={signInError} />);
