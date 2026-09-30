import {PanoPlugin, viewComponent} from '@panomc/sdk';
import {derived, get, writable} from 'svelte/store';
import {_ as i18n} from '@panomc/sdk/utils/language';
import ApiUtil from '@panomc/sdk/utils/api';
import {installAuthGuardFetch} from './theme/authGuardFetch.js';
import { showToast } from '@panomc/sdk/toasts';

const pluginId = 'pano-plugin-auth-guard';

// this is to render plugin translations
export const _ = derived(i18n, ($_fn) => {
  return (key, options) => $_fn(`plugins.${pluginId}.${key}`, options);
});

// Success/failure colouring for this plugin's toasts, matching the panel. showToast from
// @panomc/sdk/toasts is the host panel's ToastContainer `show`, whose signature is
// (text, params, toastComponent, options): passing undefined for toastComponent keeps the
// host's DefaultToast, and options.variant maps to Bootstrap's text-success / text-danger.
// These live here rather than in @panomc/sdk/toasts because this plugin is pinned to
// @panomc/sdk 1.0.0-dev.39, which predates the variants; they can be dropped for a direct
// SDK import once that pin moves. On an older panel build the extra argument is ignored and
// the toast renders neutral, so this degrades instead of breaking.
export function showSuccessToast(text, params = {}) {
  return showToast(text, params, undefined, { variant: 'success' });
}

export function showErrorToast(text, params = {}) {
  return showToast(text, params, undefined, { variant: 'danger' });
}

export const captchaProvider = writable('NONE');
export const captchaSiteKey = writable('');
export const recaptchaLegacy = writable(false);
export const captchaOnLogin = writable(false);
export const captchaOnRegister = writable(false);
export const twoFactorEnabled = writable(false);
export const magicLoginEnabled = writable(false);

/** Login + link screen: preserve “captcha open” state when the view changes and the widget unmounts. */
export const loginCaptchaDeferred = writable({ active: false, identity: null });

export function setLoginCaptchaDeferred(identity) {
  loginCaptchaDeferred.set({ active: true, identity: identity != null && identity !== '' ? String(identity) : null });
}

export function clearLoginCaptchaDeferred() {
  loginCaptchaDeferred.set({ active: false, identity: null });
}

async function loadPublicConfig(event) {
  try {
    const body = await ApiUtil.get({
      path: '/api/auth-guard/config',
      request: event,
    });

    if (body.result === 'ok') {
      captchaProvider.set(body.captchaProvider);
      captchaSiteKey.set(body.captchaSiteKey);
      recaptchaLegacy.set(body.recaptchaLegacy || false);
      captchaOnLogin.set(body.captchaOnLogin);
      captchaOnRegister.set(body.captchaOnRegister);
      twoFactorEnabled.set(body.twoFactorEnabled);
      magicLoginEnabled.set(body.magicLoginEnabled);
    }

    return body;
  } catch (e) {
    console.error('[pano-plugin-auth-guard] Failed to load config', e);
    return null;
  }
}

/** Set in onLoad, for pages that need the host API (the panel's `pano.ui.auth.login.complete`). */
export let pano;

const pushLoginCaptcha = (pano, contentEdit) => {
  if (pano == null || typeof contentEdit !== 'function') {
    return;
  }
  contentEdit((items) => {
    items.push({
      id: `${pluginId}-captcha`,
      priority: 50,
      component: viewComponent(
        () => import('./theme/components/view/CaptchaWidget.svelte'),
      ),
    });
  });
};

/**
 * Captcha, the 2FA modal and the magic-link button on a login form. Shared by the theme's
 * `/login` and the panel's own sign-in page (a SERVERS install runs no theme), which expose the
 * same `pano.ui.auth.login` surface.
 */
function registerLoginContent(pano) {
  pano.ui.auth.login.onLoad(async (data, event) => {
    const body = await loadPublicConfig(event);
    if (!body || body.result !== 'ok') return;

    if (body.captchaProvider !== 'NONE' && body.captchaOnLogin) {
      pushLoginCaptcha(pano, (cb) => pano.ui.auth.login.content.edit(cb));
    }

    if (body.twoFactorEnabled) {
      pano.ui.auth.login.content.edit((items) => {
        items.push({
          id: `${pluginId}-2fa-guard`,
          priority: 210,
          component: viewComponent(
            () => import('./theme/components/view/TwoFactorLoginGuard.svelte'),
          ),
        });
      });
    }

    if (body.magicLoginEnabled) {
      pano.ui.auth.login.alternativeMethods.add({
        id: `${pluginId}-magic-login`,
        priority: 50,
        component: viewComponent(
          () => import('./theme/components/view/MagicLoginButton.svelte'),
        ),
      });
    }
  });
}

