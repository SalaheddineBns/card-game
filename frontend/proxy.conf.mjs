// Dev-server proxy: /api and /ws go to the backend, so the session cookie and CSRF are same-origin.
// BACKEND_URL overrides the target, e.g. `BACKEND_URL=http://localhost:18080 npm start`.
const target = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default {
  '/api': { target, secure: false, changeOrigin: false },
  '/ws': { target, secure: false, ws: true, changeOrigin: false },
};
