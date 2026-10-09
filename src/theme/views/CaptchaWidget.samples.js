// Sample data for the view catalogue (doc 02 section 7). Pure data.
// The widget reads the public captcha settings the plugin loads for the form (theme/state.js stores) and shows nothing
// while the provider is NONE, so the catalogue shows it empty until those are set.
export const notApplicable = ['empty', 'loading', 'error'];

/** @type {import('@panomc/plugin-kit').Samples} */
export default {
  filled: { props: { data: {} } },
};
