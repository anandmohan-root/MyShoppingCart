const { defineConfig } = require("@playwright/test");

// The stack must already be running (docker compose up -d); these tests start nothing.
module.exports = defineConfig({
  testDir: "tests",
  timeout: 90_000,
  // Both tests change shared shop data, so they run one after the other.
  workers: 1,
  use: {
    baseURL: "http://localhost:8000",
    browserName: "chromium",
    trace: "retain-on-failure",
  },
});
