<style>
  .auth-guard-captcha-widget {
    display: flex;
    justify-content: center;
    margin-top: 0.5rem;
  }

  .auth-guard-captcha-widget__error {
    text-align: center;
    font-size: 0.875rem;
  }
</style>

{#if provider !== 'NONE' && showCaptchaPanel}
  <div class="auth-guard-captcha-widget">
    {#if error}
      <div class="auth-guard-captcha-widget__error text-danger">
        <i class="fas fa-exclamation-triangle me-1"></i>
        {$_(error)}
      </div>
    {:else}
      <div bind:this={widgetContainer}></div>
    {/if}
  </div>
{/if}

<script>
  import { onMount, untrack } from 'svelte';
  import { derived, get } from 'svelte/store';
  import { _ as i18n } from '@panomc/sdk/utils/language';

  import { installAuthGuardFetch, isAuthGuardFetchPatched, setAuthGuardFetchResponseHandler } from '../authGuardFetch.js';
  import {
    captchaProvider,
    captchaSiteKey,
    recaptchaLegacy,
    clearLoginCaptchaDeferred,
    loginCaptchaDeferred,
    setLoginCaptchaDeferred,
  } from '../state.js';

  // plugin translations: $_('key') reads plugins.pano-plugin-auth-guard.key
  const _ = derived(i18n, ($_fn) => (key, options) => $_fn(`plugins.pano-plugin-auth-guard.${key}`, options));

  let { data = {} } = $props();

  const isLoginContext = $derived(data?.pageType === 'login');
  const isRegisterContext = $derived((data?.pageType ?? '') === 'register');

  // Login pages on this theme are two-step (username first, password reveal on next submit);
  // captcha is hidden until after the first request so the user isn't asked to solve a challenge
  // before there's any credential to validate. Plugin-hosted pages that present the credential
  // form in a single step (e.g. social-login's link-existing form, magic-link confirm) can opt
  // out by passing `data.deferred = false`, mounting the captcha immediately.
  const showCaptchaPanel = $derived(
    isLoginContext && data?.deferred !== false
      ? $loginCaptchaDeferred.active
      : true,
  );

  let provider = $state('');
  let siteKey = $state('');
  let isLegacy = $state(false);
  let widgetContainer;
  let widgetId;
  let loading = $state(true);
  let error = $state('');

  // Global token store — other components read this
  if (typeof window !== 'undefined' && !window.__authGuardCaptchaToken) {
    window.__authGuardCaptchaToken = '';
  }

  // Subscribe to stores
  const unsubProvider = captchaProvider.subscribe(v => provider = v);
  const unsubSiteKey = captchaSiteKey.subscribe(v => siteKey = v);
  const unsubLegacy = recaptchaLegacy.subscribe(v => isLegacy = v);

  /**
   * After a failed login/register, the provider token is single-use — clear and reset the widget
   * so the user can solve again. Skip while 2FA is in progress (server uses captchaStepToken).
   */
  function shouldResetCaptchaAfterAuthError(responseBody, requestOptions, requestUrl) {
    if (!responseBody?.error) return false;
    if (responseBody.error.code === 'LINK_CODE_REQUIRED') {
      return false;
    }
    if (responseBody.error.code === 'PLUGIN_DENIED_LOGIN') {
      const r = responseBody.error.details?.reason;
      if (r === 'TWO_FACTOR_REQUIRED') return false;
      if (r === 'plugins.pano-plugin-auth-guard.errors.two-factor-required') return false;
      if (typeof r === 'string' && r.includes('two-factor')) return false;
    }
    let req;
    try {
      req = requestOptions?.body && JSON.parse(requestOptions.body);
    } catch (e) {
      return true;
    }
    if (req?.captchaStepToken) return false;
    // Login "username first" step sends blank password — do not clear a solved captcha
    if (typeof requestUrl === 'string' && requestUrl.includes('/api/v1/auth/login')) {
      const p = req?.password;
      if (p == null || String(p).trim() === '') {
        return false;
      }
    }
    return true;
  }

  /** Edit/loading: even when disabled, `.value` is unchanged; needed for snapshot comparison. */
  function readLoginUsernameValue() {
    if (typeof document === 'undefined') {
      return null;
    }
    const el = document.getElementById('usernameOrEmail');
    if (!el) {
      return null;
    }
    return el.value;
  }

  function identitySnapshotFromRequest(requestUrl, requestOptions) {
    if (!requestOptions?.body) {
      return null;
    }
    try {
      const b = JSON.parse(requestOptions.body);
      if (typeof requestUrl === 'string' && requestUrl.includes('/api/v1/auth/login')) {
        return b.usernameOrEmail != null ? String(b.usernameOrEmail) : null;
      }
      if (typeof requestUrl === 'string' && requestUrl.includes('/api/v1/auth/verify-link-code')) {
        return b.username != null ? String(b.username) : null;
      }
    } catch (e) {
      // ignore
    }
    return null;
  }

  function markAfterFirstLoginRequest(requestUrl, requestOptions) {
    if (!isLoginContext) {
      return;
    }
    const id = identitySnapshotFromRequest(requestUrl, requestOptions) ?? readLoginUsernameValue();
    setLoginCaptchaDeferred(id);
  }

  function clearLoginCaptchaStep() {
    if (!isLoginContext) {
      return;
    }
    clearLoginCaptchaDeferred();
  }

  function hideLoginCaptchaIfUsernameChanged() {
    if (!isLoginContext) {
      return;
    }
    const { identity: snap } = get(loginCaptchaDeferred);
    if (snap == null) {
      return;
    }
    const current = readLoginUsernameValue();
    if (current === null) {
      return;
    }
    if (current === snap) {
      return;
    }
    clearLoginCaptchaStep();
    window.__authGuardCaptchaToken = '';
    error = '';
    resetCaptchaWidget();
    widgetId = undefined;
  }

  /** Non-login: register, reset password, renew password; fresh captcha when identity/field changes. */
  function clearSolvedNonLoginCaptcha() {
    if (isLoginContext) {
      return;
    }
    window.__authGuardCaptchaToken = '';
    resetCaptchaWidget();
  }

  function resetCaptchaWidget() {
    window.__authGuardCaptchaToken = '';
    if (widgetId == null) return;
    try {
      const recaptchaApi = isLegacy ? window.grecaptcha : window.grecaptcha?.enterprise;
      switch (provider) {
        case 'RECAPTCHA':
          if (recaptchaApi?.reset) {
            recaptchaApi.reset(widgetId);
          }
          break;
        case 'HCAPTCHA':
          window.hcaptcha?.reset?.(widgetId);
          break;
        case 'TURNSTILE':
          window.turnstile?.reset?.(widgetId);
          break;
      }
    } catch (e) {
      console.error('[auth-guard] reset captcha', e);
    }
  }

  onMount(() => {
    if (typeof globalThis !== 'undefined' && globalThis.window) {
      if (!isAuthGuardFetchPatched()) {
        installAuthGuardFetch();
      }
    }
    let findFieldsInterval;
    let detachLoginUser;
    let detachRegUser;
    const attachIdentityListeners = () => {
      const loginEl = document.getElementById('usernameOrEmail');
      if (loginEl && !loginEl.dataset.authGuardIdentityListener) {
        loginEl.dataset.authGuardIdentityListener = '1';
        const onEdit = () => {
          hideLoginCaptchaIfUsernameChanged();
        };
        loginEl.addEventListener('input', onEdit);
        loginEl.addEventListener('change', onEdit);
        detachLoginUser = () => {
          delete loginEl.dataset.authGuardIdentityListener;
          loginEl.removeEventListener('input', onEdit);
          loginEl.removeEventListener('change', onEdit);
        };
      }
      const regEl = document.getElementById('registerUserName');
      if (regEl && !regEl.dataset.authGuardIdentityListener) {
        regEl.dataset.authGuardIdentityListener = '1';
        const onRegChange = () => {
          clearSolvedNonLoginCaptcha();
        };
        regEl.addEventListener('change', onRegChange);
        detachRegUser = () => {
          delete regEl.dataset.authGuardIdentityListener;
          regEl.removeEventListener('change', onRegChange);
        };
      }
    };
    let findTries = 0;
    findFieldsInterval = setInterval(() => {
      findTries += 1;
      attachIdentityListeners();
      if (findTries > 150) {
        clearInterval(findFieldsInterval);
        findFieldsInterval = null;
      }
    }, 100);

    function looksLikeLinkCodeForm(form) {
      if (!form || typeof form.querySelector !== 'function') {
        return false;
      }
      const u = form.querySelector('#usernameOrEmail');
      if (!u || !u.disabled) {
        return false;
      }
      // Typical link step: six short fields (or six digits), inputmode="numeric" (theme/LinkCodeInput)
      return form.querySelectorAll('input[inputmode="numeric"]').length >= 6;
    }

    function clearDeferredOnHostNavigation() {
      if (!isLoginContext) {
        return;
      }
      clearLoginCaptchaDeferred();
      window.__authGuardCaptchaToken = '';
    }

    function onClickCapture(/** @type {MouseEvent} */ e) {
      if (!isLoginContext) {
        return;
      }
      const el = e.target;
      if (!el || el.nodeType !== 1) {
        return;
      }
      if (el instanceof HTMLInputElement) {
        return;
      }
      if (!(el instanceof HTMLButtonElement)) {
        return;
      }
      if (el.type === 'submit') {
        return;
      }
      const form = el.closest('form');
      if (!form || !looksLikeLinkCodeForm(form)) {
        return;
      }
      window.setTimeout(() => {
        clearLoginCaptchaDeferred();
        window.__authGuardCaptchaToken = '';
      }, 0);
    }

    document.addEventListener('click', onClickCapture, true);

    // No theme/kit import: when the route (pathname) changes in the same tab, clear deferred.
    // viewState-only transitions (LOGIN↔LINK_CODE) do not change pathname; back from link: click-capture.
    let lastPathname = typeof location !== 'undefined' ? location.pathname : '';
    const pathPoll = setInterval(() => {
      if (!isLoginContext || typeof location === 'undefined') {
        return;
      }
      if (!get(loginCaptchaDeferred).active) {
        lastPathname = location.pathname;
        return;
      }
      if (location.pathname === lastPathname) {
        return;
      }
      lastPathname = location.pathname;
      clearDeferredOnHostNavigation();
    }, 400);

    setAuthGuardFetchResponseHandler((ctx) => {
      const { urlString: requestUrl, requestOptions: options, resJson } = ctx;
      if (resJson == null) {
        if (
          typeof requestUrl === 'string' &&
          (requestUrl.includes('/api/v1/auth/login') || requestUrl.includes('/api/v1/auth/verify-link-code'))
        ) {
          markAfterFirstLoginRequest(requestUrl, options);
        }
        return;
      }
      if (requestUrl.includes('/api/v1/auth/login')) {
        if (!resJson?.error) {
          clearLoginCaptchaStep();
        } else {
          markAfterFirstLoginRequest(requestUrl, options);
        }
      } else if (requestUrl.includes('/api/v1/auth/verify-link-code')) {
        if (!resJson?.error) {
          clearLoginCaptchaStep();
        } else {
          markAfterFirstLoginRequest(requestUrl, options);
        }
      }
      if (shouldResetCaptchaAfterAuthError(resJson, options, requestUrl)) {
        resetCaptchaWidget();
      }
    });

    // Cleanup on destroy (client-only)
    return () => {
      clearInterval(pathPoll);
      document.removeEventListener('click', onClickCapture, true);
      if (findFieldsInterval) {
        clearInterval(findFieldsInterval);
      }
      detachLoginUser?.();
      detachRegUser?.();

      setAuthGuardFetchResponseHandler(null);

      unsubProvider();
      unsubSiteKey();
      unsubLegacy();

      window.__authGuardCaptchaToken = '';
    };
  });

  $effect(() => {
    if (provider === 'NONE' || !siteKey) {
      loading = false;
      return;
    }
    if (!showCaptchaPanel) {
      return;
    }
    untrack(() => loadCaptchaScript());
  });

  function loadCaptchaScript() {
    let url;
    if (provider === 'RECAPTCHA') {
      url = isLegacy
        ? 'https://www.google.com/recaptcha/api.js?render=explicit'
        : 'https://www.google.com/recaptcha/enterprise.js?render=explicit';
    } else if (provider === 'HCAPTCHA') {
      url = 'https://js.hcaptcha.com/1/api.js?render=explicit';
    } else if (provider === 'TURNSTILE') {
      url = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
    }

    if (!url) {
      loading = false;
      return;
    }

    // Check if script already loaded
    const existingScript = document.querySelector(`script[src*="${url.split('?')[0]}"]`);
    if (existingScript) {
      waitForApiAndRender();
      return;
    }

    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.defer = true;
    script.onload = () => waitForApiAndRender();
    script.onerror = () => {
      error = 'captcha.load-failed';
      loading = false;
    };
    document.head.appendChild(script);
  }

  function waitForApiAndRender() {
    const maxWait = 10000;
    const interval = 100;
    let waited = 0;

    const check = setInterval(() => {
      waited += interval;
      if (waited > maxWait) {
        clearInterval(check);
        error = 'captcha.init-failed';
        loading = false;
        return;
      }

      if (isApiReady()) {
        clearInterval(check);
        loading = false;
        // Wait for DOM to update
        setTimeout(() => renderWidget(), 50);
      }
    }, interval);
  }

  function isApiReady() {
    switch (provider) {
      case 'RECAPTCHA':
        if (isLegacy) {
          return typeof window.grecaptcha !== 'undefined' && typeof window.grecaptcha.render === 'function';
        }
        return typeof window.grecaptcha !== 'undefined' && typeof window.grecaptcha.enterprise !== 'undefined' && typeof window.grecaptcha.enterprise.render === 'function';
      case 'HCAPTCHA':
        return typeof window.hcaptcha !== 'undefined' && typeof window.hcaptcha.render === 'function';
      case 'TURNSTILE':
        return typeof window.turnstile !== 'undefined' && typeof window.turnstile.render === 'function';
      default:
        return false;
    }
  }

  function renderWidget() {
    if (!widgetContainer) return;

    try {
      const callback = (token) => {
        window.__authGuardCaptchaToken = token;
      };

      const expiredCallback = () => {
        window.__authGuardCaptchaToken = '';
      };

      switch (provider) {
        case 'RECAPTCHA': {
          const g = window.grecaptcha;
          const recaptchaApi = isLegacy ? g : g?.enterprise;
          if (recaptchaApi == null || typeof recaptchaApi.render !== 'function') {
            throw new Error('reCAPTCHA API not available');
          }
          widgetId = recaptchaApi.render(widgetContainer, {
            sitekey: siteKey,
            callback,
            'expired-callback': expiredCallback,
          });
          break;
        }
        case 'HCAPTCHA':
          widgetId = window.hcaptcha.render(widgetContainer, {
            sitekey: siteKey,
            callback,
            'expired-callback': expiredCallback,
          });
          break;
        case 'TURNSTILE':
          widgetId = window.turnstile.render(widgetContainer, {
            sitekey: siteKey,
            callback,
            'expired-callback': expiredCallback,
          });
          break;
      }
    } catch (e) {
      console.error('[auth-guard] Failed to render captcha widget', e);
      error = 'captcha.render-failed';
    }
  }
</script>
