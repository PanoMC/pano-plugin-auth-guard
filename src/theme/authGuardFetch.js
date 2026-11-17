const FLAG = '__panoAuthGuardFetchPatched';
const _orig = typeof globalThis !== 'undefined' && globalThis.fetch
  ? globalThis.fetch.bind(globalThis)
  : null;

const AUTH_CAPTCHA_INJECT = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/verify-link-code',
  '/api/v1/auth/reset-password',
  '/api/v1/auth/verify-email',
  '/api/v1/auth/verify-new-email',
  '/api/v1/auth/renew-password',
  // Plugin-driven auth flows that run the core auth hooks (e.g. social login) get the captcha
  // token too, so captcha enforced server-side on these is satisfiable client-side.
  '/api/plugins/pano-plugin-social-login/register',
  '/api/plugins/pano-plugin-social-login/link-existing',
];

let responseHandler = null;

export function setAuthGuardFetchResponseHandler(fn) {
  responseHandler = typeof fn === 'function' ? fn : null;
}

function toUrlString(input) {
  if (typeof input === 'string') {
    return input;
  }
  if (typeof URL !== 'undefined' && input instanceof URL) {
    return input.href;
  }
  if (typeof Request !== 'undefined' && input instanceof Request) {
    return input.url;
  }
  return '';
}

function isAuthCaptchaPath(url) {
  return url && typeof url === 'string' && AUTH_CAPTCHA_INJECT.some((p) => url.includes(p));
}

function getCaptchaToken() {
  if (typeof window === 'undefined' || !window) {
    return '';
  }
  if (window.__authGuardCaptchaToken) {
    return String(window.__authGuardCaptchaToken);
  }
  return '';
}

/**
 * Theme/ApiUtil requests: `fetch(urlString, { body: jsonString })`.
 * @param {string} _url
 * @param {Request| string | URL} input
 * @param {RequestInit|undefined} init
 */
function mergeStringBody(input, init) {
  if (!init || init.body == null) {
    return { input, init, merged: false, bodyString: null };
  }
  if (init.body instanceof FormData || init.body instanceof Blob) {
    return { input, init, merged: false, bodyString: null };
  }
  if (typeof init.body === 'string') {
    try {
      const o = JSON.parse(init.body);
      if (o == null || typeof o !== 'object' || Array.isArray(o)) {
        return { input, init, merged: false, bodyString: null };
      }
      o.captchaToken = getCaptchaToken();
      const s = JSON.stringify(o);
      return { input, init: { ...init, body: s }, merged: true, bodyString: s };
    } catch (e) {
      return { input, init, merged: false, bodyString: null };
    }
  }
  if (init.body && typeof init.body === 'object') {
    const o = { ...init.body, captchaToken: getCaptchaToken() };
    const s = JSON.stringify(o);
    return { input, init: { ...init, body: s }, merged: true, bodyString: s };
  }
  return { input, init, merged: false, bodyString: null };
}

/**
 * Injected once when the plugin loads; when `window.__authGuardCaptchaToken` is set, it is written onto the request.
 * Theme files (e.g. `auth.js`) are not modified.
 */
export function installAuthGuardFetch() {
  if (_orig == null) {
    return;
  }
  if (typeof window === 'undefined' || !window) {
    return;
  }
  if (window[FLAG] === true) {
    return;
  }
  window[FLAG] = true;
  if (window.__authGuardCaptchaToken == null) {
    window.__authGuardCaptchaToken = '';
  }

  const runHandler = async (res, url, requestOptions) => {
    if (!responseHandler) {
      return;
    }
    let resJson = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('json')) {
      try {
        resJson = await res.clone().json();
      } catch (e) {
        resJson = null;
      }
    }
    try {
      responseHandler({
        urlString: url,
        res,
        resJson,
        requestOptions: requestOptions || { body: '' },
      });
    } catch (e) {
      // no-op
    }
  };

  const bodyStringFromInit = (init) => {
    if (!init || init.body == null) {
      return '';
    }
    if (typeof init.body === 'string') {
      return init.body;
    }
    if (init.body && typeof init.body === 'object') {
      try {
        return JSON.stringify(init.body);
      } catch (e) {
        return '';
      }
    }
    return '';
  };

  globalThis.fetch = async (input, init) => {
    const url = toUrlString(input);
    if (!isAuthCaptchaPath(url)) {
      return _orig(input, init);
    }

    const token = getCaptchaToken();
    let outInput = input;
    let outInit = init;
    let finalBodyString = null;

    if (typeof Request !== 'undefined' && input instanceof Request) {
      if (['GET', 'HEAD'].includes(String(input.method || 'GET').toUpperCase())) {
        const res = await _orig(input, init);
        await runHandler(res, url, { body: '' });
        return res;
      }
      if (!input.body) {
        const res = await _orig(input, init);
        await runHandler(res, url, { body: '' });
        return res;
      }
      let o;
      try {
        const text = await input.clone().text();
        o = text ? JSON.parse(text) : {};
      } catch (e) {
        const res = await _orig(input, init);
        await runHandler(res, url, { body: '' });
        return res;
      }
      if (o == null || typeof o !== 'object' || Array.isArray(o)) {
        const res = await _orig(input, init);
        await runHandler(res, url, { body: '' });
        return res;
      }
      o.captchaToken = token;
      finalBodyString = JSON.stringify(o);
      outInput = new Request(input, { body: finalBodyString });
    } else {
      const m = mergeStringBody(input, init);
      if (!m.merged) {
        const res = await _orig(input, init);
        await runHandler(res, url, { body: bodyStringFromInit(init) });
        return res;
      }
      outInput = input;
      outInit = m.init;
      finalBodyString = m.bodyString;
    }

    const res = await _orig(outInput, outInit);
    await runHandler(res, url, { body: finalBodyString != null && finalBodyString !== '' ? finalBodyString : '' });
    return res;
  };
}

export function isAuthGuardFetchPatched() {
  return typeof window !== 'undefined' && window[FLAG] === true;
}
