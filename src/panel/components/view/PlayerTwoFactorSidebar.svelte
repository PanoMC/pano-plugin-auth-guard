<script module>
  /**
   * Invoked during panel `executeHookLoad` + layout load.
   * Without `request: event`, `ApiUtil` has no session/CSRF on SSR; the first HTML is empty.
   * (`ApiUtil` is only imported in the instance script; two imports with the same name fail in Svelte 5.)
   * @param {import('@sveltejs/kit').LayoutLoadEvent} event
   */
  export async function load(event) {
    const { default: ApiUtil } = await import('@panomc/sdk/utils/api');
    const username = event.params?.username;
    if (!username || typeof username !== 'string') {
      return {};
    }
    try {
      const res = await ApiUtil.get({
        path: `/api/panel/auth-guard/player/${encodeURIComponent(username)}/2fa`,
        request: event,
      });
      if (res && res.result === 'ok') {
        return {
          ag2faPreload: {
            _ok: true,
            twoFactorGlobalEnabled: !!res.twoFactorGlobalEnabled,
            twoFactorEnabled: !!res.twoFactorEnabled,
            setupPending: !!res.setupPending,
          },
        };
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] 2fa sidebar load() failed', e);
    }
    return {};
  }
</script>

<div class="card mt-3 animate__animated animate__fadeIn">
  <div class="card-header d-flex align-items-center gap-2">
    <i class="fas fa-user-shield text-secondary"></i>
    <span>{$_('panel-2fa.sidebar-title')}</span>
  </div>
  <div class="card-body">
    {#if loading}
      <div class="d-flex justify-content-center py-2">
        <div class="spinner-border spinner-border-sm text-secondary" role="status"></div>
      </div>
    {:else if !globalEnabled}
      <p class="text-muted small mb-0">
        <i class="fas fa-info-circle me-1"></i>
        {$_('panel-2fa.global-off-sidebar')}
      </p>
    {:else}
      <div class="d-flex align-items-center gap-2 flex-wrap">
        <small class="text-muted">{$_('panel-2fa.status-label')}</small>
        {#if twoFactorEnabled}
          <span class="badge bg-success"><i class="fas fa-check-circle me-1"></i>{$_('panel-2fa.on')}</span>
        {:else if setupPending}
          <span class="badge bg-warning text-dark"
            ><i class="fas fa-hourglass-half me-1"></i>{$_('panel-2fa.setup-pending')}</span
          >
        {:else}
          <span class="badge bg-secondary">{$_('panel-2fa.off')}</span>
        {/if}
      </div>
    {/if}
  </div>
</div>

<script>
  import { _ } from '../../../main.js';
  import ApiUtil from '@panomc/sdk/utils/api';

  export let playerData;

  /** @type {{ _ok: boolean, twoFactorGlobalEnabled?: boolean, twoFactorEnabled?: boolean, setupPending?: boolean } | undefined} */
  export let ag2faPreload = undefined;

  let loading = true;
  let globalEnabled = true;
  let twoFactorEnabled = false;
  let setupPending = false;

  /** Avoid a second `loadData` pass for the same user */
  let lastFetchedUsername = null;

  $: if (ag2faPreload?._ok) {
    globalEnabled = ag2faPreload.twoFactorGlobalEnabled;
    twoFactorEnabled = ag2faPreload.twoFactorEnabled;
    setupPending = ag2faPreload.setupPending;
    loading = false;
    lastFetchedUsername = playerData?.username ?? null;
  } else if (playerData?.username && playerData.username !== lastFetchedUsername) {
    lastFetchedUsername = playerData.username;
    loadData();
  }

  async function loadData() {
    if (!playerData?.username) return;
    loading = true;
    try {
      const res = await ApiUtil.get({
        path: `/api/panel/auth-guard/player/${encodeURIComponent(playerData.username)}/2fa`
      });
      if (res && res.result === 'ok') {
        globalEnabled = !!res.twoFactorGlobalEnabled;
        twoFactorEnabled = !!res.twoFactorEnabled;
        setupPending = !!res.setupPending;
      }
    } catch (e) {
      console.error('[pano-plugin-auth-guard] 2fa sidebar load failed', e);
    } finally {
      loading = false;
    }
  }
</script>
