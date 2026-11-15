import { accessToken, signIn } from "./auth.js";

/** Calls the API through Kong with the user's token. A 401 sends the user to sign in again. */
export async function api(path, { method = "GET", body } = {}) {
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

/**
 * Calls onChange whenever order-service pushes a status change (GET /orders/events, a
 * server-sent event stream). fetch is used instead of EventSource because EventSource cannot send
 * the Authorization header. onChange also runs once each time the stream opens, so no change is
 * missed while connecting; the server ends each stream after about 50 seconds and this reconnects.
 */
export async function followOrders(stop, onChange) {
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
      // The stream is registered before the server answers, so this catches any change made
      // between drawing the page and opening the stream.
      await onChange();
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      for (let chunk = await reader.read(); !chunk.done; chunk = await reader.read()) {
        if (chunk.value.includes("event:order-status")) {
          await onChange();
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
