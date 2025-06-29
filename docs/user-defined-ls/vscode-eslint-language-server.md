# ESLInt Language Server

 * Install Eslint Language Server
 * Execute `npm install eslint` in your project root
 * Create an eslint-config.js file on your roor project:
 
 ```js
 export default [
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
    },
    rules: {
      'no-console': 'error',
    },
  },
];
```

 * Create a `test.js` file like this:

```js 
console.log("eslint test")
 ```
 
You should see an eslint error on console.