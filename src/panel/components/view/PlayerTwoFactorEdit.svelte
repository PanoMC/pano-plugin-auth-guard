<div class="pt-2 pb-3 mb-3 border-bottom animate__animated animate__fadeIn">
  <div class="d-flex align-items-center gap-2 mb-1">
    <i class="fas fa-user-shield text-secondary"></i>
    <span class="fw-bold">{$_('panel-2fa.title')}</span>
  </div>
  <p class="text-secondary small mb-3">{$_('panel-2fa.edit-desc')}</p>

  {#if loading}
    <div class="d-flex align-items-center gap-2">
      <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
    </div>
  {:else if !globalEnabled}
    <div class="alert alert-secondary small mb-0" role="alert">
      <i class="fas fa-info-circle me-1"></i>
      {$_('panel-2fa.global-off')}
    </div>
  {:else if twoFactorEnabled}
    <div class="d-flex flex-wrap align-items-center gap-2">
      <span class="badge bg-success"><i class="fas fa-check-circle me-1"></i>{$_('panel-2fa.on')}</span>
      <button
        class="btn btn-outline-danger btn-sm"
        type="button"
        disabled={actionLoading}
        on:click={confirmRemove}
      >
        {#if actionLoading}
          <span class="spinner-border spinner-border-sm" role="status"></span>
        {:else}
          <i class="fas fa-unlock me-1"></i>{$_('panel-2fa.remove')}
        {/if}
      </button>
    </div>
  {:else if setupPending}
    <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
      <span class="badge bg-warning text-dark"
        ><i class="fas fa-hourglass-half me-1"></i>{$_('panel-2fa.setup-pending')}</span
      >
    </div>
    <div class="d-flex flex-wrap gap-2">
      <button class="btn btn-secondary btn-sm" type="button" disabled={setupLoading} on:click={startSetup}>
        {#if setupLoading}
          <span class="spinner-border spinner-border-sm" role="status"></span>
        {:else}
          {$_('two-factor.setup-button')}
        {/if}
      </button>
    </div>
  {:else}
    <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
      <span class="badge bg-secondary">{$_('panel-2fa.off')}</span>
    </div>
    <button class="btn btn-secondary btn-sm" type="button" disabled={setupLoading} on:click={startSetup}>
      {#if setupLoading}
        <span class="spinner-border spinner-border-sm" role="status"></span>
      {:else}
        {$_('two-factor.setup-button')}
      {/if}
    </button>
  {/if}

  {#if globalEnabled && setupStep === 'scan'}
    <div class="mt-3 position-relative">
      <button
        type="button"
        class="btn-close position-absolute top-0 end-0"
        aria-label="Cancel"
        on:click={cancelScan}
      ></button>
      <p class="mb-2 fw-semibold pe-4">{$_('two-factor.scan-qr')}</p>
      <div class="d-flex justify-content-center py-2">
        <img
          class="bg-white p-1 rounded"
          style="box-shadow: 0 2px 8px rgba(0,0,0,0.1);"
          src={qrDataUrl}
          width="200"
          height="200"
          alt="2FA"
        />
      </div>
      <p class="mb-1 mt-2 small text-muted">{$_('two-factor.manual-entry')}</p>
      <div
        class="mb-3 font-monospace text-center p-2 rounded"
        style="font-size: 0.95rem; background: var(--bs-secondary-bg, #f8f9fa); word-break: break-all; user-select: all;"
      >
        {secretDisplay}
      </div>
      <p class="mb-2 fw-semibold">{$_('two-factor.verify-code')}</p>
      <div class="d-flex flex-column align-items-start gap-2">
        <input
          type="text"
          class="form-control"
          style="max-width: 12rem; letter-spacing: 0.2em; text-align: center; font-size: 1.1rem;"
          maxlength="6"
          placeholder="000000"
          bind:value={verifyCode}
          on:input={onCodeInput}
          disabled={verifyLoading}
        />
        {#if verifyError}
          <div class="text-danger small">{$_('two-factor.setup-failed')}</div>
        {/if}
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          disabled={verifyLoading || verifyCode.length !== 6}
          on:click={verifySetup}
        >
          {#if verifyLoading}
            <span class="spinner-border spinner-border-sm" role="status"></span>
            {$_('two-factor.verifying')}
          {:else}
            {$_('two-factor.verify-button')}
          {/if}
        </button>
      </div>
    </div>
  {/if}
</div>

<script>
  import { _ } from '../../../main.js';
  import { api } from '@panomc/sdk/plugin-api';

  export let playerData;
  export let onHookRegister;

  const username = () => playerData?.username || '';

  let loading = true;
  let actionLoading = false;
  let globalEnabled = false;
  let twoFactorEnabled = false;
  let setupPending = false;

  let setupStep = 'idle';
  let setupLoading = false;
  let verifyLoading = false;
  let verifyError = false;
  let verifyCode = '';
  let qrDataUrl = '';
  let secretDisplay = '';

  $: isDirty = false;

  $: if (onHookRegister) {
    onHookRegister({
      _id: 'pano-plugin-auth-guard-2fa-edit',
      isDirty: isDirty,
      save: async () => {}
    });
  }

  function onCodeInput() {
    verifyError = false;
    verifyCode = String(verifyCode || '')
      .replace(/\D/g, '')
      .slice(0, 6);
  }

  /** Edit modal reuses the same card row instance for every player; reload when the user changes. */
  let lastLoadedUsername = null;

  $: if (playerData?.username) {
    if (playerData.username !== lastLoadedUsername) {
      lastLoadedUsername = playerData.username;
      setupStep = 'idle';
      verifyCode = '';
      verifyError = false;
      qrDataUrl = '';
      secretDisplay = '';
      actionLoading = false;
      setupLoading = false;
      verifyLoading = false;
      loadData();
    }
  }

  async function loadData() {
    if (!username()) return;
    loading = true;
    try {
      const res = await api.panel.get({
        path: `/player/${encodeURIComponent(username())}/2fa`
      });
      if (res && !res.error) {
        globalEnabled = !!res.twoFactorGlobalEnabled;
        twoFactorEnabled = !!res.twoFactorEnabled;
        setupPending = !!res.setupPending;
        if (setupStep !== 'scan') {
          setupStep = 'idle';
        }
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] 2fa panel load failed', e);
    } finally {
      loading = false;
    }
  }

  function cancelScan() {
    setupStep = 'idle';
    verifyCode = '';
    verifyError = false;
  }

  async function startSetup() {
    if (!username()) return;
    setupLoading = true;
    verifyError = false;
    try {
      const res = await api.panel.post({
        path: `/player/${encodeURIComponent(username())}/2fa/setup-init`
      });
      if (!res?.error) {
        if (res.alreadyEnabled) {
          twoFactorEnabled = true;
          setupPending = false;
          setupStep = 'idle';
          return;
        }
        // Drawn by the plugin itself: the URI carries the secret, so it is not sent to a QR service.
        qrDataUrl = res.qrCode;
        secretDisplay = res.secret;
        setupStep = 'scan';
        setupPending = true;
        globalEnabled = true;
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] 2fa setup-init failed', e);
    } finally {
      setupLoading = false;
    }
  }

  async function verifySetup() {
    if (verifyCode.length !== 6 || !username()) return;
    verifyLoading = true;
    verifyError = false;
    try {
      const res = await api.panel.post({
        path: `/player/${encodeURIComponent(username())}/2fa/verify-setup`,
        body: { code: verifyCode }
      });
      if (!res?.error && res.valid) {
        setupStep = 'idle';
        twoFactorEnabled = true;
        setupPending = false;
        verifyCode = '';
      } else {
        verifyError = true;
      }
    } catch (e) {
      verifyError = true;
    } finally {
      verifyLoading = false;
    }
  }

  function confirmRemove() {
    if (typeof globalThis === 'undefined' || !globalThis.confirm) {
      doRemove();
      return;
    }
    if (globalThis.confirm($_('panel-2fa.remove-confirm'))) {
      doRemove();
    }
  }

  async function doRemove() {
    if (!username()) return;
    actionLoading = true;
    try {
      const res = await api.panel.post({
        path: `/player/${encodeURIComponent(username())}/2fa/reset`
      });
      if (!res?.error && res.removed) {
        twoFactorEnabled = false;
        setupPending = false;
        setupStep = 'idle';
        await loadData();
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] 2fa reset failed', e);
    } finally {
      actionLoading = false;
    }
  }
</script>
