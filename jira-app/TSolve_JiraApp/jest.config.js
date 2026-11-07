// Tests only. The Babel options stay here, not in a babel.config.js, so Forge's own bundler never picks them up.
module.exports = {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  transform: {
    '^.+\\.jsx?$': [
      'babel-jest',
      {
        babelrc: false,
        configFile: false,
        presets: [
          ['@babel/preset-env', { targets: { node: 'current' } }],
          ['@babel/preset-react', { runtime: 'automatic' }],
        ],
      },
    ],
  },
  setupFilesAfterEnv: ['@testing-library/jest-dom'],
};
