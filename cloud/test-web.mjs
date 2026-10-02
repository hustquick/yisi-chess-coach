import assert from 'node:assert/strict';
import { chromium } from 'playwright';
const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  const responses = []; page.on('response', response => { if (response.url().includes('/chess-engine/analyze')) responses.push(response.status()); });
  await page.goto('https://yisi-chess-pwa.pages.dev/');
  await page.locator('.candidate').first().waitFor({ timeout: 90000 });
  assert.ok((await page.locator('header').textContent()).includes('Stockfish 18 云端'));
  assert.equal(await page.locator('#stockfish-engine').count(), 0, '不用浏览器 lite 引擎');
  assert.ok(responses.includes(200));
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
  await page.getByText('对弈与分析设置', { exact: true }).click();
  await page.getByRole('button', { name: '人机对战', exact: true }).click();
  await page.getByLabel('执棋').selectOption('b');
  await page.waitForFunction(() => document.querySelector('.moves')?.textContent?.includes('1.') && document.querySelectorAll('.candidate').length > 0, undefined, { timeout: 90000 });
  assert.deepEqual(errors, []);
  console.log('PASS Pages uses native cloud Stockfish + mobile layout + computer move');
} finally { await browser.close(); }
