<style>
  .auth-guard-two-factor-setup__header {
    font-weight: 600;
  }

  .auth-guard-two-factor-setup__qr {
    display: flex;
    justify-content: center;
    padding: 1rem;
  }

  .auth-guard-two-factor-setup__qr-image {
    background: white;
    padding: 0.5rem;
  }

  .auth-guard-two-factor-setup__secret {
    font-family: monospace;
    font-size: 0.95rem;
    letter-spacing: 0.05em;
    background: var(--pano-color-surface, #f8f9fa);
    color: var(--pano-color-text, #212529);
    padding: 0.5rem 1rem;
    text-align: center;
    word-break: break-all;
    user-select: all;
  }

  .auth-guard-two-factor-setup__code-input {
    font-family: monospace;
    font-size: 1.25rem;
    letter-spacing: 0.25em;
    text-align: center;
    max-width: 200px;
    margin: 0 auto;
  }

  .auth-guard-two-factor-setup__fade-in {
    animation: auth-guard-fade-in 250ms both;
  }

  @keyframes auth-guard-fade-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .auth-guard-two-factor-setup__modal {
    background: rgba(0, 0, 0, 0.5);
  }

  .auth-guard-two-factor-setup__shake {
    animation: auth-guard-shake 250ms both;
  }

  @keyframes auth-guard-shake {
    from, to { transform: translate3d(0, 0, 0); }
    10%, 30%, 50%, 70%, 90% { transform: translate3d(-10px, 0, 0); }
    20%, 40%, 60%, 80% { transform: translate3d(10px, 0, 0); }
  }
</style>

{#if globallyEnabled}
<div class="auth-guard-two-factor-setup card">
  <div class="auth-guard-two-factor-setup__header card-header">
    <i class="fas fa-shield-alt me-2"></i>{$_('two-factor.title')}
  </div>
  <div class="auth-guard-two-factor-setup__body card-body">
    <p class="text-muted mb-3">{$_('two-factor.description')}</p>

    {#if isEnabled}
      <!-- 2FA is enabled -->
      <div class="auth-guard-two-factor-setup__alert alert alert-success mb-3">
        <i class="fas fa-check-circle me-1"></i>
        {$_('two-factor.enabled')}
      </div>
      <button class="auth-guard-two-factor-setup__action btn btn-outline-danger btn-sm" type="button" onclick={() => showDisableModal = true}>
        <i class="fas fa-times me-1"></i>{$_('two-factor.disable-button')}
      </button>
    {:else if setupStep === 'idle'}
      <!-- 2FA is not enabled -->
      <div class="auth-guard-two-factor-setup__disabled alert alert-secondary mb-3">
        <i class="fas fa-info-circle me-1"></i>
        {$_('two-factor.disabled')}
      </div>
      <button class="auth-guard-two-factor-setup__setup-button btn btn-secondary" type="button" onclick={startSetup} disabled={setupLoading}>
        {#if setupLoading}
          <span class="spinner-border spinner-border-sm me-1" role="status"></span>
        {/if}
        <i class="fas fa-lock me-1"></i>{$_('two-factor.setup-button')}
      </button>
    {:else if setupStep === 'scan'}
      <!-- QR code scan step -->
      <div class="auth-guard-two-factor-setup__fade-in position-relative">
        <button type="button" class="btn-close position-absolute top-0 end-0" aria-label={$_('a11y.cancel-setup')} onclick={() => { setupStep = 'idle'; verifyCode = ''; }}></button>
        <p class="mb-2 fw-semibold pe-4">{$_('two-factor.scan-qr')}</p>
        <div class="auth-guard-two-factor-setup__qr">
          <img class="auth-guard-two-factor-setup__qr-image" src={qrDataUrl} alt={$_('a11y.qr-code')} width="200" height="200" />
        </div>

        <p class="mb-1 mt-3 small text-muted">{$_('two-factor.manual-entry')}</p>
        <div class="auth-guard-two-factor-setup__secret mb-3">{secretDisplay}</div>

        <p class="mb-2 fw-semibold">{$_('two-factor.verify-code')}</p>
        <div class="d-flex flex-column align-items-center gap-2">
          <input
            type="text"
            class="auth-guard-two-factor-setup__code-input form-control"
            maxlength="6"
            placeholder="000000"
            bind:value={verifyCode}
            oninput={onCodeInput}
            disabled={verifyLoading} />

          <label class="auth-guard-two-factor-setup__setup-password-label form-label mb-0" for="setupPassword">{$_('two-factor.disable-password-label')}</label>
          <input
            type="password"
            class="auth-guard-two-factor-setup__setup-password form-control"
            id="setupPassword"
            autocomplete="current-password"
            bind:value={setupPassword}
            oninput={() => { verifyError = false; }}
            disabled={verifyLoading} />

          {#if verifyError}
            <div class="text-danger small auth-guard-two-factor-setup__shake">
              {$_(verifyError === 'password' ? 'two-factor.setup-password-failed' : 'two-factor.setup-failed')}
            </div>
          {/if}

          <button
            class="auth-guard-two-factor-setup__verifying btn btn-secondary"
            type="button"
            onclick={verifySetup}
            disabled={verifyLoading || verifyCode.length !== 6 || !setupPassword}>
            {#if verifyLoading}
              <span class="spinner-border spinner-border-sm me-1" role="status"></span>
              {$_('two-factor.verifying')}
            {:else}
              {$_('two-factor.verify-button')}
            {/if}
          </button>
        </div>
      </div>
    {:else if setupStep === 'done'}
      <div class="auth-guard-two-factor-setup__setup-success alert alert-success auth-guard-two-factor-setup__fade-in">
        <i class="fas fa-check-circle me-1"></i>
        {$_('two-factor.setup-success')}
      </div>
    {/if}
  </div>
</div>
{/if}

<!-- Disable 2FA Modal -->
{#if showDisableModal}
  <div class="auth-guard-two-factor-setup auth-guard-two-factor-setup__modal modal fade show d-block" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-dialog-centered" role="dialog">
      <div class="modal-content">
        <form onsubmit={(e) => { e.preventDefault(); disableTwoFactor(); }}>
          <div class="auth-guard-two-factor-setup__disable-title modal-header">
            <h5 class="auth-guard-two-factor-setup__title modal-title">{$_('two-factor.disable-title')}</h5>
            <button type="button" class="btn-close" aria-label={$_('a11y.close')} onclick={() => showDisableModal = false}></button>
          </div>
          <div class="auth-guard-two-factor-setup__disable-desc modal-body">
            <p class="text-muted">{$_('two-factor.disable-desc')}</p>

            <div class="mb-3">
              <label class="auth-guard-two-factor-setup__label form-label" for="disableCode">{$_('two-factor.disable-code-label')}</label>
              <input
                type="text"
                class="auth-guard-two-factor-setup__code-input form-control"
                id="disableCode"
                maxlength="6"
                placeholder="000000"
                bind:value={disableCode}
                disabled={disableLoading} />
            </div>

            <div class="mb-3">
              <label class="auth-guard-two-factor-setup__disable-password-label form-label" for="disablePassword">{$_('two-factor.disable-password-label')}</label>
              <input
                type="password"
                class="auth-guard-two-factor-setup__input form-control"
                id="disablePassword"
                bind:value={disablePassword}
                disabled={disableLoading} />
            </div>

            {#if disableError}
              <div class="text-danger small">{$_('two-factor.disable-failed')}</div>
            {/if}
          </div>
          <div class="auth-guard-two-factor-setup__footer modal-footer">
            <button type="button" class="auth-guard-two-factor-setup__action-2 btn btn-link" onclick={() => showDisableModal = false} disabled={disableLoading}>
              {$_('two-factor.cancel')}
            </button>
            <button
              type="submit"
              class="auth-guard-two-factor-setup__disable-confirm btn btn-danger"
              disabled={disableLoading || disableCode.length !== 6 || !disablePassword}>
              {#if disableLoading}
                <span class="spinner-border spinner-border-sm me-1" role="status"></span>
              {/if}
              {$_('two-factor.disable-confirm')}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
{/if}

<script module>
  import { api } from '@panomc/sdk/plugin-api';

  // The 2FA section of the user settings: mounts in the settings-content slot, after the core sections.
  export const view = { slot: 'settings-content', id: 'pano-plugin-auth-guard-2fa-settings', priority: 200 };

  export async function load(event) {
    try {
      const { session: { user } } = await event.parent();
      if (!user) return { globallyEnabled: false, twoFactorEnabled: false };

      const res = await api.get({
        path: '/2fa/status',
        request: event,
      });

      if (!res?.error) {
        return {
          globallyEnabled: !!res.twoFactorGlobalEnabled,
          twoFactorEnabled: !!res.twoFactorEnabled,
        };
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] Failed to load 2FA status', e);
    }
    return { globallyEnabled: false, twoFactorEnabled: false };
  }
</script>

<script>
  import { derived } from 'svelte/store';
  import { _ as i18n } from '@panomc/sdk/utils/language';
  import { untrack } from 'svelte';

  // plugin translations: $_('key') reads plugins.pano-plugin-auth-guard.key
  const _ = derived(i18n, ($_fn) => (key, options) => $_fn(`plugins.pano-plugin-auth-guard.${key}`, options));

  let { data } = $props();

  let isEnabled = $state(!!data?.twoFactorEnabled);
  let globallyEnabled = $state(!!data?.globallyEnabled);

  // Setup state
  let setupStep = $state('idle'); // idle, scan, done
  let setupLoading = $state(false);
  let qrDataUrl = $state('');
  let secretDisplay = $state('');
  let verifyCode = $state('');
  let verifyLoading = $state(false);
  let verifyError = $state(false); // false, true (wrong code) or 'password'
  let setupPassword = $state('');

  // Disable state
  let showDisableModal = $state(false);
  let disableCode = $state('');
  let disablePassword = $state('');
  let disableLoading = $state(false);
  let disableError = $state(false);

  $effect(() => {
    const next = data;
    untrack(() => {
      globallyEnabled = !!next?.globallyEnabled;
      // Only sync enabled from SSR when not actively in setup/disable flow.
      if (setupStep === 'idle') {
        isEnabled = !!next?.twoFactorEnabled;
      }
    });
  });

  async function startSetup() {
    setupLoading = true;
    try {
      const res = await api.post({ path: '/2fa/setup' });
      if (!res.error) {
        if (res.alreadyEnabled) {
          isEnabled = true;
          setupLoading = false;
          return;
        }
        // Drawn by the plugin itself: the URI carries the secret, so it is not sent to a QR service.
        qrDataUrl = res.qrCode;
        secretDisplay = res.secret;
        setupStep = 'scan';
      }
    } catch (e) {
      console.error('[auth-guard] Failed to start 2FA setup', e);
    }
    setupLoading = false;
  }

  function onCodeInput() {
    verifyError = false;
    verifyCode = verifyCode.replace(/\D/g, '').slice(0, 6);
  }

  async function verifySetup() {
    if (verifyCode.length !== 6 || !setupPassword) return;
    verifyLoading = true;
    verifyError = false;
    try {
      const res = await api.post({
        path: '/2fa/verify-setup',
        body: { code: verifyCode, password: setupPassword },
      });
      if (!res.error && res.valid) {
        setupStep = 'done';
        isEnabled = true;
        setupPassword = '';
      } else {
        verifyError = res.error?.code === 'NO_PERMISSION' ? 'password' : true;
      }
    } catch (e) {
      verifyError = true;
    }
    verifyLoading = false;
  }

  async function disableTwoFactor() {
    if (disableCode.length !== 6 || !disablePassword) return;
    disableLoading = true;
    disableError = false;
    try {
      const res = await api.post({
        path: '/2fa/disable',
        body: { code: disableCode, password: disablePassword },
      });
      if (!res.error && res.valid) {
        isEnabled = false;
        showDisableModal = false;
        disableCode = '';
        disablePassword = '';
        setupStep = 'idle';
      } else {
        disableError = true;
      }
    } catch (e) {
      disableError = true;
    }
    disableLoading = false;
  }
</script>
