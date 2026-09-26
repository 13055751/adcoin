import express from 'express';
import { config } from './config.js';
import { router } from './routes.js';

const app = express();
app.use(express.json({ limit: '64kb' }));

app.use((req, res, next) => {
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.set('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.use(router);

app.get('/health', (req, res) => res.json({ ok: true, service: 'adcoin-backend' }));

app.use((req, res) => {
  res.status(404).json({ ok: false, error: 'not_found', path: req.path });
});

app.use((err, req, res, next) => {
  console.error('[error]', err);
  res.status(500).json({ ok: false, error: 'internal' });
});

app.listen(config.port, () => {
  console.log('┌──────────────────────────────────────────────┐');
  console.log('│ AdCoin Backend 已启动                         │');
  console.log('│  监听      : http://127.0.0.1:' + config.port + '          │');
  console.log('│  广告模式  : ' + config.adMode.padEnd(42) + '│');
  console.log('│  插件地址  : ' + config.pluginBaseUrl.padEnd(42) + '│');
  console.log('└──────────────────────────────────────────────┘');
  console.log('端点: /api/auth/register|login  /api/ad/claim  /api/link/bind|unbind');
  console.log('      /api/friend/<request|accept|reject|remove|list>  /api/transfer  /api/me');
});
