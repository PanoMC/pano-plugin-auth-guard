<style>
  .magic-login-wrapper {
    position: relative;
  }

  .btn-magic-login {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    width: 100%;
    padding: 0.65rem 1rem;
    border-radius: 0.5rem;
    font-weight: 500;
    background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
    border: none;
    color: #fff;
    transition: all 0.2s ease;
    box-shadow: 0 2px 8px rgba(79, 70, 229, 0.25);
  }

  .btn-magic-login:hover:not(:disabled) {
    background: linear-gradient(135deg, #4338ca 0%, #3730a3 100%);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.35);
    color: #fff;
  }

  .btn-magic-login:active:not(:disabled) {
    transform: translateY(0);
  }

  .btn-magic-login:disabled {
    opacity: 0.65;
    cursor: not-allowed;
  }

  .magic-login-form {
    width: 100%;
  }

  .magic-login-sent {
    text-align: center;
    padding: 1rem 0;
  }

  .magic-login-sent i {
    font-size: 2.5rem;
    color: #4f46e5;
    margin-bottom: 0.75rem;
  }
</style>

{#if $magicLoginEnabled}
  <div class="magic-login-wrapper">
    {#if viewState === 'button'}
      <button
        class="btn btn-magic-login"
        type="button"
        onclick={() => viewState = 'form'}>
        <i class="fas fa-magic"></i>
        <span>{$_('magic-login.button')}</span>
      </button>
    {:else if viewState === 'form'}
      <div class="magic-login-form animate__animated animate__fadeIn">
        <form onsubmit={sendMagicLink}>
          <div class="mb-2">
            <div class="form-floating">
              <input
                type="email"
                class="form-control"
                id="magicLoginEmail"
                bind:value={email}
                disabled={sending}
                placeholder="email@example.com" />
              <label for="magicLoginEmail">{$_('magic-login.email-label')}</label>
            </div>
          </div>
          {#if errorMessage}
            <div class="alert alert-danger py-2 small animate__animated animate__shakeX">{errorMessage}</div>
          {/if}
          <button
            class="btn btn-magic-login w-100"
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
            class="btn btn-link btn-sm w-100 mt-1"
            type="button"
            disabled={sending}
            onclick={() => viewState = 'button'}>
            {$_('magic-login.back-to-login')}
          </button>
        </form>
      </div>
    {:else if viewState === 'sent'}
      <div class="magic-login-sent animate__animated animate__fadeIn">
        <i class="fas fa-envelope-open-text d-block"></i>
        <h6>{$_('magic-login.sent-title')}</h6>
        <p class="text-muted small mb-2">{$_('magic-login.sent-desc')}</p>
        <button
          class="btn btn-link btn-sm"
          type="button"
          onclick={() => { viewState = 'button'; email = ''; }}>
          {$_('magic-login.back-to-login')}
        </button>
      </div>
    {/if}
  </div>
{/if}

<script>
  import { _, magicLoginEnabled } from '../../../main.js';

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
      const res = await fetch('/api/auth-guard/magic-login/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });

      const body = await res.json();

      if (body.result === 'ok') {
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
