// Sample data for the view catalogue (doc 02 section 7). Pure data.
export const notApplicable = ['loading', 'error'];

/** @type {import('@panomc/plugin-kit').Samples} */
export default {
  filled: { session: 'user', props: { data: { globallyEnabled: true, twoFactorEnabled: true } } },
  empty: { session: 'user', props: { data: { globallyEnabled: true, twoFactorEnabled: false } } },
  off: { label: 'Two-factor switched off by the admin', session: 'user', props: { data: { globallyEnabled: false, twoFactorEnabled: false } } },
};
