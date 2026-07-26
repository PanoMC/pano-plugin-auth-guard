{#if addon?.id === 'pano-plugin-auth-guard'}
  <div class="card">
    <div class="card-header">
      {$_('plugin-settings.title')}
    </div>
    <div class="card-body">
      <!-- Captcha Provider -->
      <div class="row mb-3">
        <label class="col-md-6 col-form-label" for="captchaProvider">
          <span class="d-block">
            {$_('plugin-settings.captcha-provider')}
          </span>
          <small>
            {$_('plugin-settings.captcha-provider-desc')}
          </small>
        </label>
        <div class="col-md-6">
          <select
            class="form-select"
            id="captchaProvider"
            bind:value={config.captchaProvider}>
            <option value="NONE">{$_('plugin-settings.captcha-none')}</option>
            <option value="RECAPTCHA">{$_('plugin-settings.captcha-recaptcha')}</option>
            <option value="HCAPTCHA">{$_('plugin-settings.captcha-hcaptcha')}</option>
            <option value="TURNSTILE">{$_('plugin-settings.captcha-turnstile')}</option>
          </select>
        </div>
      </div>

      {#if config.captchaProvider !== 'NONE'}
        <!-- reCAPTCHA Legacy Mode (only for RECAPTCHA) -->
        {#if config.captchaProvider === 'RECAPTCHA'}
          <div class="row mb-3 animate__animated animate__fadeIn">
            <label class="col-md-6 col-form-label" for="recaptchaLegacy">
              <span class="d-block">
                {$_('plugin-settings.recaptcha-legacy')}
              </span>
              <small>
                {$_('plugin-settings.recaptcha-legacy-desc')}
              </small>
            </label>
            <div class="col-md-6 d-flex align-items-center">
              <div class="form-check form-switch">
                <input
                  class="form-check-input"
                  type="checkbox"
                  id="recaptchaLegacy"
                  bind:checked={config.recaptchaLegacy} />
              </div>
            </div>
          </div>
        {/if}

        <!-- Site Key -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaSiteKey">
            {$_('plugin-settings.captcha-site-key')}
          </label>
          <div class="col-md-6">
            <input
              type="text"
              class="form-control"
              id="captchaSiteKey"
              bind:value={config.captchaSiteKey} />
            <small class="d-block mt-2">
              {$_('plugin-settings.captcha-site-key-desc')}
            </small>
          </div>
        </div>

        <!-- Secret Key -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaSecretKey">
            {$_('plugin-settings.captcha-secret-key')}
          </label>
          <div class="col-md-6">
            {#if hasSecretKey && !editingSecretKey}
              <div class="d-flex align-items-center gap-2">
                <input
                  type="password"
                  class="form-control"
                  value="••••••••••••••••"
                  disabled />
                <button
                  class="btn btn-outline-primary btn-sm"
                  type="button"
                  aria-label="Edit secret key"
                  onclick={() => { editingSecretKey = true; config.captchaSecretKey = ''; }}>
                  <i class="fas fa-edit"></i>
                </button>
              </div>
            {:else}
              <div class="input-group">
                <input
                  type={showSecretKey ? 'text' : 'password'}
                  class="form-control"
                  id="captchaSecretKey"
                  placeholder={hasSecretKey && editingSecretKey ? $_('plugin-settings.captcha-secret-key-new-placeholder') : ''}
                  bind:value={config.captchaSecretKey} />
                <button
                  class="btn btn-outline-secondary"
                  type="button"
                  aria-label="Toggle visibility"
                  onclick={() => showSecretKey = !showSecretKey}>
                  <i class="fas {showSecretKey ? 'fa-eye-slash' : 'fa-eye'}"></i>
                </button>
                {#if editingSecretKey}
                  <button
                    class="btn btn-outline-danger"
                    type="button"
                    aria-label="Cancel edit"
                    onclick={() => { editingSecretKey = false; config.captchaSecretKey = ''; }}>
                    <i class="fas fa-times"></i>
                  </button>
                {/if}
              </div>
            {/if}
            <small class="d-block mt-2">
              {$_('plugin-settings.captcha-secret-key-desc')}
            </small>
          </div>
        </div>

        <!-- Project ID (only for reCAPTCHA Enterprise mode) -->
        {#if config.captchaProvider === 'RECAPTCHA' && !config.recaptchaLegacy}
          <div class="row mb-3 animate__animated animate__fadeIn">
            <label class="col-md-6 col-form-label" for="captchaProjectId">
              {$_('plugin-settings.captcha-project-id')}
            </label>
            <div class="col-md-6">
              {#if hasProjectId && !editingProjectId}
                <div class="d-flex align-items-center gap-2">
                  <input
                    type="password"
                    class="form-control"
                    value="••••••••••••••••"
                    disabled />
                  <button
                    class="btn btn-outline-primary btn-sm"
                    type="button"
                    aria-label="Edit project ID"
                    onclick={() => { editingProjectId = true; config.captchaProjectId = ''; }}>
                    <i class="fas fa-edit"></i>
                  </button>
                </div>
              {:else}
                <div class="input-group">
                  <input
                    type="text"
                    class="form-control"
                    id="captchaProjectId"
                    placeholder={hasProjectId && editingProjectId ? $_('plugin-settings.captcha-project-id-new-placeholder') : ''}
                    bind:value={config.captchaProjectId} />
                  {#if editingProjectId}
                    <button
                      class="btn btn-outline-danger"
                      type="button"
                      aria-label="Cancel edit"
                      onclick={() => { editingProjectId = false; config.captchaProjectId = ''; }}>
                      <i class="fas fa-times"></i>
                    </button>
                  {/if}
                </div>
              {/if}
              <small class="d-block mt-2">
                {$_('plugin-settings.captcha-project-id-desc')}
              </small>
            </div>
          </div>
        {/if}

        <!-- Captcha on Login -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnLogin">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-login')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-login-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnLogin"
                bind:checked={config.captchaOnLogin} />
            </div>
          </div>
        </div>

        <!-- Captcha on Register -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnRegister">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-register')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-register-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnRegister"
                bind:checked={config.captchaOnRegister} />
            </div>
          </div>
        </div>

        <!-- Captcha on password reset -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnResetPassword">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-reset-password')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-reset-password-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnResetPassword"
                bind:checked={config.captchaOnResetPassword} />
            </div>
          </div>
        </div>

        <!-- Captcha on email verification (account activation) -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnVerifyEmail">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-verify-email')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-verify-email-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnVerifyEmail"
                bind:checked={config.captchaOnVerifyEmail} />
            </div>
          </div>
        </div>

        <!-- Captcha on new email confirmation -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnVerifyNewEmail">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-verify-new-email')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-verify-new-email-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnVerifyNewEmail"
                bind:checked={config.captchaOnVerifyNewEmail} />
            </div>
          </div>
        </div>

        <!-- Captcha on renew / change password (link flow) -->
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="captchaOnRenewPassword">
            <span class="d-block">
              {$_('plugin-settings.captcha-on-renew-password')}
            </span>
            <small>
              {$_('plugin-settings.captcha-on-renew-password-desc')}
            </small>
          </label>
          <div class="col-md-6 d-flex align-items-center">
            <div class="form-check form-switch">
              <input
                class="form-check-input"
                type="checkbox"
                id="captchaOnRenewPassword"
                bind:checked={config.captchaOnRenewPassword} />
            </div>
          </div>
        </div>
      {/if}

      <hr class="my-4" />

      <!-- Two-Factor Authentication -->
      <div class="row mb-3">
        <label class="col-md-6 col-form-label" for="twoFactorEnabled">
          <span class="d-block">
            {$_('plugin-settings.two-factor-enabled')}
          </span>
          <small>
            {$_('plugin-settings.two-factor-enabled-desc')}
          </small>
        </label>
        <div class="col-md-6 d-flex align-items-center">
          <div class="form-check form-switch">
            <input
              class="form-check-input"
              type="checkbox"
              id="twoFactorEnabled"
              bind:checked={config.twoFactorEnabled} />
          </div>
        </div>
      </div>

      {#if config.twoFactorEnabled}
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="twoFactorIssuer">
            {$_('plugin-settings.two-factor-issuer')}
          </label>
          <div class="col-md-6">
            <input
              type="text"
              class="form-control"
              id="twoFactorIssuer"
              placeholder={website ? $website.name || '' : ''}
              bind:value={config.twoFactorIssuer} />
            <small class="d-block mt-2">
              {$_('plugin-settings.two-factor-issuer-desc')}
            </small>
          </div>
        </div>
      {/if}

      <hr class="my-4" />

      <!-- Magic Login -->
      <div class="row mb-3">
        <label class="col-md-6 col-form-label" for="magicLoginEnabled">
          <span class="d-block">
            {$_('plugin-settings.magic-login-enabled')}
          </span>
          <small>
            {$_('plugin-settings.magic-login-enabled-desc')}
          </small>
        </label>
        <div class="col-md-6 d-flex align-items-center">
          <div class="form-check form-switch">
            <input
              class="form-check-input"
              type="checkbox"
              id="magicLoginEnabled"
              bind:checked={config.magicLoginEnabled} />
          </div>
        </div>
      </div>

      {#if config.magicLoginEnabled}
        <div class="row mb-3 animate__animated animate__fadeIn">
          <label class="col-md-6 col-form-label" for="magicLoginExpire">
            {$_('plugin-settings.magic-login-expire')}
          </label>
          <div class="col-md-6">
            <input
              type="number"
              class="form-control"
              id="magicLoginExpire"
              min="1"
              max="1440"
              bind:value={config.magicLoginLinkExpireMinutes} />
            <small class="d-block mt-2">
              {$_('plugin-settings.magic-login-expire-desc')}
            </small>
          </div>
        </div>
      {/if}

      <div class="mt-4">
        <button class="btn btn-secondary" onclick={saveConfig} disabled={saving || !hasChanges}>
          {#if saving}
            <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"
            ></span>
          {:else}{/if}
          {$_('plugin-settings.save')}
        </button>
      </div>
    </div>
  </div>
{/if}

<script>
    import {getContext} from 'svelte';
  import { _, showSuccessToast, showErrorToast } from '../../../main.js';
    import ApiUtil from '@panomc/sdk/utils/api';

    export let addon;

  /** Panel AppLayout: `{ name, description, ... }` from platform site settings */
  const website = getContext('website');

  let showSecretKey = false;
  let hasSecretKey = addon?.config?.hasSecretKey || false;
  let editingSecretKey = false;
  let hasProjectId = addon?.config?.hasProjectId || false;
  let editingProjectId = false;

  let config = {
    captchaProvider: addon?.config?.captchaProvider || 'NONE',
    captchaSiteKey: addon?.config?.captchaSiteKey || '',
    captchaSecretKey: '',
    captchaProjectId: '',
    recaptchaLegacy: addon?.config?.recaptchaLegacy ?? false,
    captchaOnLogin: addon?.config?.captchaOnLogin ?? true,
    captchaOnRegister: addon?.config?.captchaOnRegister ?? true,
    captchaOnResetPassword: addon?.config?.captchaOnResetPassword ?? true,
    captchaOnVerifyEmail: addon?.config?.captchaOnVerifyEmail ?? true,
    captchaOnVerifyNewEmail: addon?.config?.captchaOnVerifyNewEmail ?? true,
    captchaOnRenewPassword: addon?.config?.captchaOnRenewPassword ?? true,
    twoFactorEnabled: addon?.config?.twoFactorEnabled || false,
    twoFactorIssuer: addon?.config?.twoFactorIssuer || '',
    magicLoginEnabled: addon?.config?.magicLoginEnabled || false,
    magicLoginLinkExpireMinutes: addon?.config?.magicLoginLinkExpireMinutes || 15,
  };
  let saving = false;

  let initialConfig = JSON.parse(JSON.stringify(config));

  $: hasChanges = JSON.stringify(config) !== JSON.stringify(initialConfig);

  async function saveConfig() {
    if (saving) return;
    saving = true;
    try {
      const body = { ...config };

      // If secret key not changed, don't send it
      if (hasSecretKey && !editingSecretKey) {
        delete body.captchaSecretKey;
      }

      // If project ID not changed, don't send it
      if (hasProjectId && !editingProjectId) {
        delete body.captchaProjectId;
      }

      await ApiUtil.put({
        path: '/api/panel/auth-guard/config',
        body,
      });
      addon.config = {
        ...config,
        hasSecretKey: config.captchaSecretKey ? true : hasSecretKey,
        hasProjectId: config.captchaProjectId ? true : hasProjectId,
      };
      hasSecretKey = addon.config.hasSecretKey;
      editingSecretKey = false;
      hasProjectId = addon.config.hasProjectId;
      editingProjectId = false;
      initialConfig = JSON.parse(JSON.stringify(config));
      await showSuccessToast('components.toasts.settings-save-success');
    } catch (e) {
      console.error('Failed to save auth guard config', e);
      await showErrorToast('components.toasts.settings-save-error');
    } finally {
      saving = false;
    }
  }
</script>
