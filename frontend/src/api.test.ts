import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, getCsrfToken, logout } from './api';

const TOKEN = '3f1c2b7a-9d4e-4c0a-8b6f-1e2d3c4b5a69';

function setCookie(value: string) {
  // jsdom honours document.cookie assignments, so this mirrors Set-Cookie from the backend.
  document.cookie = `XSRF-TOKEN=${value}; path=/`;
}

function clearCookie() {
  document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
}

describe('getCsrfToken', () => {
  it('returns the XSRF-TOKEN value from a cookie header', () => {
    expect(getCsrfToken(`JSESSIONID=abc; XSRF-TOKEN=${TOKEN}; theme=dark`)).toBe(TOKEN);
  });

  it('decodes percent-encoded values', () => {
    expect(getCsrfToken('XSRF-TOKEN=a%2Fb%3Dc')).toBe('a/b=c');
  });

  it('ignores cookies whose name merely contains the token name', () => {
    expect(getCsrfToken('MY-XSRF-TOKEN=nope; XSRF-TOKEN-OLD=nope')).toBeNull();
  });

  it('returns null when the cookie is absent or empty', () => {
    expect(getCsrfToken('')).toBeNull();
    expect(getCsrfToken('JSESSIONID=abc')).toBeNull();
    expect(getCsrfToken('XSRF-TOKEN=')).toBeNull();
  });

  it('reads document.cookie by default', () => {
    setCookie(TOKEN);
    try {
      expect(getCsrfToken()).toBe(TOKEN);
    } finally {
      clearCookie();
    }
  });
});

type FetchMock = (input: string, init?: RequestInit) => Promise<Response>;

describe('apiFetch', () => {
  const fetchMock = vi.fn<FetchMock>(async () => new Response('[]', { status: 200 }));

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockClear();
    setCookie(TOKEN);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearCookie();
  });

  function sentHeaders(): Headers {
    return new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
  }

  it('does not add the CSRF header to GET requests', async () => {
    await apiFetch('/api/devices');

    expect(fetchMock).toHaveBeenCalledWith('/api/devices', expect.objectContaining({ credentials: 'same-origin' }));
    expect(sentHeaders().has('X-XSRF-TOKEN')).toBe(false);
  });

  it('adds the CSRF header to non-safe requests', async () => {
    await apiFetch('/api/devices', { method: 'post', headers: { Accept: 'application/json' } });

    const headers = sentHeaders();
    expect(headers.get('X-XSRF-TOKEN')).toBe(TOKEN);
    expect(headers.get('Accept')).toBe('application/json');
  });

  it('sends non-safe requests without the header when no token has been issued yet', async () => {
    clearCookie();

    await apiFetch('/api/devices', { method: 'DELETE' });

    expect(sentHeaders().has('X-XSRF-TOKEN')).toBe(false);
  });
});

describe('logout', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    clearCookie();
  });

  it('POSTs to /logout with the CSRF header and treats the redirect as success', async () => {
    setCookie(TOKEN);
    const redirect = { type: 'opaqueredirect', ok: false, status: 0 } as Response;
    const fetchMock = vi.fn<FetchMock>(async () => redirect);
    vi.stubGlobal('fetch', fetchMock);

    await expect(logout()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/logout');
    expect(init?.method).toBe('POST');
    expect(init?.redirect).toBe('manual');
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe(TOKEN);
  });

  it('rejects when the server refuses the logout', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 403 })));

    await expect(logout()).rejects.toThrow('Logout failed: server returned 403');
  });
});
