// Public auth-guard settings the main entry reads from /config and the theme views read back as stores.
// A view cannot import main.js (it holds the plugin class and the panel pages) nor a package, so the stores live here as
// plain objects that follow the Svelte store contract (`$captchaProvider` and `get(store)` work on them).

/**
 * @template T
 * @param {T} value
 */
function writable(value) {
  /** @type {Set<(value: T) => void>} */
  const subscribers = new Set();

  /** @param {T} next */
  function set(next) {
    if (Object.is(next, value)) return;

    value = next;

    for (const run of [...subscribers]) run(value);
  }

  return {
    set,
    /** @param {(value: T) => T} update */
    update: (update) => set(update(value)),
    /** @param {(value: T) => void} run */
    subscribe(run) {
      subscribers.add(run);
      run(value);

      return () => {
        subscribers.delete(run);
      };
    },
  };
}

export const captchaProvider = writable('NONE');
export const captchaSiteKey = writable('');
export const recaptchaLegacy = writable(false);
export const captchaOnLogin = writable(false);
export const captchaOnRegister = writable(false);
export const twoFactorEnabled = writable(false);
export const magicLoginEnabled = writable(false);

/** Login + link screen: preserve “captcha open” state when the view changes and the widget unmounts. */
export const loginCaptchaDeferred = writable({ active: false, identity: null });

export function setLoginCaptchaDeferred(identity) {
  loginCaptchaDeferred.set({ active: true, identity: identity != null && identity !== '' ? String(identity) : null });
}

export function clearLoginCaptchaDeferred() {
  loginCaptchaDeferred.set({ active: false, identity: null });
}

/** The host API, set by main.js in onLoad, for the pages that need it (the panel's `pano.ui.auth.login.complete`). */
export let pano;

export function setPano(value) {
  pano = value;
}
