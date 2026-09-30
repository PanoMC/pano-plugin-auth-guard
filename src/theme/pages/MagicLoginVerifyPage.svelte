<style>
  .magic-verify-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2rem;
    text-align: center;
    min-height: 200px;
  }

  .magic-verify-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
  }

  .magic-verify-icon.success {
    color: #10b981;
  }

  .magic-verify-icon.error {
    color: #ef4444;
  }

  .magic-verify-icon.loading {
    color: #4f46e5;
  }
</style>

<div class="magic-verify-container">
  {#if state === 'loading'}
    <div class="magic-verify-icon loading">
      <span class="spinner-border" role="status"></span>
    </div>
    <p class="text-muted">{$_('magic-login.verifying')}</p>
  {:else if state === 'success'}
    <div class="magic-verify-icon success animate__animated animate__bounceIn">
      <i class="fas fa-check-circle"></i>
    </div>
    <h5>{$_('magic-login.success')}</h5>
    <p class="text-muted small">{$_('magic-login.verifying')}</p>
  {:else if state === 'no-panel-access'}
    <div class="magic-verify-icon error animate__animated animate__shakeX">
      <i class="fas fa-times-circle"></i>
    </div>
    <h5>{$_('magic-login.no-panel-access')}</h5>
    <a href={loginHref} class="btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {:else if state === 'expired'}
    <div class="magic-verify-icon error animate__animated animate__shakeX">
      <i class="fas fa-times-circle"></i>
    </div>
    <h5>{$_('magic-login.expired')}</h5>
    <a href={loginHref} class="btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {:else if state === 'error'}
    <div class="magic-verify-icon error animate__animated animate__shakeX">
      <i class="fas fa-exclamation-triangle"></i>
    </div>
    <h5>{$_('magic-login.error')}</h5>
    <a href={loginHref} class="btn btn-secondary mt-2">
      {$_('magic-login.back-to-login')}
    </a>
  {/if}
</div>

<script>
  import { onMount } from 'svelte';
  import { _, pano } from '../../main.js';

  let state = $state('loading'); // loading, success, expired, error, no-panel-access

  // On a SERVERS install this page is served by the panel, which has its own sign-in page.
  const loginHref = pano?.isPanel ? '/panel/login' : '/login';

  onMount(async () => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');

    if (!token) {
      state = 'error';
      return;
    }

    try {
      const res = await fetch(`/api/auth-guard/magic-login/verify?token=${encodeURIComponent(token)}`);
      const body = await res.json();

      if (body.result === 'ok') {
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
      } else if (body.error === 'MAGIC_LOGIN_LINK_EXPIRED') {
        state = 'expired';
      } else {
        state = 'error';
      }
    } catch (e) {
      console.error('[auth-guard] Magic login verify failed', e);
      state = 'error';
    }
  });
</script>
