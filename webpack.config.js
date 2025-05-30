const path = require('path');

module.exports = {
  target: 'node',
  entry: './dist/extension.js',
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: 'extension.bundle.js',
    libraryTarget: 'commonjs2'
  },
  externals: {
    vscode: 'commonjs vscode',
    bufferutil: 'commonjs bufferutil',
    'utf-8-validate': 'commonjs utf-8-validate'
  },
  resolve: {
    extensions: ['.js'],
    fallback: {
      bufferutil: false,
      'utf-8-validate': false
    }
  },
  optimization: {
    minimize: false
  }
}; 