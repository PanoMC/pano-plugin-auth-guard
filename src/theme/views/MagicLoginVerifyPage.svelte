<style>
  .auth-guard-magic-login-verify-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2rem;
    text-align: center;
    min-height: 200px;
  }

  .auth-guard-magic-login-verify-page__icon {
    font-size: 3rem;
    margin-bottom: 1rem;
  }

  .auth-guard-magic-login-verify-page__shake {
    animation: auth-guard-shake 250ms both;
  }

  @keyframes auth-guard-shake {
    from, to { transform: translate3d(0, 0, 0); }
    10%, 30%, 50%, 70%, 90% { transform: translate3d(-10px, 0, 0); }
    20%, 40%, 60%, 80% { transform: translate3d(10px, 0, 0); }
  }

  .auth-guard-magic-login-verify-page__bounce-in {
    animation: auth-guard-bounce-in 250ms both;
  }

  @keyframes auth-guard-bounce-in {
    from, 20%, 40%, 60%, 80%, to { animation-timing-function: cubic-bezier(0.215, 0.61, 0.355, 1); }
    0% { opacity: 0; transform: scale3d(0.3, 0.3, 0.3); }
    20% { transform: scale3d(1.1, 1.1, 1.1); }
    40% { transform: scale3d(0.9, 0.9, 0.9); }
    60% { opacity: 1; transform: scale3d(1.03, 1.03, 1.03); }
    80% { transform: scale3d(0.97, 0.97, 0.97); }
    to { opacity: 1; transform: scale3d(1, 1, 1); }
  }
</style>

