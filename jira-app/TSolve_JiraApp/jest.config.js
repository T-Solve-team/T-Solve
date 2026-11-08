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
  moduleNameMapper: {
    '^@forge/react$': '<rootDir>/test/forge-react.jsx',
    // The package has no "main"; tests mock it, Jest only needs a file to resolve.
    '^@forge/jira-bridge$': '<rootDir>/node_modules/@forge/jira-bridge/out/index.js',
  },
  setupFilesAfterEnv: ['@testing-library/jest-dom'],
};
