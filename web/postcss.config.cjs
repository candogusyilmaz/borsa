const { MOBILE_BREAKPOINT } = require('./src/theme/tokens.ts');

module.exports = {
  plugins: {
    'postcss-preset-mantine': {},
    'postcss-simple-vars': {
      variables: {
        'mantine-breakpoint-xs': '30em',
        'mantine-breakpoint-sm': MOBILE_BREAKPOINT,
        'mantine-breakpoint-md': '64em',
        'mantine-breakpoint-lg': '75em',
        'mantine-breakpoint-xl': '90em'
      }
    }
  }
};
