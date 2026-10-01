/**
 * 全链路冒烟测试：spawn mock-plugin + backend，然后跑通
 * 注册登录 → 看广告发币 → 幂等 → 绑定 → 好友 → 转账 → 解绑。
 *
 * 运行：npm run smoke
 */
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const API_KEY = 'smoke-test-secret-1234567890';
const DB_DIR = mkdtempSync(path.join(tmpdir(), 'adcoin-smoke-'));

let failures = 0;
function check(name, cond, extra = '') {
  console.log(`${cond ? '✓' : '✗'} ${name}${extra ? '  (' + extra + ')' : ''}`);
  if (!cond) failures++;
}

function spawnServer(name, script, env) {
  return spawn(process.execPath, [script], {
    cwd: ROOT,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function waitFor(url, timeoutMs = 8000) {
  const t0 = Date.now();
  return new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const r = await fetch(url);
        if (r.ok) return resolve();
      } catch { /* not up yet */ }
      if (Date.now() - t0 > timeoutMs) return reject(new Error('等待 ' + url + ' 超时'));
      setTimeout(tick, 200);
    };
    tick();
  });
}

const api = (method, pathname, body, token) =>
  fetch('http://127.0.0.1:8787' + pathname, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  }).then((r) => r.json());

const main = async () => {
  const plugin = spawnServer('mock-plugin', 'mock/mock-plugin.js', {
    MOCK_PLUGIN_PORT: 17890, PLUGIN_API_KEY: API_KEY,
  });
  const backend = spawnServer('backend', 'src/server.js', {
    PORT: 8787, DB_FILE: path.join(DB_DIR, 'users.json'),
    PLUGIN_BASE_URL: 'http://127.0.0.1:17890', PLUGIN_API_KEY: API_KEY,
    AD_MODE: 'mock', AD_REWARD_AMOUNT: 50,
  });
  try {
    await waitFor('http://127.0.0.1:17890/health');
    await waitFor('http://127.0.0.1:8787/health');

    // 注册
    const regA = await api('POST', '/api/auth/register', { username: 'alice', password: 'secret1' });
    check('注册 alice', regA.ok && regA.token);
    const regB = await api('POST', '/api/auth/register', { username: 'bob', password: 'secret1' });
    check('注册 bob', regB.ok && regB.token);
    check('重复用户名被拒', (await api('POST', '/api/auth/register', { username: 'alice', password: 'secret1' })).error === 'username_taken');
    const loginA = await api('POST', '/api/auth/login', { username: 'alice', password: 'secret1' });
    const tokenA = loginA.token;
    const tokenB = regB.token;

    // 初始状态
    const me0 = await api('GET', '/api/me', null, tokenA);
    check('初始余额 0 未绑定', me0.balance === 0 && me0.linked === false);

    // 绑定（真实插件要求先绑定再领奖）
    const bindA = await api('POST', '/api/link/bind', { code: 'ABC12345' }, tokenA);
    check('alice 绑定', bindA.ok && bindA.user.linkedPlayerName, JSON.stringify(bindA));
    check('绑定签发长期令牌(96位hex)', typeof bindA.longToken === 'string' && bindA.longToken.length === 96, `len=${bindA.longToken?.length}`);
    const bindB = await api('POST', '/api/link/bind', { code: 'XYZ98765' }, tokenB);
    check('bob 绑定', bindB.ok);
    const bindDup = await api('POST', '/api/link/bind', { code: 'ABC12345' }, tokenA);
    check('重复绑定被拒', bindDup.error === 'already_linked');
    const meToken = await api('GET', '/api/me', null, tokenA);
    check('/me 暴露 hasToken', meToken.hasToken === true);

    // 长期令牌直接重绑（换设备场景，绑定态不中断）
    const rebind = await api('POST', '/api/link/bind-long', {}, tokenA);
    check('长期令牌重绑成功', rebind.ok && rebind.user.linkedPlayerName, JSON.stringify(rebind));
    check('重绑轮换令牌', typeof rebind.longToken === 'string' && rebind.longToken !== bindA.longToken);

    // 看广告（mock）→ 发币
    const claim1 = await api('POST', '/api/ad/claim', { platform: 'mock', transactionId: 't-001', adUnitId: 'mock-unit' }, tokenA);
    check('看广告发币 50', claim1.ok && claim1.credited && claim1.balance === 50, JSON.stringify(claim1));
    const me1 = await api('GET', '/api/me', null, tokenA);
    check('余额刷新 50', me1.balance === 50);

    // 幂等：同 transactionId 重复 claim
    const claim2 = await api('POST', '/api/ad/claim', { platform: 'mock', transactionId: 't-001', adUnitId: 'mock-unit' }, tokenA);
    check('重复 claim 幂等（duplicate，余额不变）', claim2.duplicate === true && claim2.balance === 50);

    // 好友
    const search = await api('GET', '/api/friend/search?q=bo', null, tokenA);
    check('搜索 bob', search.ok && search.results.some((r) => r.username === 'bob'));
    const bobAppId = search.results.find((r) => r.username === 'bob').appUserId;
    const reqF = await api('POST', '/api/friend/request', { otherAppUserId: bobAppId }, tokenA);
    check('alice 发好友请求', reqF.ok && reqF.pending === true);
    const accF = await api('POST', '/api/friend/accept', { otherAppUserId: regA.user.appUserId }, tokenB);
    check('bob 接受请求', accF.ok);
    const listF = await api('POST', '/api/friend/list', {}, tokenA);
    check('alice 好友列表 1 人', listF.ok && Array.isArray(listF.friends) && listF.friends.length === 1);

    // 转账
    const tr = await api('POST', '/api/transfer', { toAppUserId: bobAppId, amount: 20, clientTxId: 'c1' }, tokenA);
    check('alice 转 20 给 bob', tr.ok && tr.fromBalance === 30 && tr.toBalance === 20, JSON.stringify(tr));
    const trDup = await api('POST', '/api/transfer', { toAppUserId: bobAppId, amount: 20, clientTxId: 'c1' }, tokenA);
    check('转账幂等（duplicate）', trDup.error === 'duplicate', JSON.stringify(trDup));
    const me2 = await api('GET', '/api/me', null, tokenA);
    check('alice 最终余额 30', me2.balance === 30);

    // 排行榜
    const lb = await api('GET', '/api/leaderboard', null, tokenA);
    check('排行榜返回', lb.ok && Array.isArray(lb.top) && lb.top.length > 0, JSON.stringify(lb));

    // 解绑（必须携带长期令牌）
    const un = await api('POST', '/api/link/unbind', {}, tokenA);
    check('alice 解绑', un.ok && un.unlinked === true);
    const meAfter = await api('GET', '/api/me', null, tokenA);
    check('解绑后令牌被吊销', meAfter.hasToken === false);
    const rebind2 = await api('POST', '/api/link/bind-long', {}, tokenA);
    check('吊销后重绑被拒(no_token)', rebind2.error === 'no_token', JSON.stringify(rebind2));

    console.log(failures === 0 ? '\n✅ 冒烟测试全部通过' : `\n❌ ${failures} 项失败`);
    process.exitCode = failures === 0 ? 0 : 1;
  } finally {
    plugin.kill();
    backend.kill();
    rmSync(DB_DIR, { recursive: true, force: true });
  }
};

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
