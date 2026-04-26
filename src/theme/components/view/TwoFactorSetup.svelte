<style>
  .two-factor-card {
    border-radius: 0.75rem;
  }

  .two-factor-card .card-header {
    font-weight: 600;
  }

  .qr-container {
    display: flex;
    justify-content: center;
    padding: 1rem;
  }

  .qr-container img {
    border-radius: 0.5rem;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    background: white;
    padding: 0.5rem;
  }

  .secret-key {
    font-family: monospace;
    font-size: 0.95rem;
    letter-spacing: 0.05em;
    background: var(--bs-secondary-bg, #f8f9fa);
    color: var(--bs-body-color, #212529);
    padding: 0.5rem 1rem;
    border-radius: 0.5rem;
    text-align: center;
    word-break: break-all;
    user-select: all;
  }

  .code-input {
    font-family: monospace;
    font-size: 1.25rem;
    letter-spacing: 0.25em;
    text-align: center;
    max-width: 200px;
    margin: 0 auto;
  }
</style>

{#if globallyEnabled}
<div class="card two-factor-card">
  <div class="card-header">
    <i class="fas fa-shield-alt me-2"></i>{$_('two-factor.title')}
  </div>
  <div class="card-body">
    <p class="text-muted mb-3">{$_('two-factor.description')}</p>

    {#if isEnabled}
      <!-- 2FA is enabled -->
      <div class="alert alert-success mb-3">
        <i class="fas fa-check-circle me-1"></i>
        {$_('two-factor.enabled')}
      </div>
      <button class="btn btn-outline-danger btn-sm" type="button" onclick={() => showDisableModal = true}>
        <i class="fas fa-times me-1"></i>{$_('two-factor.disable-button')}
      </button>
    {:else if setupStep === 'idle'}
      <!-- 2FA is not enabled -->
      <div class="alert alert-secondary mb-3">
        <i class="fas fa-info-circle me-1"></i>
        {$_('two-factor.disabled')}
      </div>
      <button class="btn btn-secondary" type="button" onclick={startSetup} disabled={setupLoading}>
        {#if setupLoading}
          <span class="spinner-border spinner-border-sm me-1" role="status"></span>
        {/if}
        <i class="fas fa-lock me-1"></i>{$_('two-factor.setup-button')}
      </button>
    {:else if setupStep === 'scan'}
      <!-- QR code scan step -->
      <div class="animate__animated animate__fadeIn position-relative">
        <button type="button" class="btn-close position-absolute top-0 end-0" aria-label="Cancel setup" onclick={() => { setupStep = 'idle'; verifyCode = ''; }}></button>
        <p class="mb-2 fw-semibold pe-4">{$_('two-factor.scan-qr')}</p>
        <div class="qr-container">
          <img src={qrDataUrl} alt="2FA QR Code" width="200" height="200" />
        </div>

        <p class="mb-1 mt-3 small text-muted">{$_('two-factor.manual-entry')}</p>
        <div class="secret-key mb-3">{secretDisplay}</div>

        <p class="mb-2 fw-semibold">{$_('two-factor.verify-code')}</p>
        <div class="d-flex flex-column align-items-center gap-2">
          <input
            type="text"
            class="form-control code-input"
            maxlength="6"
            placeholder="000000"
            bind:value={verifyCode}
            oninput={onCodeInput}
            disabled={verifyLoading} />

          {#if verifyError}
            <div class="text-danger small animate__animated animate__shakeX">
              {$_('two-factor.setup-failed')}
            </div>
          {/if}

          <button
            class="btn btn-secondary"
            type="button"
            onclick={verifySetup}
            disabled={verifyLoading || verifyCode.length !== 6}>
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
      <div class="alert alert-success animate__animated animate__fadeIn">
        <i class="fas fa-check-circle me-1"></i>
        {$_('two-factor.setup-success')}
      </div>
    {/if}
  </div>
</div>
{/if}

<!-- Disable 2FA Modal -->
{#if showDisableModal}
  <div class="modal fade show d-block" tabindex="-1" role="dialog" style="background: rgba(0,0,0,0.5);">
    <div class="modal-dialog modal-dialog-centered" role="dialog">
      <div class="modal-content">
        <form onsubmit={(e) => { e.preventDefault(); disableTwoFactor(); }}>
          <div class="modal-header">
            <h5 class="modal-title">{$_('two-factor.disable-title')}</h5>
            <button type="button" class="btn-close" aria-label="Close" onclick={() => showDisableModal = false}></button>
          </div>
          <div class="modal-body">
            <p class="text-muted">{$_('two-factor.disable-desc')}</p>

            <div class="mb-3">
              <label class="form-label" for="disableCode">{$_('two-factor.disable-code-label')}</label>
              <input
                type="text"
                class="form-control code-input"
                id="disableCode"
                maxlength="6"
                placeholder="000000"
                bind:value={disableCode}
                disabled={disableLoading} />
            </div>

            <div class="mb-3">
              <label class="form-label" for="disablePassword">{$_('two-factor.disable-password-label')}</label>
              <input
                type="password"
                class="form-control"
                id="disablePassword"
                bind:value={disablePassword}
                disabled={disableLoading} />
            </div>

            {#if disableError}
              <div class="text-danger small">{$_('two-factor.disable-failed')}</div>
            {/if}
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-link" onclick={() => showDisableModal = false} disabled={disableLoading}>
              Cancel
            </button>
            <button
              type="submit"
              class="btn btn-danger"
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
  import ApiUtil from '@panomc/sdk/utils/api';

  export async function load(event) {
    try {
      const { session: { user } } = await event.parent();
      if (!user) return { globallyEnabled: false, twoFactorEnabled: false };

      const res = await ApiUtil.get({
        path: '/api/auth-guard/2fa/status',
        request: event,
      });

      if (res?.result === 'ok') {
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
  import { _ } from '../../../main.js';
  import { untrack } from 'svelte';

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
  let verifyError = $state(false);

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
      const res = await ApiUtil.post({ path: '/api/auth-guard/2fa/setup' });
      if (res.result === 'ok') {
        if (res.alreadyEnabled) {
          isEnabled = true;
          setupLoading = false;
          return;
        }
        // Generate QR code as data URL using a simple QR API
        qrDataUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(res.totpUri)}`;
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
    if (verifyCode.length !== 6) return;
    verifyLoading = true;
    verifyError = false;
    try {
      const res = await ApiUtil.post({
        path: '/api/auth-guard/2fa/verify-setup',
        body: { code: verifyCode },
      });
      if (res.result === 'ok' && res.valid) {
        setupStep = 'done';
        isEnabled = true;
      } else {
        verifyError = true;
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
      const res = await ApiUtil.post({
        path: '/api/auth-guard/2fa/disable',
        body: { code: disableCode, password: disablePassword },
      });
      if (res.result === 'ok' && res.valid) {
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
