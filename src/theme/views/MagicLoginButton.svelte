<style>
  .auth-guard-magic-login-button {
    position: relative;
  }

  .auth-guard-magic-login-button__button {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    width: 100%;
    padding: 0.65rem 1rem;
    font-weight: 500;
  }

  .auth-guard-magic-login-button__form {
    width: 100%;
  }

  .auth-guard-magic-login-button__sent {
    text-align: center;
    padding: 1rem 0;
  }

  .auth-guard-magic-login-button__sent-icon {
    font-size: 2.5rem;
    margin-bottom: 0.75rem;
  }

  .auth-guard-magic-login-button__fade-in {
    animation: auth-guard-fade-in 250ms both;
  }

  @keyframes auth-guard-fade-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .auth-guard-magic-login-button__shake {
    animation: auth-guard-shake 250ms both;
  }

  @keyframes auth-guard-shake {
    from, to { transform: translate3d(0, 0, 0); }
    10%, 30%, 50%, 70%, 90% { transform: translate3d(-10px, 0, 0); }
    20%, 40%, 60%, 80% { transform: translate3d(10px, 0, 0); }
  }
</style>

{#if $magicLoginEnabled}
  <div class="auth-guard-magic-login-button">
    {#if viewState === 'button'}
      <button
        class="auth-guard-magic-login-button__button btn"
        type="button"
        onclick={() => viewState = 'form'}>
        <i class="fas fa-magic"></i>
        <span>{$_('magic-login.button')}</span>
      </button>
    {:else if viewState === 'form'}
      <div class="auth-guard-magic-login-button__form auth-guard-magic-login-button__fade-in">
        <form onsubmit={sendMagicLink}>
          <div class="mb-2">
            <div class="form-floating">
              <input
                type="email"
                class="auth-guard-magic-login-button__input form-control"
                id="magicLoginEmail"
                bind:value={email}
                disabled={sending}
                placeholder="email@example.com" />
              <label for="magicLoginEmail">{$_('magic-login.email-label')}</label>
            </div>
          </div>
          {#if errorMessage}
            <div class="auth-guard-magic-login-button__alert alert alert-danger py-2 small auth-guard-magic-login-button__shake">{errorMessage}</div>
          {/if}
          <button
            class="auth-guard-magic-login-button__button btn w-100"
            type="submit"
            disabled={sending || !email.includes('@')}>
            {#if sending}
              <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
              <span>{$_('magic-login.sending')}</span>
            {:else}
              <i class="fas fa-paper-plane"></i>
              <span>{$_('magic-login.send-button')}</span>
            {/if}
          </button>
          <button
            class="auth-guard-magic-login-button__action btn btn-link btn-sm w-100 mt-1"
            type="button"
            disabled={sending}
            onclick={() => viewState = 'button'}>
            {$_('magic-login.back-to-login')}
          </button>
        </form>
      </div>
    {:else if viewState === 'sent'}
      <div class="auth-guard-magic-login-button__sent auth-guard-magic-login-button__fade-in">
        <i class="auth-guard-magic-login-button__sent-icon fas fa-envelope-open-text d-block"></i>
        <h6>{$_('magic-login.sent-title')}</h6>
        <p class="text-muted small mb-2">{$_('magic-login.sent-desc')}</p>
        <button
          class="auth-guard-magic-login-button__back-to-login btn btn-link btn-sm"
          type="button"
          onclick={() => { viewState = 'button'; email = ''; }}>
          {$_('magic-login.back-to-login')}
        </button>
      </div>
    {/if}
  </div>
{/if}

<script>
  import { derived } from 'svelte/store';
  import { _ as i18n } from '@panomc/sdk/utils/language';
  import { magicLoginEnabled } from '../state.js';

  // plugin translations: $_('key') reads plugins.pano-plugin-auth-guard.key
  const _ = derived(i18n, ($_fn) => (key, options) => $_fn(`plugins.pano-plugin-auth-guard.${key}`, options));

  let viewState = $state('button'); // button, form, sent
  let email = $state('');
  let sending = $state(false);
  let errorMessage = $state('');

  async function sendMagicLink(e) {
    e.preventDefault();
    if (!email.includes('@') || sending) return;

    sending = true;
    errorMessage = '';

    try {
      const res = await fetch('/api/plugins/pano-plugin-auth-guard/magic-login/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });

      const body = await res.json();

      if (!body.error) {
        viewState = 'sent';
      } else {
        errorMessage = $_('magic-login.error');
      }
    } catch (e) {
      console.error('[auth-guard] Magic login init failed', e);
      errorMessage = $_('magic-login.error');
    }
    sending = false;
  }
</script>
