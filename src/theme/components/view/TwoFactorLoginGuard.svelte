<style>
  .totp-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(4px);
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
    animation: fadeIn 0.2s ease;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .totp-card {
    background: var(--bs-body-bg, #fff);
    border-radius: 1rem;
    padding: 2rem;
    max-width: 380px;
    width: 90%;
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.3);
    text-align: center;
  }

  .totp-card h5 {
    margin-bottom: 0.25rem;
  }

  .totp-card p {
    font-size: 0.875rem;
    color: var(--bs-secondary);
    margin-bottom: 1.25rem;
  }

  .totp-input {
    font-size: 1.5rem;
    letter-spacing: 0.5rem;
    text-align: center;
    padding: 0.75rem;
    border-radius: 0.5rem;
  }

  .totp-error {
    color: var(--bs-danger);
    font-size: 0.85rem;
    margin-top: 0.5rem;
  }
</style>

{#if showTotp}
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <div class="totp-overlay" on:click|self={cancel}>
    <div class="totp-card">
      <h5><i class="fas fa-shield-halved me-2"></i>{$_('two-factor.login-title')}</h5>
      <p>{$_('two-factor.login-desc')}</p>

      <input
        bind:this={totpInput}
        bind:value={totpCode}
        class="form-control totp-input"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        placeholder="000000"
        disabled={verifying}
        on:input={onInput}
        on:keydown={(e) => e.key === 'Escape' && cancel()}
      />

      {#if totpError}
        <div class="totp-error">{$_('two-factor.login-invalid-code')}</div>
      {/if}

      <div class="d-flex gap-2 mt-3">
        <button class="btn btn-outline-secondary flex-fill" type="button" on:click={cancel} disabled={verifying}>
          {$_('two-factor.login-cancel')}
        </button>
        <button
          class="btn btn-secondary flex-fill"
          type="button"
          on:click={verify}
          disabled={verifying || totpCode.length !== 6}
        >
          {#if verifying}
            <span class="spinner-border spinner-border-sm me-1" role="status"></span>
          {/if}
          {$_('two-factor.login-verify')}
        </button>
      </div>
    </div>
  </div>
{/if}

<script>
  import { onMount, onDestroy, tick } from 'svelte';
  import { _ } from '../../../main.js';

  let showTotp = $state(false);
  let totpCode = $state('');
  let totpError = $state(false);
  let verifying = $state(false);
  let totpInput;

  // Store the pending login request so we can retry with totpCode
  let pendingUrl = null;
  let pendingOptions = null;
  let pendingResolve = null;
  /** From TWO_FACTOR_REQUIRED — bypasses reusing one-time captcha on the 2FA step */
  let pendingCaptchaStepToken = null;

  let originalFetch;

  /** API extras.reason + cancel() synthetic response — i18n key in plugin locales */
  const TWO_FACTOR_REQUIRED_REASON = 'plugins.pano-plugin-auth-guard.errors.two-factor-required';

  function isTwoFactorRequiredResponse(body) {
    return (
      body?.error === 'PLUGIN_DENIED_LOGIN' &&
      (body.reason === 'TWO_FACTOR_REQUIRED' || body.reason === TWO_FACTOR_REQUIRED_REASON)
    );
  }

  if (typeof window !== 'undefined') {
    onMount(() => {
      // Monkey-patch fetch to intercept ANY response that asks for a second factor. We don't
      // filter by URL — `TWO_FACTOR_REQUIRED` can only come from this plugin's onBeforeLogin
      // hook, so the response shape is a sufficient and stable contract. This lets the modal
      // pop on the core `/api/auth/login`, the platform's `/api/auth/complete-pending` (used by
      // social login, magic link, …), and any future endpoint that runs the auth-completion
      // pipeline — without auth-guard needing an explicit URL allowlist.
      originalFetch = window.fetch;
      window.fetch = async function(url, options) {
        const response = await originalFetch.call(this, url, options);

        // Cheap pre-check: only POSTs (with a body) ever trigger onBeforeLogin, and we need
        // JSON to inspect the result. Anything else passes through untouched.
        const method = (options?.method || (url instanceof Request ? url.method : 'GET') || 'GET').toUpperCase();
        if (method !== 'POST') {
          return response;
        }
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('json')) {
          return response;
        }

        const cloned = response.clone();
        try {
          const body = await cloned.json();

          if (isTwoFactorRequiredResponse(body)) {
            // Store the original request for retry (preserves the endpoint, so the modal works
            // for /api/auth/login, /api/auth/complete-pending, or any other entry adapter).
            pendingUrl = url;
            pendingOptions = options ? { ...options } : {};
            pendingCaptchaStepToken = body.captchaStepToken ?? null;

            // Show TOTP dialog and wait for user input
            return new Promise((resolve) => {
              pendingResolve = resolve;
              showTotp = true;
              totpCode = '';
              totpError = false;
              tick().then(() => totpInput?.focus());
            });
          }

          if (body.error === 'PLUGIN_DENIED_LOGIN' && body.reason === 'plugins.pano-plugin-auth-guard.errors.two-factor-invalid-code') {
            // Still in 2FA flow — show error, keep dialog open
            if (showTotp) {
              totpCode = '';
              totpError = true;
              verifying = false;
              tick().then(() => totpInput?.focus());

              // Return a new promise that waits for the next attempt
              return new Promise((resolve) => {
                pendingResolve = resolve;
              });
            }
          }
        } catch (e) {
          // Not JSON, pass through
        }

        return response;
      };
    });

    onDestroy(() => {
      if (originalFetch) {
        window.fetch = originalFetch;
      }
    });
  }

  function onInput() {
    totpError = false;
    totpCode = totpCode.replace(/\D/g, '').slice(0, 6);

    // Auto-verify on 6 digits
    if (totpCode.length === 6) {
      verify();
    }
  }

  async function verify() {
    if (totpCode.length !== 6 || verifying) return;
    verifying = true;

    try {
      // Inject totpCode into the original request body
      const bodyObj = JSON.parse(pendingOptions.body);
      bodyObj.totpCode = totpCode;
      if (pendingCaptchaStepToken) {
        bodyObj.captchaStepToken = pendingCaptchaStepToken;
      }
      pendingOptions.body = JSON.stringify(bodyObj);

      // Re-send via the PATCHED fetch (so CaptchaWidget etc. still run)
      const response = await window.fetch(pendingUrl, pendingOptions);

      // If we get here, the 2FA was valid (or a different error).
      // The patched fetch will handle TWO_FACTOR_INVALID_CODE above.
      // For success or other errors, resolve the original promise.
      showTotp = false;
      verifying = false;
      pendingCaptchaStepToken = null;

      if (pendingResolve) {
        pendingResolve(response);
        pendingResolve = null;
      }
    } catch (e) {
      console.error('[auth-guard] 2FA verify error', e);
      verifying = false;
      totpError = true;
    }
  }

  function cancel() {
    showTotp = false;
    totpCode = '';
    totpError = false;
    verifying = false;
    pendingCaptchaStepToken = null;

    // Resolve with the same error shape the API uses so ErrorAlert can translate reason (plugin i18n)
    if (pendingResolve) {
      const errorResponse = new Response(
        JSON.stringify({
          result: 'error',
          error: 'PLUGIN_DENIED_LOGIN',
          reason: TWO_FACTOR_REQUIRED_REASON
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      );
      pendingResolve(errorResponse);
      pendingResolve = null;
    }
  }
</script>
