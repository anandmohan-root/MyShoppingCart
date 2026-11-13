// Browser sign-in with Keycloak: authorization code flow with PKCE, no client secret.
// Keycloak is reached through Kong on this same origin, so tokens carry the issuer the
// services expect (http://localhost:8000/realms/shop).

const OIDC = "/realms/shop/protocol/openid-connect";
const CLIENT_ID = "shop-web";
const REDIRECT_URI = location.origin + "/";

// Tokens live in sessionStorage: they survive a reload but not closing the tab. The page's
// Content-Security-Policy allows no inline or third-party script, which limits who could read them.
const store = sessionStorage;

function base64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomString(byteCount) {
  return base64url(crypto.getRandomValues(new Uint8Array(byteCount)));
}

/** Sends the browser to Keycloak's sign-in page. */
export async function signIn() {
  const verifier = randomString(32);
  const state = randomString(16);
  store.setItem("pkce", JSON.stringify({ verifier, state, returnTo: location.hash }));
  const challenge = base64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: "code",
    scope: "openid",
    redirect_uri: REDIRECT_URI,
    state,
    code_challenge: challenge,
    code_challenge_method: "S256",
  });
  location.assign(`${OIDC}/auth?${params}`);
}

/** Finishes sign-in when Keycloak sends the browser back with a code. Returns the page to show. */
export async function completeSignIn() {
  const query = new URLSearchParams(location.search);
  if (!query.has("code") && !query.has("error")) {
    return null;
  }
  const saved = JSON.parse(store.getItem("pkce") || "null");
  store.removeItem("pkce");
  history.replaceState(null, "", "/" + (saved?.returnTo || ""));
  if (query.has("error")) {
    throw new Error("Sign-in failed: " + (query.get("error_description") || query.get("error")));
  }
  if (!saved || query.get("state") !== saved.state) {
    throw new Error("Sign-in failed: the response did not match this browser's request. Try again.");
  }
  await requestTokens({
    grant_type: "authorization_code",
    code: query.get("code"),
    redirect_uri: REDIRECT_URI,
    code_verifier: saved.verifier,
  });
  return saved.returnTo;
}

async function requestTokens(form) {
  const response = await fetch(`${OIDC}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: CLIENT_ID, ...form }),
  });
  if (!response.ok) {
    throw new Error("Sign-in failed (" + response.status + ")");
  }
  const tokens = await response.json();
  store.setItem("tokens", JSON.stringify({
    access: tokens.access_token,
    refresh: tokens.refresh_token,
    expiresAt: Date.now() + tokens.expires_in * 1000,
  }));
}

function savedTokens() {
  return JSON.parse(store.getItem("tokens") || "null");
}

/** A valid access token, refreshed if it is about to expire; null if the user must sign in again. */
export async function accessToken() {
  const tokens = savedTokens();
  if (!tokens) {
    return null;
  }
  if (Date.now() < tokens.expiresAt - 30_000) {
    return tokens.access;
  }
  try {
    await requestTokens({ grant_type: "refresh_token", refresh_token: tokens.refresh });
    return savedTokens().access;
  } catch {
    store.removeItem("tokens");
    return null;
  }
}

/** The signed-in user's name and roles, read from the access token; null if signed out. */
export function currentUser() {
  const tokens = savedTokens();
  if (!tokens) {
    return null;
  }
  const payload = tokens.access.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const claims = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(payload), c => c.charCodeAt(0))));
  return {
    name: claims.preferred_username,
    isAdmin: (claims.realm_access?.roles || []).includes("admin"),
  };
}

/**
 * Ends the Keycloak session too, so the next sign-in asks for a password again. No id_token_hint
 * is sent: Keycloak in dev mode makes new signing keys whenever it restarts, which would make an
 * older ID token fail the sign-out. Keycloak asks the user to confirm instead.
 */
export function signOut() {
  store.removeItem("tokens");
  const params = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: REDIRECT_URI });
  location.assign(`${OIDC}/logout?${params}`);
}
