import { defineConfig } from "vite";

export default defineConfig({
  base: "/admin/",
  build: { outDir: process.env.DISCORD_BOT_ADMIN_OUT_DIR ?? "../target/classes/static/admin" },
});
