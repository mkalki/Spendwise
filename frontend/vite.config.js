import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backendProxy = {
  target: "http://localhost:8080",
  changeOrigin: true,
};

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173,

    proxy: {
      "/api": backendProxy,
      "/oauth2": backendProxy,
      "/login/oauth2": backendProxy,
    },
  },
});