<div class="auth-guard-magic-login-verify-page">
  {#if state === 'loading'}
    <div class="auth-guard-magic-login-verify-page__icon is-loading">
      <span class="spinner-border" role="status"></span>
    </div>
    <p class="text-muted">{$_('magic-login.verifying')}</p>
  {:else if state === 'success'}
    <div class="auth-guard-magic-login-verify-page__icon is-success auth-guard-magic-login-verify-page__bounce-in">
      <i class="fas fa-check-circle"></i>
    </div>
    <h5>{$_('magic-login.success')}</h5>
    <p class="text-muted small">{$_('magic-login.verifying')}</p>
  {:else if state === 'totp'}
    <form class="auth-guard-magic-login-verify-page__totp" onsubmit={(e) => { e.preventDefault(); submitTotp(); }}>
      <h5 class="auth-guard-magic-login-verify-page__totp-title"><i class="fas fa-shield-halved me-2"></i>{$_('two-factor.login-title')}</h5>
      <p class="auth-guard-magic-login-verify-page__totp-description text-muted small">{$_('two-factor.login-desc')}</p>
      <input
        class="auth-guard-magic-login-verify-page__totp-input form-control text-center"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        placeholder="000000"
        bind:value={totpCode}
        oninput={onTotpInput}
        disabled={verifying} />
      {#if totpError}
        <div class="auth-guard-magic-login-verify-page__totp-error text-danger small mt-2">{totpError}</div>
      {/if}
      <button
        class="auth-guard-magic-login-verify-page__totp-submit btn btn-secondary mt-3"
        type="submit"
        disabled={verifying || totpCode.length !== 6}>
        {$_('two-factor.login-verify')}
      </button>
    </form>
  {:else if state === 'no-panel-access'}
    <div class="auth-guard-magic-login-verify-page__icon is-error auth-guard-magic-login-verify-page__shake">
      <i class="fas fa-times-circle"></i>
    </div>
    <h5>{$_('magic-login.no-panel-access')}</h5>
    <a href={loginHref} class="auth-guard-magic-login-verify-page__action btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {:else if state === 'expired'}
    <div class="auth-guard-magic-login-verify-page__icon is-error auth-guard-magic-login-verify-page__shake">
      <i class="fas fa-times-circle"></i>
    </div>
    <h5>{$_('magic-login.expired')}</h5>
    <a href={loginHref} class="auth-guard-magic-login-verify-page__back-to-login btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {:else if state === 'error'}
    <div class="auth-guard-magic-login-verify-page__icon is-error auth-guard-magic-login-verify-page__shake">
      <i class="fas fa-exclamation-triangle"></i>
    </div>
    <h5>{$_('magic-login.error')}</h5>
    <a href={loginHref} class="auth-guard-magic-login-verify-page__action-2 btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {/if}
</div>

<script module>
  // The page the magic-link mail lands on; registered for the theme by this declaration (the panel's own sign-in page
  // registers it from main.js).
  export const view = { path: '/auth-guard/magic-login' };
</script>

<script>
  import { onMount } from 'svelte';
  import { derived } from 'svelte/store';
  import { _ as i18n } from '@panomc/sdk/utils/language';
  import { pano } from '../state.js';

  // plugin translations: $_('key') reads plugins.pano-plugin-auth-guard.key
  const _ = derived(i18n, ($_fn) => (key, options) => $_fn(`plugins.pano-plugin-auth-guard.${key}`, options));

  let state = $state('loading'); // loading, totp, success, expired, error, no-panel-access
  let token = null;
  let totpCode = $state('');
  let totpError = $state('');
  let verifying = $state(false);

  // On a SERVERS install this page is served by the panel, which has its own sign-in page.
  const loginHref = pano?.isPanel ? '/panel/login' : '/login';

  const TWO_FACTOR_REQUIRED_REASON = 'plugins.pano-plugin-auth-guard.errors.two-factor-required';

  function deniedReason(body) {
    return body?.error?.code === 'PLUGIN_DENIED_LOGIN' ? body.error.details?.reason : null;
  }

  // POST, so a mail scanner following the link with a GET cannot use it up. The 2FA code, once asked for, goes
  // in the same request.
  async function send(extra = {}) {
    const res = await fetch('/api/plugins/pano-plugin-auth-guard/magic-login/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, ...extra }),
    });
    return res.json();
  }

  async function handle(body) {
    if (!body.error) {
      state = 'success';

      // The panel checks panel access and opens the dashboard itself.
      if (pano?.isPanel && pano.ui.auth?.login?.complete) {
        const result = await pano.ui.auth.login.complete(body.csrfToken);
        if (result === 'NO_PANEL_ACCESS') {
          state = 'no-panel-access';
        }
        return;
      }

      // Redirect to home after a short delay — full page load picks up the new session
      setTimeout(() => {
        window.location.href = '/';
      }, 1500);
      return;
    }

    const reason = deniedReason(body);
    if (reason === TWO_FACTOR_REQUIRED_REASON || reason === 'TWO_FACTOR_REQUIRED') {
      state = 'totp';
      return;
    }
    if (state === 'totp' && reason) {
      totpCode = '';
      totpError = $_(reason.replace('plugins.pano-plugin-auth-guard.', ''));
      return;
    }

    state = body.error?.code === 'MAGIC_LOGIN_LINK_EXPIRED' ? 'expired' : 'error';
  }

  function onTotpInput() {
    totpError = '';
    totpCode = totpCode.replace(/\D/g, '').slice(0, 6);
    if (totpCode.length === 6) {
      submitTotp();
    }
  }

  async function submitTotp() {
    if (totpCode.length !== 6 || verifying) return;
    verifying = true;
    try {
      await handle(await send({ totpCode }));
    } catch (e) {
      console.error('[auth-guard] Magic login verify failed', e);
      state = 'error';
    }
    verifying = false;
  }

  onMount(async () => {
    const params = new URLSearchParams(window.location.search);
    token = params.get('token');

    if (!token) {
      state = 'error';
      return;
    }

    try {
      await handle(await send());
    } catch (e) {
      console.error('[auth-guard] Magic login verify failed', e);
      state = 'error';
    }
  });
</script>
