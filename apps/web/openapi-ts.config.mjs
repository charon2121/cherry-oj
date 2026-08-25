/** @satisfies {import('@hey-api/openapi-ts').UserConfig} */
const config = {
  input: '../../contracts/web-api.openapi.json',
  output: 'src/generated/api',
  plugins: ['@hey-api/typescript'],
};

export default config;
