import { defineConfig } from "vite";
import { resolve } from "node:path";

export default defineConfig(({ mode }) => {
  const dashboard = mode === "dashboard";
  const surface = dashboard ? "dashboard" : "admin";
  return {
    root: dashboard ? "public-dashboard" : ".",
    base: `/${surface}/`,
    build: { outDir: resolve(process.env.DISCORD_BOT_STATIC_OUT_DIR ?? "../target/classes/static", surface) },
  };
});