function registerMagicLoginPage(pano, options = {}) {
  pano.ui.page.register({
    path: '/auth-guard/magic-login',
    component: viewComponent(
      () => import('./theme/pages/MagicLoginVerifyPage.svelte'),
    ),
    ...options,
  });
}

export default class AuthGuardPlugin extends PanoPlugin {
  onLoad() {
    pano = this.pano;

    if (pano.isPanel) {
      // Load config for addon detail page
      pano.ui.addon.onLoad(async (data, event) => {
        if (data.addon.id !== pluginId) return;

        try {
          const config = await ApiUtil.get({
            path: '/api/panel/auth-guard/config',
            request: event,
          });
          data.addon.config = config;
        } catch (e) {
          console.error('[pano-plugin-auth-guard] Failed to load panel config', e);
        }
      });

      // Register settings component on plugin detail page
      pano.ui.hook.register({
        name: `panel:plugin-detail:content:${pluginId}`,
        component: viewComponent(
          () => import('./panel/components/view/AuthGuardSettings.svelte'),
        ),
        permission: `pano.plugin.${pluginId}.manage.settings`,
      });

      pano.ui.player.editModal.cardRows.edit((items) => {
        items.push({
          id: `${pluginId}-player-2fa-edit`,
          component: viewComponent(
            () => import('./panel/components/view/PlayerTwoFactorEdit.svelte'),
          ),
          permission: `pano.plugin.${pluginId}.manage.player.two.factor`,
          priority: 95,
        });
      });

      pano.ui.hook.register({
        name: 'panel:player-detail:sidebar',
        component: viewComponent(
          () => import('./panel/components/view/PlayerTwoFactorSidebar.svelte'),
        ),
        permission: `pano.plugin.${pluginId}.manage.player.two.factor`,
      });

      // A SERVERS install signs in on the panel itself, so the same login widgets go there.
      // Panels older than the `pano.ui.auth` surface simply skip this.
      if (pano.ui.auth?.login) {
        if (typeof globalThis !== 'undefined' && globalThis.window) {
          installAuthGuardFetch();
        }

        registerLoginContent(pano);

        // The magic link lands on /auth-guard/magic-login; on a SERVERS install the platform
        // moves it under /panel, where the visitor has no session yet.
        registerMagicLoginPage(pano, { public: true });
      }
    } else {
      if (typeof globalThis !== 'undefined' && globalThis.window) {
        installAuthGuardFetch();
      }

      // Fetch public config and register captcha/2FA/magic login on the login page
      registerLoginContent(pano);

      // Fetch public config and register captcha/magic login on register page
      pano.ui.auth.register.onLoad(async (data, event) => {
        const body = await loadPublicConfig(event);
        if (!body || body.result !== 'ok') return;

        if (body.captchaProvider !== 'NONE' && body.captchaOnRegister) {
          pushLoginCaptcha(pano, (cb) => pano.ui.auth.register.content.edit(cb));
        }
      });

      pano.ui.auth.resetPassword.onLoad(async (data, event) => {
        const body = await loadPublicConfig(event);
        if (
          body?.result === 'ok' &&
          body.captchaProvider !== 'NONE' &&
          body.captchaOnResetPassword
        ) {
          pushLoginCaptcha(pano, (cb) => pano.ui.auth.resetPassword.content.edit(cb));
        }
      });
      pano.ui.auth.activate.onLoad(async (data, event) => {
        const body = await loadPublicConfig(event);
        if (
          body?.result === 'ok' &&
          body.captchaProvider !== 'NONE' &&
          body.captchaOnVerifyEmail
        ) {
          pushLoginCaptcha(pano, (cb) => pano.ui.auth.activate.content.edit(cb));
        }
      });
      pano.ui.auth.activateNewEmail.onLoad(async (data, event) => {
        const body = await loadPublicConfig(event);
        if (
          body?.result === 'ok' &&
          body.captchaProvider !== 'NONE' &&
          body.captchaOnVerifyNewEmail
        ) {
          pushLoginCaptcha(pano, (cb) => pano.ui.auth.activateNewEmail.content.edit(cb));
        }
      });
      pano.ui.auth.renewPassword.onLoad(async (data, event) => {
        const body = await loadPublicConfig(event);
        if (
          body?.result === 'ok' &&
          body.captchaProvider !== 'NONE' &&
          body.captchaOnRenewPassword
        ) {
          pushLoginCaptcha(pano, (cb) => pano.ui.auth.renewPassword.content.edit(cb));
        }
      });

      // Register magic login verify page
      registerMagicLoginPage(pano);

      // Register 2FA setup section in user settings
      pano.ui.settings.content.edit((items) => {
        items.push({
          id: `${pluginId}-2fa-settings`,
          priority: 200,
          component: viewComponent(
            () => import('./theme/components/view/TwoFactorSetup.svelte'),
          ),
        });
      });
    }
  }

  onContextUpdate(ctx) {}

  onUnload() {}
}
