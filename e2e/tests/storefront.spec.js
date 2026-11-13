// The storefront in a real browser, through Kong and Keycloak, against the running stack.
// Credentials are the test users from .env (run with: set -a; source ../.env; set +a).
const { test, expect } = require("@playwright/test");

const { SHOP_USER, SHOP_USER_PASSWORD, SHOP_ADMIN, SHOP_ADMIN_PASSWORD } = process.env;

async function signIn(page, username, password) {
  await page.goto("/");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByLabel("Username or email").fill(username);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
}

test.beforeAll(() => {
  if (!SHOP_USER || !SHOP_ADMIN) {
    throw new Error("Load the test users first: set -a; source ../.env; set +a");
  }
});

/** Buys one bag of coffee beans through the cart and waits until My orders shows it paid. */
async function buyBeansAndSeeThemPaid(page) {
  const beans = page.locator("article.card", { hasText: "Coffee Beans 1kg" });
  await beans.getByRole("button", { name: "Add to cart" }).click();
  await expect(page.locator("#notice")).toContainText("in your cart");

  await page.getByRole("link", { name: /^Cart/ }).click();
  await expect(page.getByRole("cell", { name: "Coffee Beans 1kg" })).toBeVisible();
  await page.getByRole("button", { name: "Check out" }).click();

  // Newest order first; the page updates by itself when payment completes.
  const order = page.locator("tbody tr").first();
  await expect(order).toContainText("Coffee Beans 1kg");
  await expect(order).toContainText("Paid", { timeout: 30_000 });
  return order;
}

test("a customer buys a product, sees it paid, and cancels it", async ({ page }) => {
  await signIn(page, SHOP_USER, SHOP_USER_PASSWORD);
  await expect(page.getByRole("link", { name: "Admin" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "All orders" })).toHaveCount(0);

  const order = await buyBeansAndSeeThemPaid(page);

  page.once("dialog", dialog => dialog.accept());
  await order.getByRole("button", { name: "Cancel" }).click();
  await expect(page.locator("tbody tr").first()).toContainText("Cancelled");
});

test("an admin adds a product, restocks it and takes it off sale", async ({ page }) => {
  const name = `E2E Kettle ${Date.now()}`;
  await signIn(page, SHOP_ADMIN, SHOP_ADMIN_PASSWORD);
  await page.getByRole("link", { name: "Admin" }).click();

  await page.getByLabel("New product name").fill(name);
  await page.getByLabel("New product price").fill("25.00");
  await page.getByRole("button", { name: "Add product" }).click();
  await expect(page.locator("#notice")).toContainText(`Added ${name}`);

  // Names sit in input boxes, so find the row by the box's value.
  const row = async () => {
    const rows = page.locator("tbody tr");
    const index = await rows.evaluateAll((all, wanted) => all.findIndex(r => r.querySelector("input").value === wanted), name);
    expect(index).toBeGreaterThanOrEqual(0);
    return rows.nth(index);
  };

  await (await row()).getByLabel("Restock quantity").fill("5");
  await (await row()).getByRole("button", { name: "Restock" }).click();
  await expect(page.locator("#notice")).toContainText(`${name} now has 5 in stock`);

  await (await row()).getByRole("button", { name: "Take off sale" }).click();
  await expect(page.locator("#notice")).toContainText(`${name} is off sale`);
  await expect((await row()).getByRole("button", { name: "Put on sale" })).toBeVisible();
});

test("an admin cancels a customer's paid order, and the customer's page shows it at once", async ({ browser }) => {
  const customer = await (await browser.newContext()).newPage();
  const admin = await (await browser.newContext()).newPage();
  await signIn(customer, SHOP_USER, SHOP_USER_PASSWORD);
  const order = await buyBeansAndSeeThemPaid(customer);

  await signIn(admin, SHOP_ADMIN, SHOP_ADMIN_PASSWORD);
  await admin.getByRole("link", { name: "All orders" }).click();
  await admin.getByLabel("Status").selectOption("PAID");
  // Newest paid order first: the one the customer just placed.
  const row = admin.locator("tbody tr").first();
  await expect(row).toContainText("Coffee Beans 1kg × 1");
  admin.once("dialog", dialog => dialog.accept());
  await row.getByRole("button", { name: "Cancel" }).click();
  await expect(admin.locator("#notice")).toContainText("Order cancelled");

  // No reload: the customer's open page is told by the server.
  await expect(order).toContainText("Cancelled", { timeout: 15_000 });
});
