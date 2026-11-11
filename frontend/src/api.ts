/**
 * Shared fetch helpers for the backend API.
 *
 * Spring Security issues the CSRF token in a readable `XSRF-TOKEN` cookie and expects it back
 * verbatim in the `X-XSRF-TOKEN` header on every state-changing request. All backend calls should
 * go through {@link apiFetch} so the header is attached automatically.
 */

export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Methods that Spring Security's CsrfFilter lets through without a token. */
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

/** Reads the CSRF token from the cookie jar, or `null` when the backend has not issued one yet. */
export function getCsrfToken(cookieSource: string = document.cookie): string | null {
  for (const part of cookieSource.split(';')) {
    const separator = part.indexOf('=');
    if (separator === -1) continue;
    if (part.slice(0, separator).trim() !== CSRF_COOKIE) continue;
    const value = part.slice(separator + 1).trim();
    return value ? decodeURIComponent(value) : null;
  }
  return null;
}

/** `fetch` for same-origin backend calls that adds the CSRF header on non-safe methods. */
export function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.has(method)) {
    const token = getCsrfToken();
    if (token) headers.set(CSRF_HEADER, token);
  }
  return fetch(input, { credentials: 'same-origin', ...init, headers });
}

/**
 * Ends the server session. Spring Security only accepts `POST /logout` once CSRF protection is on
 * and answers with a redirect to `/`, which is not followed here; callers reload the app instead.
 */
export async function logout(): Promise<void> {
  const res = await apiFetch('/logout', { method: 'POST', redirect: 'manual' });
  if (res.type !== 'opaqueredirect' && !res.ok) {
    throw new Error(`Logout failed: server returned ${res.status}`);
  }
}
